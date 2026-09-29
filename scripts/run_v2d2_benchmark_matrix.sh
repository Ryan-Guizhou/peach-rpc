#!/usr/bin/env bash
set -euo pipefail

PROFILE="${1:-full}"
OUTPUT_DIR="${2:-target/v2d2-matrix}"
JAR="${PEACH_RPC_BENCHMARK_JAR:-peach-rpc-benchmarks/target/benchmarks.jar}"

if [[ ! -f "$JAR" ]]; then
  echo "Benchmark JAR not found: $JAR" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

case "$PROFILE" in
  smoke)
    payloads=(256)
    shards=(1)
    threads=(1)
    modes=(sample)
    scenarios=(NOOP)
    scenario_connections=(1)
    scenario_threads=(1)
    warmup_iterations=1
    measurement_iterations=1
    warmup_time=500ms
    measurement_time=500ms
    forks=1
    ;;
  standard)
    payloads=(64 1024 16384)
    shards=(1 4)
    threads=(1 64 256)
    modes=(sample thrpt)
    scenarios=(NOOP CPU BLOCKING SLOW_PROVIDER OVERLOAD)
    scenario_connections=(1 4)
    scenario_threads=(1 64 256)
    warmup_iterations=2
    measurement_iterations=3
    warmup_time=1s
    measurement_time=1s
    forks=1
    ;;
  full)
    payloads=(64 256 1024 16384 1048576)
    shards=(1 2 4 8)
    threads=(1 16 64 256 1024)
    modes=(sample thrpt)
    scenarios=(NOOP CPU BLOCKING SLOW_PROVIDER OVERLOAD)
    scenario_connections=(1 4)
    scenario_threads=(1 16 64 256 1024)
    warmup_iterations=2
    measurement_iterations=4
    warmup_time=1s
    measurement_time=1s
    forks=1
    ;;
  *)
    echo "Unknown profile: $PROFILE" >&2
    exit 1
    ;;
esac

{
  echo "profile=$PROFILE"
  echo "commit=${PEACH_RPC_BENCHMARK_COMMIT:-${GITHUB_SHA:-unknown}}"
  echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "uname=$(uname -a)"
  echo "java=$(java -version 2>&1 | tr '\n' ' ')"
  echo "processors=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo unknown)"
} > "$OUTPUT_DIR/environment.properties"

for payload in "${payloads[@]}"; do
  for shard in "${shards[@]}"; do
    for thread_count in "${threads[@]}"; do
      for mode in "${modes[@]}"; do
        output="$OUTPUT_DIR/${mode}-p${payload}-c${shard}-t${thread_count}.json"
        if ! java -jar "$JAR" EndToEndPayloadBenchmark.echo \
          -p "payloadSize=$payload" \
          -p "connectionsPerEndpoint=$shard" \
          -t "$thread_count" \
          -bm "$mode" \
          -wi "$warmup_iterations" \
          -i "$measurement_iterations" \
          -w "$warmup_time" \
          -r "$measurement_time" \
          -f "$forks" \
          -prof gc \
          -rf json \
          -rff "$output"; then
          echo "$mode,$payload,$shard,$thread_count" >> "$OUTPUT_DIR/failures.csv"
          if [[ "$PROFILE" == "smoke" ]]; then
            exit 1
          fi
        fi
      done
    done
  done
done

for scenario in "${scenarios[@]}"; do
  for shard in "${scenario_connections[@]}"; do
    for thread_count in "${scenario_threads[@]}"; do
      for mode in "${modes[@]}"; do
        output="$OUTPUT_DIR/scenario-${scenario}-${mode}-c${shard}-t${thread_count}.json"
        if ! java -jar "$JAR" ExecutionScenarioBenchmark.invoke \
          -p "scenario=$scenario" \
          -p "connectionsPerEndpoint=$shard" \
          -t "$thread_count" \
          -bm "$mode" \
          -wi "$warmup_iterations" \
          -i "$measurement_iterations" \
          -w "$warmup_time" \
          -r "$measurement_time" \
          -f "$forks" \
          -prof gc \
          -rf json \
          -rff "$output"; then
          echo "scenario:$scenario,$mode,$shard,$thread_count" >> "$OUTPUT_DIR/failures.csv"
          if [[ "$PROFILE" == "smoke" ]]; then
            exit 1
          fi
        fi
      done
    done
  done
done

python3 scripts/summarize_v2d2_results.py "$OUTPUT_DIR"
