#!/usr/bin/env bash
set -euo pipefail
OUTPUT_DIR="${1:-target/v2d2-fixed-evidence}"
PROFILE="${PEACH_RPC_EVIDENCE_PROFILE:-full}"
CONCURRENCY="${PEACH_RPC_SOAK_CONCURRENCY:-10000}"
DURATION_SECONDS="${PEACH_RPC_SOAK_DURATION_SECONDS:-1800}"
PAYLOAD_SIZE="${PEACH_RPC_SOAK_PAYLOAD_SIZE:-256}"
CONNECTIONS="${PEACH_RPC_SOAK_CONNECTIONS:-4}"
WARMUP_SECONDS="${PEACH_RPC_SOAK_WARMUP_SECONDS:-30}"
CLIENT_TIMEOUT_MS="${PEACH_RPC_SOAK_CLIENT_TIMEOUT_MS:-15000}"
if [[ "$PROFILE" != "full" ]]; then echo "Fixed production evidence requires PEACH_RPC_EVIDENCE_PROFILE=full" >&2; exit 1; fi
if [[ "${PEACH_RPC_EVIDENCE_CLASS:-controlled}" != "controlled" ]]; then echo "Fixed production evidence requires PEACH_RPC_EVIDENCE_CLASS=controlled" >&2; exit 1; fi
if [[ -z "${PEACH_RPC_RUNNER_ID:-}" ]]; then echo "PEACH_RPC_RUNNER_ID must identify the fixed performance runner" >&2; exit 1; fi
if (( DURATION_SECONDS < 1800 )); then echo "Fixed production evidence requires at least 1800 soak seconds" >&2; exit 1; fi
if (( CONCURRENCY < 10000 )); then echo "Fixed production evidence requires at least 10000 logical callers" >&2; exit 1; fi
export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_BENCHMARK_COMMIT="${PEACH_RPC_BENCHMARK_COMMIT:-${GITHUB_SHA:-unknown}}"
rm -rf "$OUTPUT_DIR"; mkdir -p "$OUTPUT_DIR"
mvn -B -ntp -pl peach-rpc-benchmarks -am clean package -DskipTests
bash scripts/capture_v2d2_environment.sh "$OUTPUT_DIR"
bash scripts/run_v2d2_benchmark_matrix.sh full "$OUTPUT_DIR/matrix"
PEACH_RPC_SOAK_CONCURRENCY="$CONCURRENCY" PEACH_RPC_SOAK_DURATION_SECONDS="$DURATION_SECONDS" PEACH_RPC_SOAK_PAYLOAD_SIZE="$PAYLOAD_SIZE" PEACH_RPC_SOAK_CONNECTIONS="$CONNECTIONS" PEACH_RPC_SOAK_WARMUP_SECONDS="$WARMUP_SECONDS" PEACH_RPC_SOAK_CLIENT_TIMEOUT_MS="$CLIENT_TIMEOUT_MS" PEACH_RPC_SOAK_OUTPUT="$OUTPUT_DIR/soak.json" bash scripts/run_v2d2_soak.sh
python3 scripts/validate_v2d2_evidence.py --environment "$OUTPUT_DIR/environment.properties" --matrix-dir "$OUTPUT_DIR/matrix" --soak "$OUTPUT_DIR/soak.json" --profile full --require-matrix --require-soak --require-controlled --min-soak-seconds 1800 --min-concurrency 10000 --output-dir "$OUTPUT_DIR"
python3 scripts/generate_v2d2_decision_report.py --environment "$OUTPUT_DIR/environment.properties" --matrix-summary "$OUTPUT_DIR/matrix/summary.csv" --soak "$OUTPUT_DIR/soak.json" --output-dir "$OUTPUT_DIR"
echo "V2-D.2 controlled evidence bundle completed: $OUTPUT_DIR"
