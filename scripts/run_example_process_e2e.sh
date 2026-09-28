#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROVIDER_JAR="$(find "$ROOT_DIR/peach-rpc-examples/peach-rpc-example-provider/target" -maxdepth 1 -name '*-exec.jar' -print -quit)"
CONSUMER_JAR="$(find "$ROOT_DIR/peach-rpc-examples/peach-rpc-example-consumer/target" -maxdepth 1 -name '*-exec.jar' -print -quit)"
NACOS_ENDPOINT="${NACOS_TEST_ENDPOINT:-127.0.0.1:8848}"

if [[ -z "$PROVIDER_JAR" || -z "$CONSUMER_JAR" ]]; then
  echo "Executable example JARs were not found. Build the reactor first." >&2
  exit 1
fi

PROVIDER_LOG="$(mktemp)"
CONSUMER_LOG="$(mktemp)"
PROVIDER_PID=""
CONSUMER_PID=""

cleanup() {
  if [[ -n "$CONSUMER_PID" ]]; then
    kill "$CONSUMER_PID" 2>/dev/null || true
    wait "$CONSUMER_PID" 2>/dev/null || true
  fi
  if [[ -n "$PROVIDER_PID" ]]; then
    kill "$PROVIDER_PID" 2>/dev/null || true
    wait "$PROVIDER_PID" 2>/dev/null || true
  fi
  rm -f "$PROVIDER_LOG" "$CONSUMER_LOG"
}
trap cleanup EXIT

start_provider() {
  : >"$PROVIDER_LOG"
  java -jar "$PROVIDER_JAR"     --peach.rpc.registry.endpoints="$NACOS_ENDPOINT"     >"$PROVIDER_LOG" 2>&1 &
  PROVIDER_PID=$!

  for _ in {1..100}; do
    if ! kill -0 "$PROVIDER_PID" 2>/dev/null; then
      echo "Provider process exited before becoming ready." >&2
      cat "$PROVIDER_LOG" >&2
      exit 1
    fi
    if timeout 1 bash -c '</dev/tcp/127.0.0.1/19090' 2>/dev/null; then
      return
    fi
    sleep 0.1
  done

  echo "Provider did not open port 19090 in time." >&2
  cat "$PROVIDER_LOG" >&2
  exit 1
}

wait_for_success_count() {
  local expected="$1"
  for _ in {1..250}; do
    local current
    current="$(grep -c "RPC demo completed successfully: Hello, Peach RPC!" "$CONSUMER_LOG" || true)"
    if (( current >= expected )); then
      return
    fi
    if ! kill -0 "$CONSUMER_PID" 2>/dev/null; then
      echo "Consumer process exited before recovery completed." >&2
      break
    fi
    sleep 0.1
  done

  echo "Independent JVM RPC recovery did not complete successfully." >&2
  echo "----- provider log -----" >&2
  cat "$PROVIDER_LOG" >&2
  echo "----- consumer log -----" >&2
  cat "$CONSUMER_LOG" >&2
  exit 1
}

start_provider

java -jar "$CONSUMER_JAR"   --peach.rpc.registry.endpoints="$NACOS_ENDPOINT"   --peach.rpc.example.repeat-interval-millis=200   --peach.rpc.client.resilience.circuit-open-duration=500ms   --peach.rpc.client.resilience.outlier-ejection-duration=500ms   >"$CONSUMER_LOG" 2>&1 &
CONSUMER_PID=$!

wait_for_success_count 1

kill "$PROVIDER_PID"
wait "$PROVIDER_PID" 2>/dev/null || true
PROVIDER_PID=""

# Capture all successes produced before the replacement Provider is started.
successes_before_restart="$(grep -c "RPC demo completed successfully: Hello, Peach RPC!" "$CONSUMER_LOG" || true)"

start_provider
wait_for_success_count "$((successes_before_restart + 1))"

echo "Independent JVM RPC recovery succeeded without restarting the Consumer."
