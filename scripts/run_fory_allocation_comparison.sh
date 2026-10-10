#!/usr/bin/env bash
set -euo pipefail

# Compare the SAME benchmark source against two exact revisions.
# Shared CI measurements are smoke evidence, never a production performance claim.
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: bash scripts/run_fory_allocation_comparison.sh <baseline-sha> <candidate-sha> [output-dir]" >&2
  exit 2
fi

BASE_SHA="$1"
HEAD_SHA="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="${3:-$ROOT/target/fory-allocation-comparison}"
RUNS="${OTRYX_RPC_FORY_COMPARE_RUNS:-3}"
CLASS="${OTRYX_RPC_FORY_EVIDENCE_CLASS:-shared-ci-smoke}"
RUNNER_ID="${OTRYX_RPC_RUNNER_ID:-${RUNNER_NAME:-unknown}}"
HOST_FINGERPRINT="${OTRYX_RPC_HOST_FINGERPRINT:-}"
JVM_FLAGS="${OTRYX_RPC_ALLOCATION_JVM_FLAGS:--Xms256m -Xmx256m}"
WARMUP="${OTRYX_RPC_FORY_JMH_WARMUP:-2}"
MEASUREMENT="${OTRYX_RPC_FORY_JMH_MEASUREMENT:-3}"
ITERATION_TIME="${OTRYX_RPC_FORY_JMH_TIME:-1s}"

if [[ ! "$BASE_SHA" =~ ^[a-f0-9]{40}$ || ! "$HEAD_SHA" =~ ^[a-f0-9]{40}$ ]]; then
  echo "Both SHAs must be 40 lowercase hexadecimal characters" >&2
  exit 1
fi
if [[ "$BASE_SHA" == "$HEAD_SHA" || "$(git -C "$ROOT" rev-parse HEAD)" != "$HEAD_SHA" ]]; then
  echo "Baseline must differ from actual checked-out HEAD SHA" >&2
  exit 1
fi
if [[ ! "$RUNS" =~ ^[1-9][0-9]*$ ]]; then
  echo "OTRYX_RPC_FORY_COMPARE_RUNS must be a positive integer" >&2
  exit 1
fi
if [[ "$CLASS" != "shared-ci-smoke" && "$CLASS" != "controlled-micro" ]]; then
  echo "OTRYX_RPC_FORY_EVIDENCE_CLASS must be shared-ci-smoke or controlled-micro" >&2
  exit 1
fi
if [[ "$CLASS" == "controlled-micro" ]]; then
  if (( RUNS < 3 )) || [[ -z "$HOST_FINGERPRINT" || "$RUNNER_ID" == "unknown" ]]; then
    echo "Controlled micro evidence needs >=3 pairs and explicit fixed runner/host fingerprint" >&2
    exit 1
  fi
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    echo "GitHub Actions cannot certify a fixed physical benchmark host" >&2
    exit 1
  fi
fi
git -C "$ROOT" cat-file -e "${BASE_SHA}^{commit}"
git -C "$ROOT" cat-file -e "${HEAD_SHA}^{commit}"

BENCH_SRC="otryx-benchmarks/src/main/java/io/peach/rpc/benchmarks/ForyArgumentEncodingBenchmark.java"
if [[ ! -f "$ROOT/$BENCH_SRC" ]]; then
  echo "Missing candidate benchmark source $BENCH_SRC" >&2
  exit 1
fi
BASE_TMP="$(mktemp -d "${RUNNER_TEMP:-/tmp}/peach-fory-base.XXXXXX")"
BASE_WORKTREE="$BASE_TMP/repo"
cleanup() {
  git -C "$ROOT" worktree remove --force "$BASE_WORKTREE" >/dev/null 2>&1 || true
  rm -rf "$BASE_TMP"
}
trap cleanup EXIT

mkdir -p "$OUTPUT"
git -C "$ROOT" worktree add --detach "$BASE_WORKTREE" "$BASE_SHA"
# Install exactly the same JMH harness source into both revisions. Only the
# target Fory library implementation is allowed to differ in this comparison.
mkdir -p "$(dirname "$BASE_WORKTREE/$BENCH_SRC")"
cp "$ROOT/$BENCH_SRC" "$BASE_WORKTREE/$BENCH_SRC"

for checkout in "$BASE_WORKTREE" "$ROOT"; do
  (
    cd "$checkout"
    mvn -B -ntp -pl otryx-codegen -am -DskipTests install
    mvn -B -ntp -pl otryx-benchmarks -am -DskipTests package
  )
done

export JAVA_TOOL_OPTIONS="$JVM_FLAGS"
export BASE_SHA HEAD_SHA CLASS RUNS JVM_FLAGS RUNNER_ID HOST_FINGERPRINT BENCH_SRC ROOT OUTPUT
python3 - <<'PY'
import hashlib
import json
import os
import platform
import subprocess
from pathlib import Path

source = Path(os.environ["ROOT"]) / os.environ["BENCH_SRC"]
meta = {
    "schema": "otryx.rpc.fory.allocation.environment.v1",
    "baseline_sha": os.environ["BASE_SHA"],
    "candidate_sha": os.environ["HEAD_SHA"],
    "evidence_class": os.environ["CLASS"],
    "runner_id": os.environ["RUNNER_ID"],
    "physical_host_fingerprint": os.environ["HOST_FINGERPRINT"],
    "jvm_flags": os.environ["JVM_FLAGS"],
    "benchmark_source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    "os": platform.platform(),
    "cpu": platform.processor(),
    "java_version": subprocess.run(
        ["java", "-version"], text=True, capture_output=True, check=True
    ).stderr.splitlines()[0],
    "measurement_scope": "ForyArgumentEncodingBenchmark-only",
    "run_count": int(os.environ["RUNS"]),
}
Path(os.environ["OUTPUT"], "environment.json").write_text(
    json.dumps(meta, indent=2) + "\n", encoding="utf-8"
)
PY

run_side() {
  local side="$1"
  local round="$2"
  local jar
  if [[ "$side" == "baseline" ]]; then
    jar="$BASE_WORKTREE/otryx-benchmarks/target/benchmarks.jar"
  else
    jar="$ROOT/otryx-benchmarks/target/benchmarks.jar"
  fi
  local dir
  dir="$(printf '%s/%s/run-%02d' "$OUTPUT" "$side" "$round")"
  mkdir -p "$dir"
  for mode in avgt sample; do
    java -jar "$jar" 'ForyArgumentEncodingBenchmark.*' \
      -t 1 -bm "$mode" \
      -wi "$WARMUP" -i "$MEASUREMENT" \
      -w "$ITERATION_TIME" -r "$ITERATION_TIME" \
      -f 1 -prof gc -rf json \
      -rff "$dir/$mode.json"
  done
}

for (( i=1; i<=RUNS; i++ )); do
  if (( i % 2 == 1 )); then
    run_side baseline "$i"
    run_side candidate "$i"
  else
    run_side candidate "$i"
    run_side baseline "$i"
  fi
done

python3 "$ROOT/scripts/compare_fory_allocation.py" \
  --baseline "$OUTPUT/baseline" \
  --candidate "$OUTPUT/candidate" \
  --environment "$OUTPUT/environment.json" \
  --output-dir "$OUTPUT"

cat "$OUTPUT/comparison.md"
