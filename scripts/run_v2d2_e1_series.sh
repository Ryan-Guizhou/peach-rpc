#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-target/v2d2-e1-controlled}"
RUNS="${OTRYX_RPC_EVIDENCE_RUNS:-3}"
COOLDOWN_SECONDS="${OTRYX_RPC_EVIDENCE_COOLDOWN_SECONDS:-60}"

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

  OTRYX_RPC_EVIDENCE_RUN_ID="$run_id" \
  OTRYX_RPC_RUNNER_BASELINE="$BASELINE" \
    bash scripts/run_v2d2_fixed_evidence.sh "$run_dir"

  RUN_ARGS+=(--run "$run_dir")

  if (( index < RUNS && COOLDOWN_SECONDS > 0 )); then
    echo "Cooling down for $COOLDOWN_SECONDS seconds before the next independent run"
    sleep "$COOLDOWN_SECONDS"
  fi
done

python3 scripts/finalize_v2d2_e1.py \
  "${RUN_ARGS[@]}" \
  --output-dir "$ROOT_DIR/e1-handoff"

cat > "$ROOT_DIR/series.properties" <<EOF
schema_version=1
status=PASS
runs=$RUNS
runner_baseline=$BASELINE
handoff=$ROOT_DIR/e1-handoff/e1-handoff.json
repeatability_mode=REPORT_ONLY
EOF

echo "V2-D.2-E1 controlled evidence series completed: $ROOT_DIR"
echo "E1 handoff PASS closes evidence collection only; V2-D.2-E2 still defines repeatability thresholds and baseline promotion."
