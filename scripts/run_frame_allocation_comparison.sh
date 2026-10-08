#!/usr/bin/env bash
set -euo pipefail

# Run the same JMH accumulator workload against an exact baseline commit
# and the current candidate. This is a shared-runner smoke comparison,
# never a controlled performance verdict.
if [[ $# -ne 2 ]]; then
  echo "Usage: bash scripts/run_frame_allocation_comparison.sh <base-sha> <head-sha>" >&2
  exit 2
fi

BASE_SHA="$1"
HEAD_SHA="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="$ROOT/target/frame-allocation-comparison"
BASE_WORKSPACE="$(mktemp -d "${RUNNER_TEMP:-/tmp}/peach-alloc-base.XXXXXX")"
BASE_PATH="$BASE_WORKSPACE/repo"

cleanup() {
  git -C "$ROOT" worktree remove --force "$BASE_PATH" >/dev/null 2>&1 || true
  rm -rf "$BASE_WORKSPACE"
}
trap cleanup EXIT

cd "$ROOT"
if [[ ! "$BASE_SHA" =~ ^[a-f0-9]{40}$$ ||
      ! "$HEAD_SHA" =~ ^[a-f0-9]{40}$$ ]]; then
  echo "Both revisions must be full commit SHA-1 identifiers" >&2
  exit 1
fi
git cat-file -e "${BASE_SHA}^{commit}"
git cat-file -e "${HEAD_SHA}^{commit}"
if [[ "$(git rev-parse HEAD)" != "$HEAD_SHA" ]]; then
  echo "Checkout SHA does not match candidate: $(git rev-parse HEAD)" >&2
  exit 1
fi

mkdir -p "$OUTPUT/baseline" "$OUTPUT/candidate"
git worktree add --detach "$BASE_PATH" "$BASE_SHA"

export JAVA_TOOL_OPTIONS="${PEACH_RPC_ALLOCATION_JVM_FLAGS:--Xms256m -Xmx256m}"
export BASE_SHA
export HEAD_SHA
export OUTPUT

python3 - <<'PY'
import json
import os
import platform
import subprocess
from pathlib import Path
metadata = {
    "schema": "peach.rpc.allocation.comparison.v1",
    "baseline_sha": os.environ["BASE_SHA"],
    "candidate_sha": os.environ["HEAD_SHA"],
    "evidence_class": "shared-ci-smoke",
    "runner_id": os.environ.get("RUNNER_NAME", "local-uncontrolled"),
    "java_version": subprocess.run(
        ["java", "-version"],
        capture_output=True, text=True, check=True,
    ).stderr.splitlines()[0],
    "jvm_flags": os.environ["JAVA_TOOL_OPTIONS"],
    "os": platform.platform(),
    "processor": platform.processor(),
    "cpu_count": os.cpu_count(),
    "run_id": os.environ.get("GITHUB_RUN_ID", "manual"),
    "measurement_scope": "FrameAccumulatorBenchmark.accumulate",
}
Path(os.environ["OUTPUT"], "environment.json").write_text(
    json.dumps(metadata, indent=2), encoding="utf-8"
)
PY

run_jmh() {
  local checkout_dir="$1"
  local kind="$2"
  local output_dir="$OUTPUT/$kind"

  echo "Building $kind benchmark in separate checkout at $(git -C "$checkout_dir" rev-parse HEAD)"
  (cd "$checkout_dir" && mvn -B -ntp -pl peach-rpc-benchmarks -am \
      -DskipTests package)

  local jar="$checkout_dir/peach-rpc-benchmarks/target/benchmarks.jar"
  if [[ ! -s "$jar" ]]; then
    echo "Missing benchmark JAR: $jar" >&2
    exit 1
  fi

  for mode in avgt sample; do
    echo "JMH $kind/$mode with -prof gc"
    java -jar "$jar" FrameAccumulatorBenchmark.accumulate \
      -p "payloadSize=64,16384,1048576" \
      -p "fragmented=false,true" \
      -t 1 \
      -bm "$mode" \
      -wi 1 -i 2 \
      -w 500ms -r 500ms \
      -f 1 \
      -prof gc \
      -rf json \
      -rff "$output_dir/$mode.json"
  done
}

run_jmh "$BASE_PATH" baseline
run_jmh "$ROOT" candidate

python3 scripts/compare_frame_allocation.py \
  --baseline "$OUTPUT/baseline" \
  --candidate "$OUTPUT/candidate" \
  --environment "$OUTPUT/environment.json" \
  --output "$OUTPUT/comparison.md"

cat "$OUTPUT/comparison.md"
