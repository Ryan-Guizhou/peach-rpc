#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-target/v2d2-e1-controlled}"
RUNS="${PEACH_RPC_EVIDENCE_RUNS:-3}"
COOLDOWN_SECONDS="${PEACH_RPC_EVIDENCE_COOLDOWN_SECONDS:-60}"

if (( RUNS < 3 )); then
  echo "V2-D.2-E1 requires at least 3 controlled runs" >&2
  exit 1
fi
if [[ -e "$ROOT_DIR" && -n "$(find "$ROOT_DIR" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
  echo "Evidence root already contains data: $ROOT_DIR" >&2
  exit 1
fi

mkdir -p "$ROOT_DIR"
BASELINE="$ROOT_DIR/runner-baseline.json"
RUN_ARGS=()

for (( index=1; index<=RUNS; index++ )); do
  run_id="run-$index"
  run_dir="$ROOT_DIR/$run_id"
  echo "Starting V2-D.2-E1 $run_id of $RUNS"
  PEACH_RPC_EVIDENCE_RUN_ID="$run_id"   PEACH_RPC_RUNNER_BASELINE="$BASELINE"     bash scripts/run_v2d2_fixed_evidence.sh "$run_dir"
  RUN_ARGS+=(--run "$run_dir")

  if (( index < RUNS && COOLDOWN_SECONDS > 0 )); then
    echo "Cooling down for $COOLDOWN_SECONDS seconds before the next independent run"
    sleep "$COOLDOWN_SECONDS"
  fi
done

python3 scripts/compare_v2d2_evidence.py   "${RUN_ARGS[@]}"   --output-dir "$ROOT_DIR/repeatability-report-only"

cat > "$ROOT_DIR/series.properties" <<EOF
schema_version=1
status=COLLECTED
runs=$RUNS
runner_baseline=$BASELINE
repeatability_mode=REPORT_ONLY
EOF

echo "V2-D.2-E1 controlled evidence series completed: $ROOT_DIR"
echo "Repeatability remains REPORT_ONLY until V2-D.2-E2 defines evidence-backed thresholds."
