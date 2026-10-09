#!/usr/bin/env bash
set -euo pipefail

CONCURRENCY="${PEACH_RPC_SOAK_CONCURRENCY:-10000}"
PAYLOAD_SIZE="${PEACH_RPC_SOAK_PAYLOAD_SIZE:-256}"
CONNECTIONS="${PEACH_RPC_SOAK_CONNECTIONS:-4}"
WARMUP_SECONDS="${PEACH_RPC_SOAK_WARMUP_SECONDS:-15}"
DURATION_SECONDS="${PEACH_RPC_SOAK_DURATION_SECONDS:-600}"
CLIENT_TIMEOUT_MS="${PEACH_RPC_SOAK_CLIENT_TIMEOUT_MS:-5000}"
OUTPUT="${PEACH_RPC_SOAK_OUTPUT:-target/v2d2-soak.json}"
JAR="${PEACH_RPC_BENCHMARK_JAR:-peach-rpc-benchmarks/target/benchmarks.jar}"
JVM_FLAGS="${PEACH_RPC_JVM_FLAGS:-}"
export JAVA_TOOL_OPTIONS="$JVM_FLAGS"

if [[ ! -f "$JAR" ]]; then
  echo "Benchmark JAR not found: $JAR" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"

PEACH_RPC_BENCHMARK_COMMIT="${PEACH_RPC_BENCHMARK_COMMIT:-${GITHUB_SHA:-unknown}}" \
java -cp "$JAR" io.peach.rpc.benchmarks.PerformanceSoakRunner \
  "--concurrency=$CONCURRENCY" \
  "--payload-size=$PAYLOAD_SIZE" \
  "--connections-per-endpoint=$CONNECTIONS" \
  "--warmup-seconds=$WARMUP_SECONDS" \
  "--duration-seconds=$DURATION_SECONDS" \
  "--client-timeout-ms=$CLIENT_TIMEOUT_MS" \
  "--output=$OUTPUT"

# Structural checks only: the shared CI smoke cannot certify production SLOs.
python3 scripts/check_soak_quality.py \
  --soak "$OUTPUT" \
  --mode smoke \
  --output-dir "$(dirname "$OUTPUT")/soak-smoke-quality"
