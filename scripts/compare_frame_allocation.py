#!/usr/bin/env python3
"""Compare paired JMH FrameAccumulator allocation runs with provenance checks.

GitHub shared-runner measurements are smoke evidence, NOT controlled performance
or production p99 guarantees. Reject mismatched parameters and missing B/op.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


BENCHMARK_SUFFIX = "FrameAccumulatorBenchmark.accumulate"


def read_results(path: Path) -> dict[tuple[str, str, str], dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list) or not data:
        raise ValueError(f"JMH results are empty or invalid: {path}")
    output = {}
    for row in data:
        if not row.get("benchmark", "").endswith(BENCHMARK_SUFFIX):
            raise ValueError(f"Unexpected benchmark in {path}")
        mode = row.get("mode")
        if mode not in ("avgt", "sample"):
            raise ValueError(f"Unsupported JMH mode {mode}")
        params = row.get("params", {})
        payload = params.get("payloadSize")
        fragmented = params.get("fragmented")
        if payload not in ("64", "16384", "1048576") or fragmented not in (
            "false", "true"
        ):
            raise ValueError(f"Uncontrolled JMH parameters in {path}: {params}")
        key = (mode, payload, fragmented)
        if key in output:
            raise ValueError(f"Duplicate JMH scenario: {key}")
        primary = row.get("primaryMetric", {})
        score = primary.get("score")
        unit = primary.get("scoreUnit")
        if unit != "ns/op" or not finite_nonnegative(score):
            raise ValueError(f"Missing or invalid JMH mean latency in {path}: {key}")
        profiler = row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm")
        if not isinstance(profiler, dict):
            raise ValueError(f"gc.alloc.rate.norm missing: {path}: {key}")
        allocation = profiler.get("score")
        if profiler.get("scoreUnit") != "B/op" or not finite_nonnegative(
            allocation
        ):
            raise ValueError(f"Invalid JMH allocation B/op: {path}: {key}")
        p99 = None
        if mode == "sample":
            p99 = primary.get("scorePercentiles", {}).get("99.0")
            if not finite_nonnegative(p99):
                raise ValueError(f"Missing sample p99: {path}: {key}")
        output[key] = {
            "allocation_b_op": allocation,
            "score_ns_op": score,
            "p99_ns_op": p99,
            "jmh_version": row.get("jmhVersion"),
            "jdk_version": row.get("jdkVersion"),
        }
    return output


def finite_nonnegative(value: object) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
        and value >= 0
    )


def load_side(folder: Path) -> dict:
    results = {}
    for mode in ("avgt", "sample"):
        current = read_results(folder / f"{mode}.json")
        expected = {key for key in current if key[0] == mode}
        if len(expected) != 6 or expected != set(current):
            raise ValueError(f"Incomplete {mode} scenario matrix for {folder}")
        results.update(current)
    if len(results) != 12:
        raise ValueError(f"Incomplete JMH mode/parameter matrix for {folder}")
    return results


def compare(base: dict, optimized: dict, metadata: dict) -> str:
    if set(base) != set(optimized):
        raise ValueError("Baseline and candidate scenario matrices differ")
    if not metadata.get("baseline_sha") or not metadata.get("candidate_sha"):
        raise ValueError("Missing source SHAs")
    if metadata.get("evidence_class") not in ("shared-ci-smoke", "controlled"):
        raise ValueError("Invalid evidence class")
    if metadata["evidence_class"] == "controlled":
        if not metadata.get("physical_host_fingerprint") or not metadata.get(
            "fixed_runner_id"
        ):
            raise ValueError("Controlled evidence requires locked runner/host")
        # This comparison report is not the multi-run controlled evidence gate.
        raise ValueError(
            "A single microbenchmark pair is not an approved controlled "
            "performance claim; run the full independent evidence pipeline"
        )

    lines = [
        "# PR-E FrameAccumulator allocation / latency comparison",
        "",
        "**Evidence: SHARED-RUNNER SMOKE ONLY — not an official p99, "
        "allocation, throughput, or production regression verdict.**",
        "",
        f"- Baseline SHA: \`{metadata['baseline_sha']}\`",
        f"- Candidate SHA: \`{metadata['candidate_sha']}\`",
        f"- Runner: \`{metadata.get('runner_id', 'unknown')}\`",
        f"- Java: \`{metadata.get('java_version', 'unknown')}\`",
        f"- JVM flags: \`{metadata.get('jvm_flags', '')}\`",
        "- Benchmark: JMH FrameAccumulatorBenchmark.accumulate",
        "- Modes: avgt and sample, \`-prof gc\`, 1 fork, 1 thread",
        "",
        "| Mode | Payload | Fragmented | Base B/op | New B/op | B/op Δ% "
        "| Base ns/op | New ns/op | ns/op Δ% | Base p99 ns | New p99 ns |",
        "|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for key in sorted(base, key=lambda k: (k[0], int(k[1]), k[2])):
        old, new = base[key], optimized[key]
        if old["jdk_version"] != new["jdk_version"]:
            raise ValueError(f"JDK mismatch: {key}")
        if old["jmh_version"] != new["jmh_version"]:
            raise ValueError(f"JMH version mismatch: {key}")
        def change(before, after):
            if before == 0:
                return "n/a"
            return f"{(after / before - 1) * 100:+.2f}%"
        def fmt(value):
            return "n/a" if value is None else f"{value:.3f}"
        lines.append(
            f"| {key[0]} | {key[1]} | {key[2]} "
            f"| {fmt(old['allocation_b_op'])} "
            f"| {fmt(new['allocation_b_op'])} "
            f"| {change(old['allocation_b_op'], new['allocation_b_op'])} "
            f"| {fmt(old['score_ns_op'])} "
            f"| {fmt(new['score_ns_op'])} "
            f"| {change(old['score_ns_op'], new['score_ns_op'])} "
            f"| {fmt(old['p99_ns_op'])} "
            f"| {fmt(new['p99_ns_op'])} |"
        )
    lines.extend([
        "",
        "The JMH allocation metric is **estimated bytes per completed "
        "benchmark invocation**, not an end-to-end RPC allocation metric. "
        "The sample p99 measures one local accumulator call rather than "
        "RPC p99 across networks.",
        "",
        "Before promoting: run identical JDK/JVM flags on a fixed runner, "
        ">= 3 independent pairs with reversals, verify p99/p99.9 and "
        "allocation on end-to-end RPC, check GC/CPU and 10k soak, "
        "and review statistical noise.",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--environment", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        metadata = json.loads(args.environment.read_text(encoding="utf-8"))
        baseline = load_side(args.baseline)
        candidate = load_side(args.candidate)
        report = compare(baseline, candidate, metadata)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        parser.exit(1, f"Invalid allocation comparison: {error}\n")
    print(f"JMH allocation and sample-latency smoke comparison: {args.output}")


if __name__ == "__main__":
    main()
