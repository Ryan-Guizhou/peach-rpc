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

prepare_tls_material() {
  local tls_dir="$OUTPUT_DIR/tls-material"
  mkdir -p "$tls_dir"
  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required for TLS benchmark matrix" >&2
    exit 1
  fi
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$tls_dir/server-key.pem" \
    -out "$tls_dir/server-cert.pem" \
    -days 2 \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    >/dev/null 2>&1
  export PEACH_RPC_BENCHMARK_TLS_CERT="$tls_dir/server-cert.pem"
  export PEACH_RPC_BENCHMARK_TLS_KEY="$tls_dir/server-key.pem"
  export PEACH_RPC_BENCHMARK_TLS_CA="$tls_dir/server-cert.pem"
}

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
    security_modes=(PLAINTEXT)
    security_payloads=(256)
    security_shards=(1)
    security_threads=(1)
    resilience_threads=(1)
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
    security_modes=(PLAINTEXT TLS)
    security_payloads=(256 16384)
    security_shards=(1 4)
    security_threads=(1 64)
    resilience_threads=(1 64 256)
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
    security_modes=(PLAINTEXT TLS)
    security_payloads=(64 256 1024 16384 1048576)
    security_shards=(1 2 4 8)
    security_threads=(1 16 64 256)
    resilience_threads=(1 16 64 256 1024)
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
  echo "openssl=$(openssl version 2>/dev/null || echo unavailable)"
} > "$OUTPUT_DIR/environment.properties"

for payload in "${payloads[@]}"; do
  for shard in "${shards[@]}"; do
    for thread_count in "${threads[@]}"; do
      for mode in "${modes[@]}"; do
        output="$OUTPUT_DIR/${mode}-p${payload}-c${shard}-t${thread_count}.json"
        if ! java -jar "$JAR" EndToEndPayloadBenchmark.echo \
          -p "payloadSize=$payload" \
          -p "connectionsPerEndpoint=$shard" \
          -p "transportSecurity=PLAINTEXT" \
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

if [[ " ${security_modes[*]} " == *" TLS "* ]]; then
  prepare_tls_material
fi

for security_mode in "${security_modes[@]}"; do
  for payload in "${security_payloads[@]}"; do
    for shard in "${security_shards[@]}"; do
      for thread_count in "${security_threads[@]}"; do
        for mode in "${modes[@]}"; do
          output="$OUTPUT_DIR/security-${security_mode}-${mode}-p${payload}-c${shard}-t${thread_count}.json"
          if ! java -jar "$JAR" EndToEndPayloadBenchmark.echo \
            -p "payloadSize=$payload" \
            -p "connectionsPerEndpoint=$shard" \
            -p "transportSecurity=$security_mode" \
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
            echo "security:$security_mode,$mode,$payload,$shard,$thread_count" >> "$OUTPUT_DIR/failures.csv"
            if [[ "$PROFILE" == "smoke" ]]; then
              exit 1
            fi
          fi
        done
      done
    done
  done
done

for thread_count in "${resilience_threads[@]}"; do
  for mode in "${modes[@]}"; do
    output="$OUTPUT_DIR/resilience-${mode}-t${thread_count}.json"
    if ! java -jar "$JAR" 'io.peach.rpc.core.ResiliencePathBenchmark.*' \
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
      echo "resilience,$mode,$thread_count" >> "$OUTPUT_DIR/failures.csv"
      if [[ "$PROFILE" == "smoke" ]]; then
        exit 1
      fi
    fi
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
