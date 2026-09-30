#!/usr/bin/env python3
"""Compare pre/post optimization baseline candidates for V2-D.4 closure."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def numeric(value: object) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def delta_percent(before: float, after: float) -> float | None:
    if before == 0:
        return 0.0 if after == 0 else None
    return (after - before) / abs(before) * 100.0


def matrix_key(item: dict) -> tuple:
    point = item.get("point", {})
    return tuple(sorted(point.items()))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument(
        "--max-latency-regression-percent",
        type=float,
        required=True,
    )
    parser.add_argument(
        "--max-allocation-regression-percent",
        type=float,
        required=True,
    )
    parser.add_argument(
        "--max-throughput-regression-percent",
        type=float,
        required=True,
    )
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    before = load(args.before)
    after = load(args.after)
    errors: list[str] = []

    for name, report in (("before", before), ("after", after)):
        if report.get("status") != "CANDIDATE":
            errors.append(f"{name} baseline must be CANDIDATE")

    if before.get("runnerId") != after.get("runnerId"):
        errors.append("Before/after runnerId mismatch")
    if before.get("environment", {}).get(
        "host_fingerprint_sha256"
    ) != after.get("environment", {}).get(
        "host_fingerprint_sha256"
    ):
        errors.append("Before/after physical host mismatch")

    before_soak = before.get("soakBaseline", {})
    after_soak = after.get("soakBaseline", {})
    soak_deltas: dict[str, float | None] = {}
    for metric in ("throughputOpsPerSecond", "p99Micros", "p999Micros"):
        left = numeric(before_soak.get(metric))
        right = numeric(after_soak.get(metric))
        soak_deltas[metric] = (
            delta_percent(left, right)
            if left is not None and right is not None
            else None
        )

    throughput_delta = soak_deltas.get("throughputOpsPerSecond")
    if (
        throughput_delta is not None
        and throughput_delta < -args.max_throughput_regression_percent
    ):
        errors.append(
            "Soak throughput regression exceeds configured gate"
        )
    for metric in ("p99Micros", "p999Micros"):
        value = soak_deltas.get(metric)
        if (
            value is not None
            and value > args.max_latency_regression_percent
        ):
            errors.append(
                f"{metric} regression exceeds configured gate"
            )

    before_matrix = {
        matrix_key(item): item
        for item in before.get("matrixBaseline", [])
    }
    after_matrix = {
        matrix_key(item): item
        for item in after.get("matrixBaseline", [])
    }
    if set(before_matrix) != set(after_matrix):
        errors.append("Before/after Matrix point shape mismatch")

    matrix_regressions = []
    for key in sorted(set(before_matrix) & set(after_matrix)):
        left = before_matrix[key]
        right = after_matrix[key]
        score_before = numeric(left.get("scoreMedian"))
        score_after = numeric(right.get("scoreMedian"))
        alloc_before = numeric(left.get("allocationMedianBytesPerOp"))
        alloc_after = numeric(right.get("allocationMedianBytesPerOp"))
        score_delta = (
            delta_percent(score_before, score_after)
            if score_before is not None and score_after is not None
            else None
        )
        alloc_delta = (
            delta_percent(alloc_before, alloc_after)
            if alloc_before is not None and alloc_after is not None
            else None
        )
        mode = str(dict(key).get("mode", ""))
        score_failed = False
        if score_delta is not None:
            if mode == "thrpt":
                score_failed = (
                    score_delta
                    < -args.max_throughput_regression_percent
                )
            else:
                score_failed = (
                    score_delta
                    > args.max_latency_regression_percent
                )
        if score_failed:
            matrix_regressions.append(
                {
                    "point": dict(key),
                    "metric": "score",
                    "delta": score_delta,
                }
            )
        if (
            alloc_delta is not None
            and alloc_delta > args.max_allocation_regression_percent
        ):
            matrix_regressions.append(
                {
                    "point": dict(key),
                    "metric": "allocation",
                    "delta": alloc_delta,
                }
            )

    if matrix_regressions:
        errors.append(
            f"{len(matrix_regressions)} Matrix regression gates failed"
        )

    status = "PASS" if not errors else "FAIL"
    report = {
        "schemaVersion": 1,
        "stage": "V2-D.4",
        "status": status,
        "runnerId": after.get("runnerId", "unknown"),
        "soakDeltasPercent": soak_deltas,
        "matrixRegressionCount": len(matrix_regressions),
        "matrixRegressions": matrix_regressions[:100],
        "errors": errors,
        "nextStage": "V2-E.1" if status == "PASS" else "V2-D.4",
    }

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "v2d4-closure.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    lines = [
        "# V2-D.4 Performance Closure",
        "",
        f"**Status:** {status}",
        "",
        f"- Runner: `{report['runnerId']}`",
        f"- Matrix regressions: {len(matrix_regressions)}",
        f"- Next stage: `{report['nextStage']}`",
        "",
        "## Soak delta",
        "",
        "| Metric | Delta % |",
        "|---|---:|",
    ]
    for metric, value in soak_deltas.items():
        lines.append(f"| {metric} | {value} |")
    if errors:
        lines.extend(["", "## Failures", ""])
        lines.extend(f"- {error}" for error in errors)
    (args.output_dir / "v2d4-closure.md").write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )
    if errors:
        return 1
    print("V2-D.4 performance closure passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
