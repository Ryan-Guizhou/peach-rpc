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
RUN_ID="${PEACH_RPC_EVIDENCE_RUN_ID:-$(basename "$OUTPUT_DIR")}"
BASELINE="${PEACH_RPC_RUNNER_BASELINE:-}"

if [[ "$PROFILE" != "full" ]]; then
  echo "Fixed production evidence requires PEACH_RPC_EVIDENCE_PROFILE=full" >&2
  exit 1
fi
if [[ "${PEACH_RPC_EVIDENCE_CLASS:-controlled}" != "controlled" ]]; then
  echo "Fixed production evidence requires PEACH_RPC_EVIDENCE_CLASS=controlled" >&2
  exit 1
fi
if [[ -z "${PEACH_RPC_RUNNER_ID:-}" ]]; then
  echo "PEACH_RPC_RUNNER_ID must identify the fixed performance runner" >&2
  exit 1
fi
if (( DURATION_SECONDS < 1800 )); then
  echo "Fixed production evidence requires at least 1800 soak seconds" >&2
  exit 1
fi
if (( CONCURRENCY < 10000 )); then
  echo "Fixed production evidence requires at least 10000 logical callers" >&2
  exit 1
fi
if [[ -e "$OUTPUT_DIR" && -n "$(find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
  echo "Refusing to overwrite an existing evidence bundle: $OUTPUT_DIR" >&2
  exit 1
fi

GIT_HEAD="$(git rev-parse HEAD)"
if [[ -n "${PEACH_RPC_BENCHMARK_COMMIT:-}" && "$PEACH_RPC_BENCHMARK_COMMIT" != "$GIT_HEAD" ]]; then
  echo "PEACH_RPC_BENCHMARK_COMMIT does not match git HEAD" >&2
  exit 1
fi

export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_BENCHMARK_COMMIT="$GIT_HEAD"
export PEACH_RPC_EVIDENCE_RUN_ID="$RUN_ID"

mkdir -p "$OUTPUT_DIR"

bash scripts/preflight_v2d2_runner.sh "$OUTPUT_DIR"
bash scripts/capture_v2d2_environment.sh "$OUTPUT_DIR"

if [[ -n "$BASELINE" ]]; then
  python3 scripts/check_v2d2_runner_baseline.py     --environment "$OUTPUT_DIR/environment.properties"     --baseline "$BASELINE"     --initialize-if-missing
fi

mvn -B -ntp -pl peach-rpc-benchmarks -am clean package -DskipTests

bash scripts/run_v2d2_benchmark_matrix.sh   full   "$OUTPUT_DIR/matrix"

PEACH_RPC_SOAK_CONCURRENCY="$CONCURRENCY" PEACH_RPC_SOAK_DURATION_SECONDS="$DURATION_SECONDS" PEACH_RPC_SOAK_PAYLOAD_SIZE="$PAYLOAD_SIZE" PEACH_RPC_SOAK_CONNECTIONS="$CONNECTIONS" PEACH_RPC_SOAK_WARMUP_SECONDS="$WARMUP_SECONDS" PEACH_RPC_SOAK_CLIENT_TIMEOUT_MS="$CLIENT_TIMEOUT_MS" PEACH_RPC_SOAK_OUTPUT="$OUTPUT_DIR/soak.json"   bash scripts/run_v2d2_soak.sh

python3 scripts/validate_v2d2_evidence.py   --environment "$OUTPUT_DIR/environment.properties"   --matrix-dir "$OUTPUT_DIR/matrix"   --soak "$OUTPUT_DIR/soak.json"   --profile full   --require-matrix   --require-soak   --require-controlled   --min-soak-seconds 1800   --min-concurrency 10000   --output-dir "$OUTPUT_DIR"

python3 scripts/generate_v2d2_decision_report.py   --environment "$OUTPUT_DIR/environment.properties"   --matrix-summary "$OUTPUT_DIR/matrix/summary.csv"   --soak "$OUTPUT_DIR/soak.json"   --output-dir "$OUTPUT_DIR"

python3 scripts/manage_v2d2_evidence_manifest.py   create   --bundle "$OUTPUT_DIR"
python3 scripts/manage_v2d2_evidence_manifest.py   verify   --bundle "$OUTPUT_DIR"

echo "V2-D.2 controlled evidence bundle completed: $OUTPUT_DIR"
