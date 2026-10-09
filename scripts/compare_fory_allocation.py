#!/usr/bin/env python3
"""Validate paired Fory JMH evidence; never infer production RPC SLOs."""

from __future__ import annotations

import argparse
import json
import math
import statistics
from pathlib import Path

METHODS = {
    "zeroArgumentFastPath",
    "zeroArgumentLegacyStyle",
    "oneArgumentFastPath",
    "fourArgumentFastPath",
}
MODES = {"avgt", "sample"}


def finite_nonnegative(value: object) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
        and value >= 0
    )


def parse_mode(path: Path, mode: str) -> dict[str, dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list) or len(data) != len(METHODS):
        raise ValueError(f"Incomplete Fory JMH method matrix: {path}")
    results: dict[str, dict] = {}
    for row in data:
        name = row.get("benchmark", "").split(".")[-1]
        if name not in METHODS or name in results:
            raise ValueError(f"Unexpected or duplicate benchmark: {name}")
        if row.get("mode") != mode:
            raise ValueError(f"Mismatched JMH mode for {name}: {mode}")
        if row.get("params"):
            raise ValueError(f"Unexpected benchmark parameters for {name}")
        primary = row.get("primaryMetric", {})
        if primary.get("scoreUnit") != "ns/op" or not finite_nonnegative(
            primary.get("score")
        ):
            raise ValueError(f"Missing or invalid ns/op: {name}")
        gc = row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm")
        if not isinstance(gc, dict) or gc.get("scoreUnit") != "B/op" or not (
            finite_nonnegative(gc.get("score"))
        ):
            raise ValueError(f"Missing or invalid gc.alloc.rate.norm: {name}")
        p99 = None
        if mode == "sample":
            p99 = primary.get("scorePercentiles", {}).get("99.0")
            if not finite_nonnegative(p99):
                raise ValueError(f"Missing valid sample p99: {name}")
        if not row.get("jdkVersion") or not row.get("jmhVersion"):
            raise ValueError(f"Missing JDK/JMH version for {name}")
        results[name] = {
            "ns_op": float(primary["score"]),
            "b_op": float(gc["score"]),
            "p99_ns": None if p99 is None else float(p99),
            "jdk": row["jdkVersion"],
            "jmh": row["jmhVersion"],
        }
    if set(results) != METHODS:
        raise ValueError(f"Incomplete Fory benchmark method set: {path}")
    return results


def load_run(root: Path) -> dict[tuple[str, str], dict]:
    result = {}
    for mode in sorted(MODES):
        for name, data in parse_mode(root / f"{mode}.json", mode).items():
            result[mode, name] = data
    return result


def load_comparison(root: Path) -> dict[str, dict[str, dict]]:
    locations = sorted(p for p in root.iterdir() if p.is_dir() and p.name.startswith("run-"))
    if not locations:
        raise ValueError(f"No paired Fory JMH runs found at {root}")
    return {folder.name: load_run(folder) for folder in locations}


def coefficient_of_variation(values: list[float]) -> float | None:
    if len(values) < 2:
        return None
    mean = statistics.mean(values)
    if abs(mean) < 1e-12:
        return 0.0 if max(values, default=0) == 0 else None
    return statistics.stdev(values) * 100.0 / abs(mean)


def compare(baseline: dict, candidate: dict, metadata: dict) -> dict:
    if set(baseline) != set(candidate):
        raise ValueError("Baseline/candidate run sets differ")
    if not baseline:
        raise ValueError("Empty paired benchmark evidence")
    import re
    for key in ("baseline_sha", "candidate_sha"):
        if not re.fullmatch(r"[0-9a-f]{40}", str(metadata.get(key, ""))):
            raise ValueError(f"Expected exact 40-character {key}")
    if metadata["baseline_sha"] == metadata["candidate_sha"]:
        raise ValueError("Baseline and candidate commits must differ")
    evidence = metadata.get("evidence_class")
    if evidence not in {"shared-ci-smoke", "controlled-micro"}:
        raise ValueError("Invalid Fory evidence class")
    if evidence == "controlled-micro":
        if len(baseline) < 3:
            raise ValueError("Controlled microbenchmark needs >=3 AB/BA pairs")
        if not metadata.get("runner_id") or not metadata.get("physical_host_fingerprint"):
            raise ValueError("Controlled microbenchmark requires fixed runner/host fingerprint")
        if str(metadata["runner_id"]).startswith("GitHub Actions"):
            raise ValueError("GitHub-hosted shared runners are not controlled")
    rows = []
    for mode in ("avgt", "sample"):
        for name in sorted(METHODS):
            base_rows, new_rows = [], []
            reference_jdk = reference_jmh = None
            for run in sorted(baseline):
                before = baseline[run][mode, name]
                after = candidate[run][mode, name]
                for side in (before, after):
                    if reference_jdk is None:
                        reference_jdk, reference_jmh = side["jdk"], side["jmh"]
                    if side["jdk"] != reference_jdk or side["jmh"] != reference_jmh:
                        raise ValueError(f"JDK/JMH version differs: {mode}/{name}")
                base_rows.append(before)
                new_rows.append(after)
            before_b = statistics.median(x["b_op"] for x in base_rows)
            after_b = statistics.median(x["b_op"] for x in new_rows)
            before_ns = statistics.median(x["ns_op"] for x in base_rows)
            after_ns = statistics.median(x["ns_op"] for x in new_rows)
            before_p99 = (
                statistics.median(x["p99_ns"] for x in base_rows)
                if mode == "sample" else None
            )
            after_p99 = (
                statistics.median(x["p99_ns"] for x in new_rows)
                if mode == "sample" else None
            )
            rows.append({
                "mode": mode,
                "method": name,
                "baseline_bytes_per_op": before_b,
                "candidate_bytes_per_op": after_b,
                "delta_bytes_per_op": after_b - before_b,
                "baseline_ns_per_op": before_ns,
                "candidate_ns_per_op": after_ns,
                "baseline_sample_p99_ns": before_p99,
                "candidate_sample_p99_ns": after_p99,
                "baseline_allocation_cv_percent": coefficient_of_variation(
                    [x["b_op"] for x in base_rows]
                ),
                "candidate_allocation_cv_percent": coefficient_of_variation(
                    [x["b_op"] for x in new_rows]
                ),
                "jdk": reference_jdk,
                "jmh": reference_jmh,
            })
    return {
        "schema": "peach.rpc.fory.allocation.ab.v1",
        "status": "REPORT_ONLY",
        "evidence_class": evidence,
        "scope": "codec-local-JMH-not-end-to-end-RPC",
        "baseline_sha": metadata["baseline_sha"],
        "candidate_sha": metadata["candidate_sha"],
        "run_count": len(baseline),
        "runner_id": metadata.get("runner_id", "unknown"),
        "physical_host_fingerprint": metadata.get("physical_host_fingerprint", "unknown"),
        "jvm_flags": metadata.get("jvm_flags", ""),
        "methods": rows,
    }


def markdown(report: dict) -> str:
    lines = [
        "# Fory zero-argument allocation comparison",
        "",
        "**Status: REPORT_ONLY** — no automatic performance improvement or "
        "production SLO claim. This measures a local codec path, not RPC p99.",
        "",
        f"- Baseline: `{report['baseline_sha']}`",
        f"- Candidate: `{report['candidate_sha']}`",
        f"- Evidence class: `{report['evidence_class']}`",
        f"- Independent AB/BA pairs: `{report['run_count']}`",
        f"- Runner: `{report['runner_id']}`",
        f"- JVM flags: `{report['jvm_flags']}`",
        "",
        "| Mode | Method | Base B/op | New B/op | Δ B/op | "
        "Base ns/op | New ns/op | Base sample p99 ns | New sample p99 ns |",
        "|---|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    def fmt(value):
        return "-" if value is None else f"{value:.3f}"
    for item in report["methods"]:
        lines.append(
            f"| {item['mode']} | {item['method']} | "
            f"{fmt(item['baseline_bytes_per_op'])} | "
            f"{fmt(item['candidate_bytes_per_op'])} | "
            f"{fmt(item['delta_bytes_per_op'])} | "
            f"{fmt(item['baseline_ns_per_op'])} | "
            f"{fmt(item['candidate_ns_per_op'])} | "
            f"{fmt(item['baseline_sample_p99_ns'])} | "
            f"{fmt(item['candidate_sample_p99_ns'])} |"
        )
    lines.extend([
        "",
        "A small Object[0] may be removed by the JIT or dominated by Fory's "
        "other allocations. Examine raw JMH profiles and variability, then "
        "repeat on fixed hardware with end-to-end network latency, CPU and GC. "
        "Do not infer 10k concurrency stability from this table.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--environment", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        meta = json.loads(args.environment.read_text(encoding="utf-8"))
        report = compare(
            load_comparison(args.baseline),
            load_comparison(args.candidate),
            meta,
        )
        args.output_dir.mkdir(parents=True, exist_ok=True)
        (args.output_dir / "comparison.json").write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        (args.output_dir / "comparison.md").write_text(
            markdown(report), encoding="utf-8",
        )
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        parser.exit(1, f"Invalid Fory allocation comparison: {error}\n")
    print(f"Fory local-allocation evidence saved to {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
