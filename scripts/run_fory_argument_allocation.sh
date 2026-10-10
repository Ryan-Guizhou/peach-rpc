#!/usr/bin/env bash
set -euo pipefail

# Manual GC/allocation microbenchmark. Results on shared runners are smoke only.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${OTRYX_RPC_BENCHMARK_JAR:-$ROOT/otryx-benchmarks/target/benchmarks.jar}"
OUTPUT="${1:-$ROOT/target/fory-argument-allocation}"
WARMUP="${OTRYX_RPC_FORY_JMH_WARMUP:-2}"
MEASUREMENT="${OTRYX_RPC_FORY_JMH_MEASUREMENT:-3}"
JVM_FLAGS="${OTRYX_RPC_ALLOCATION_JVM_FLAGS:--Xms256m -Xmx256m}"

if [[ ! -s "$JAR" ]]; then
  echo "Missing benchmark JAR: $JAR" >&2
  echo "Build it first: mvn -B -ntp -pl otryx-benchmarks -am -DskipTests package" >&2
  exit 1
fi

mkdir -p "$OUTPUT"
{
  printf 'commit='
  git -C "$ROOT" rev-parse HEAD
  printf 'runner=%s\n' "${RUNNER_NAME:-local-uncontrolled}"
  printf 'jvm_flags=%s\n' "$JVM_FLAGS"
  printf 'measurement_scope=ForyArgumentEncodingBenchmark\n'
  printf 'evidence_class=uncontrolled-microbenchmark\n'
  java -version 2>&1
} > "$OUTPUT/environment.txt"

export JAVA_TOOL_OPTIONS="$JVM_FLAGS"
for mode in avgt sample; do
  java -jar "$JAR" 'ForyArgumentEncodingBenchmark.*' \
    -t 1 -bm "$mode" \
    -wi "$WARMUP" -i "$MEASUREMENT" \
    -w 1s -r 1s -f 1 \
    -prof gc -rf json \
    -rff "$OUTPUT/$mode.json"
done
echo "Fory argument allocation evidence written to $OUTPUT"
