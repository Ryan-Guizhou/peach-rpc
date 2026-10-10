#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${RPC_COMPARISON_OUTPUT_DIR:-target/rpc-comparison-smoke}"
OTRYX_JAR="tools/rpc-comparison/otryx/target/otryx-comparison.jar"
DUBBO_JAR="tools/rpc-comparison/dubbo/target/dubbo-comparison.jar"
CONCURRENCY="${RPC_COMPARISON_CONCURRENCY:-16}"
WARMUP="${RPC_COMPARISON_WARMUP_SECONDS:-2}"
DURATION="${RPC_COMPARISON_DURATION_SECONDS:-3}"
PAYLOAD="${RPC_COMPARISON_PAYLOAD_BYTES:-256}"

if [[ ! "$CONCURRENCY" =~ ^[0-9]+$ ]] ||
   [[ ! "$WARMUP" =~ ^[0-9]+$ ]] ||
   [[ ! "$DURATION" =~ ^[0-9]+$ ]] ||
   [[ ! "$PAYLOAD" =~ ^[0-9]+$ ]]; then
  echo "Comparison parameters must be nonnegative integers" >&2
  exit 1
fi

export RPC_COMPARISON_EVIDENCE_CLASS="smoke"
export RPC_COMPARISON_GIT_SHA="$(git rev-parse HEAD)"
export RPC_COMPARISON_RUN_ID="${RPC_COMPARISON_RUN_ID:-smoke-${RPC_COMPARISON_GIT_SHA:0:12}}"

mkdir -p "$OUTPUT_DIR"

echo "Building the installed OTRYX RPC dependency graph"
mvn -B -ntp -DskipTests \
  -pl otryx-codegen,otryx-codec-fory,otryx-transport-vertx \
  -am install

echo "Building isolated Maven runtimes"
mvn -B -ntp -f tools/rpc-comparison/pom.xml clean package

if [[ ! -s "$OTRYX_JAR" || ! -s "$DUBBO_JAR" ]]; then
  echo "Comparison jars were not produced" >&2
  exit 1
fi

python3 - "$OUTPUT_DIR/environment.json" <<'PY'
import json
import os
import platform
import subprocess
import sys

def capture(*command):
    return subprocess.check_output(command, text=True, stderr=subprocess.STDOUT).strip()

environment = {
    "schema": "otryx.rpc.comparison.environment.v1",
    "evidence_class": "smoke",
    "run_id": os.environ["RPC_COMPARISON_RUN_ID"],
    "git_sha": os.environ["RPC_COMPARISON_GIT_SHA"],
    "host": platform.node(),
    "os": platform.platform(),
    "python_version": platform.python_version(),
    "java_version": capture("java", "-version"),
    "cpu_count": os.cpu_count(),
    "runner_id": os.getenv("RUNNER_NAME", "local-uncontrolled"),
    "physical_host_fingerprint": None,
    "jvm_flags": os.getenv("JAVA_TOOL_OPTIONS", ""),
}
with open(sys.argv[1], "w", encoding="utf-8") as stream:
    json.dump(environment, stream, indent=2)
PY

find_port() {
  python3 - <<'PY'
import socket
with socket.socket() as s:
    s.bind(("127.0.0.1", 0))
    print(s.getsockname()[1])
PY
}

provider_pid=""
cleanup() {
  if [[ -n "$provider_pid" ]]; then
    kill "$provider_pid" 2>/dev/null || true
    wait "$provider_pid" 2>/dev/null || true
    provider_pid=""
  fi
}
trap cleanup EXIT

run_one() {
  local framework="$1"
  local jar="$2"
  local port
  port="$(find_port)"
  local provider_log="$OUTPUT_DIR/${framework}-provider.log"

  java -jar "$jar" provider "$port" > "$provider_log" 2>&1 &
  provider_pid="$!"
  local ready=0
  for _ in $(seq 1 90); do
    if grep -q "READY framework=${framework} " "$provider_log"; then
      ready=1
      break
    fi
    if ! kill -0 "$provider_pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if (( ready != 1 )); then
    echo "${framework} provider did not become ready" >&2
    tail -n 100 "$provider_log" >&2
    return 1
  fi

  java -jar "$jar" client 127.0.0.1 "$port" \
    "$CONCURRENCY" "$WARMUP" "$DURATION" "$PAYLOAD" \
    "$OUTPUT_DIR/${framework}.json"
  cleanup
}

run_one otryx "$OTRYX_JAR"
run_one dubbo "$DUBBO_JAR"

python3 scripts/validate_rpc_comparison.py \
  --otryx "$OUTPUT_DIR/otryx.json" \
  --dubbo "$OUTPUT_DIR/dubbo.json" \
  --environment "$OUTPUT_DIR/environment.json" \
  --report "$OUTPUT_DIR/report.md"

echo "Completed independent-JVM smoke comparison: $OUTPUT_DIR"
