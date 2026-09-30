#!/usr/bin/env python3
"""Compare repeated controlled V2-D.2 evidence bundles for repeatability."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path

DYNAMIC_ENVIRONMENT_KEYS = {
    "captured_at",
    "hostname",
}

REQUIRED_ENVIRONMENT_KEYS = {
    "commit",
    "evidence_class",
    "runner_id",
    "host_fingerprint_sha256",
    "cpu_model",
    "logical_cores",
    "physical_cores",
    "memory_bytes",
    "cpu_governor",
    "kernel",
    "java",
    "jvm_flags",
    "containerized",
}

SOAK_METRICS = (
    "throughputOpsPerSecond",
    "p50Micros",
    "p99Micros",
    "p999Micros",
    "errorRate",
    "gcCountDelta",
    "gcTimeMillisDelta",
    "processCpuCoresAverage",
)


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def read_summary(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def as_float(value: object) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def coefficient_of_variation(values: list[float]) -> float | None:
    if len(values) < 2:
        return None
    mean = statistics.mean(values)
    if math.isclose(mean, 0.0, abs_tol=1e-12):
        return 0.0 if all(math.isclose(value, 0.0, abs_tol=1e-12) for value in values) else None
    return statistics.stdev(values) / abs(mean) * 100.0


def relative_spread(values: list[float]) -> float | None:
    if not values:
        return None
    median = statistics.median(values)
    if math.isclose(median, 0.0, abs_tol=1e-12):
        return 0.0 if all(math.isclose(value, 0.0, abs_tol=1e-12) for value in values) else None
    return (max(values) - min(values)) / abs(median) * 100.0


def point_key(row: dict[str, str]) -> tuple[str, ...]:
    return (
        row.get("family", ""),
        row.get("benchmark", ""),
        row.get("scenario", ""),
        row.get("security", ""),
        row.get("mode", ""),
        row.get("payload_bytes", ""),
        row.get("connections", ""),
        row.get("threads", ""),
    )


def load_bundle(root: Path) -> dict[str, object]:
    environment_path = root / "environment.properties"
    matrix_summary_path = root / "matrix" / "summary.csv"
    soak_path = root / "soak.json"
    validation_path = root / "validation-report.json"

    missing = [
        str(path)
        for path in (
            environment_path,
            matrix_summary_path,
            soak_path,
            validation_path,
        )
        if not path.is_file()
    ]
    if missing:
        raise ValueError(
            "Evidence bundle is incomplete: " + ", ".join(missing)
        )

    validation = json.loads(
        validation_path.read_text(encoding="utf-8")
    )
    if validation.get("status") != "PASS":
        raise ValueError(
            f"Evidence validation is not PASS: {root}"
        )

    environment = parse_properties(environment_path)
    soak = json.loads(soak_path.read_text(encoding="utf-8"))
    summary = read_summary(matrix_summary_path)

    return {
        "root": str(root),
        "environment": environment,
        "soak": soak,
        "summary": summary,
    }


def compare_environment(
    bundles: list[dict[str, object]],
) -> tuple[list[str], dict[str, str]]:
    errors: list[str] = []
    baseline = bundles[0]["environment"]
    assert isinstance(baseline, dict)

    missing = sorted(
        REQUIRED_ENVIRONMENT_KEYS.difference(baseline)
    )
    if missing:
        errors.append(
            "Baseline environment is missing: "
            + ", ".join(missing)
        )

    for index, bundle in enumerate(bundles[1:], start=2):
        current = bundle["environment"]
        assert isinstance(current, dict)
        for key in REQUIRED_ENVIRONMENT_KEYS:
            if baseline.get(key) != current.get(key):
                errors.append(
                    f"Environment mismatch run1/run{index}: "
                    f"{key}={baseline.get(key)!r} vs "
                    f"{current.get(key)!r}"
                )

    comparable = {
        key: value
        for key, value in baseline.items()
        if key not in DYNAMIC_ENVIRONMENT_KEYS
    }
    return errors, comparable


def compare_matrix_shape(
    bundles: list[dict[str, object]],
) -> list[str]:
    errors: list[str] = []
    baseline_rows = bundles[0]["summary"]
    assert isinstance(baseline_rows, list)
    baseline_keys = {point_key(row) for row in baseline_rows}

    for index, bundle in enumerate(bundles[1:], start=2):
        current_rows = bundle["summary"]
        assert isinstance(current_rows, list)
        current_keys = {point_key(row) for row in current_rows}
        missing = baseline_keys - current_keys
        extra = current_keys - baseline_keys
        if missing:
            errors.append(
                f"Matrix shape run{index} is missing "
                f"{len(missing)} points"
            )
        if extra:
            errors.append(
                f"Matrix shape run{index} has "
                f"{len(extra)} unexpected points"
            )
    return errors


def matrix_variability(
    bundles: list[dict[str, object]],
) -> list[dict[str, object]]:
    scores: dict[tuple[str, ...], list[float]] = defaultdict(list)
    allocations: dict[tuple[str, ...], list[float]] = defaultdict(list)

    for bundle in bundles:
        rows = bundle["summary"]
        assert isinstance(rows, list)
        for row in rows:
            key = point_key(row)
            score = as_float(row.get("score"))
            allocation = as_float(row.get("alloc_b_op"))
            if score is not None:
                scores[key].append(score)
            if allocation is not None:
                allocations[key].append(allocation)

    result: list[dict[str, object]] = []
    for key in sorted(scores):
        score_values = scores[key]
        allocation_values = allocations.get(key, [])
        result.append(
            {
                "point": {
                    "family": key[0],
                    "benchmark": key[1],
                    "scenario": key[2],
                    "security": key[3],
                    "mode": key[4],
                    "payloadBytes": key[5],
                    "connections": key[6],
                    "threads": key[7],
                },
                "scoreMedian": statistics.median(score_values),
                "scoreCvPercent": coefficient_of_variation(score_values),
                "scoreRelativeSpreadPercent": relative_spread(score_values),
                "allocationMedianBytesPerOp": (
                    statistics.median(allocation_values)
                    if allocation_values
                    else None
                ),
                "allocationCvPercent": coefficient_of_variation(
                    allocation_values
                ),
                "allocationRelativeSpreadPercent": relative_spread(
                    allocation_values
                ),
                "runs": len(score_values),
            }
        )
    return result


def soak_variability(
    bundles: list[dict[str, object]],
) -> dict[str, dict[str, object]]:
    result: dict[str, dict[str, object]] = {}
    for metric in SOAK_METRICS:
        values = []
        for bundle in bundles:
            soak = bundle["soak"]
            assert isinstance(soak, dict)
            value = as_float(soak.get(metric))
            if value is not None:
                values.append(value)
        if not values:
            continue
        result[metric] = {
            "median": statistics.median(values),
            "min": min(values),
            "max": max(values),
            "cvPercent": coefficient_of_variation(values),
            "relativeSpreadPercent": relative_spread(values),
            "runs": len(values),
        }

    qps_per_core = []
    for bundle in bundles:
        soak = bundle["soak"]
        assert isinstance(soak, dict)
        throughput = as_float(
            soak.get("throughputOpsPerSecond")
        )
        cpu = as_float(soak.get("processCpuCoresAverage"))
        if throughput is not None and cpu is not None and cpu > 0:
            qps_per_core.append(throughput / cpu)
    if qps_per_core:
        result["qpsPerCpuCore"] = {
            "median": statistics.median(qps_per_core),
            "min": min(qps_per_core),
            "max": max(qps_per_core),
            "cvPercent": coefficient_of_variation(qps_per_core),
            "relativeSpreadPercent": relative_spread(qps_per_core),
            "runs": len(qps_per_core),
        }
    return result


def worst_points(
    matrix: list[dict[str, object]],
    key: str,
    limit: int = 20,
) -> list[dict[str, object]]:
    candidates = [
        item
        for item in matrix
        if isinstance(item.get(key), (int, float))
    ]
    return sorted(
        candidates,
        key=lambda item: float(item[key]),
        reverse=True,
    )[:limit]


def threshold_failure(
    label: str,
    value: float | None,
    threshold: float | None,
    failures: list[str],
) -> None:
    if threshold is None or value is None:
        return
    if value > threshold:
        failures.append(
            f"{label} CV {value:.3f}% exceeds "
            f"threshold {threshold:.3f}%"
        )


def write_reports(
    output_dir: Path,
    bundles: list[dict[str, object]],
    environment: dict[str, str],
    matrix: list[dict[str, object]],
    soak: dict[str, dict[str, object]],
    errors: list[str],
    threshold_failures: list[str],
    thresholds: dict[str, float | None],
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)

    status = (
        "FAIL"
        if errors or threshold_failures
        else (
            "PASS"
            if any(value is not None for value in thresholds.values())
            else "REPORT_ONLY"
        )
    )

    report = {
        "schemaVersion": 1,
        "status": status,
        "runCount": len(bundles),
        "runs": [bundle["root"] for bundle in bundles],
        "environment": environment,
        "thresholds": thresholds,
        "errors": errors,
        "thresholdFailures": threshold_failures,
        "soakVariability": soak,
        "matrixVariability": matrix,
        "worstScoreCvPoints": worst_points(
            matrix,
            "scoreCvPercent",
        ),
        "worstAllocationCvPoints": worst_points(
            matrix,
            "allocationCvPercent",
        ),
        "matrixPointCount": len(matrix),
    }

    (output_dir / "repeatability-report.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    lines = [
        "# V2-D.2 Repeatability Report",
        "",
        f"**Status:** {status}",
        "",
        f"- Controlled runs: {len(bundles)}",
        f"- Runner ID: `{environment.get('runner_id', 'unknown')}`",
        f"- Commit: `{environment.get('commit', 'unknown')}`",
        f"- Matrix points compared: {len(matrix)}",
        "",
        "## Soak variability",
        "",
        "| Metric | Median | Min | Max | CV % | Spread % |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for metric, item in soak.items():
        lines.append(
            f"| {metric} | {item.get('median', '-')} | "
            f"{item.get('min', '-')} | {item.get('max', '-')} | "
            f"{item.get('cvPercent', '-')} | "
            f"{item.get('relativeSpreadPercent', '-')} |"
        )

    lines.extend(
        [
            "",
            "## Highest matrix score CV",
            "",
            "| Family | Benchmark | Scenario | Security | Mode | Payload | Connections | Threads | CV % | Spread % |",
            "|---|---|---|---|---|---:|---:|---:|---:|---:|",
        ]
    )
    for item in worst_points(matrix, "scoreCvPercent", 15):
        point = item["point"]
        lines.append(
            f"| {point['family']} | {point['benchmark']} | "
            f"{point['scenario']} | {point['security']} | "
            f"{point['mode']} | {point['payloadBytes']} | "
            f"{point['connections']} | {point['threads']} | "
            f"{item.get('scoreCvPercent', '-')} | "
            f"{item.get('scoreRelativeSpreadPercent', '-')} |"
        )

    if errors:
        lines.extend(["", "## Comparability failures", ""])
        lines.extend(f"- {error}" for error in errors)

    if threshold_failures:
        lines.extend(["", "## Threshold failures", ""])
        lines.extend(
            f"- {failure}" for failure in threshold_failures
        )

    lines.extend(
        [
            "",
            "> REPORT_ONLY means the bundles are comparable and variability was calculated, but no engineering threshold policy was supplied. PASS requires explicit thresholds.",
            "",
        ]
    )
    (output_dir / "repeatability-report.md").write_text(
        "\n".join(lines),
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--run",
        action="append",
        type=Path,
        required=True,
        help="Controlled evidence bundle directory; repeat at least 3 times",
    )
    parser.add_argument(
        "--min-runs",
        type=int,
        default=3,
    )
    parser.add_argument(
        "--max-matrix-score-cv-percent",
        type=float,
    )
    parser.add_argument(
        "--max-matrix-allocation-cv-percent",
        type=float,
    )
    parser.add_argument(
        "--max-soak-throughput-cv-percent",
        type=float,
    )
    parser.add_argument(
        "--max-soak-p99-cv-percent",
        type=float,
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        required=True,
    )
    args = parser.parse_args()

    if len(args.run) < args.min_runs:
        raise SystemExit(
            f"At least {args.min_runs} evidence runs are required"
        )

    bundles = [load_bundle(path) for path in args.run]
    errors, environment = compare_environment(bundles)
    errors.extend(compare_matrix_shape(bundles))

    for index, bundle in enumerate(bundles, start=1):
        env = bundle["environment"]
        assert isinstance(env, dict)
        if env.get("evidence_class") != "controlled":
            errors.append(
                f"run{index} is not controlled evidence"
            )

    matrix = matrix_variability(bundles)
    soak = soak_variability(bundles)

    thresholds = {
        "maxMatrixScoreCvPercent":
            args.max_matrix_score_cv_percent,
        "maxMatrixAllocationCvPercent":
            args.max_matrix_allocation_cv_percent,
        "maxSoakThroughputCvPercent":
            args.max_soak_throughput_cv_percent,
        "maxSoakP99CvPercent":
            args.max_soak_p99_cv_percent,
    }

    threshold_failures: list[str] = []
    score_cvs = [
        as_float(item.get("scoreCvPercent"))
        for item in matrix
    ]
    allocation_cvs = [
        as_float(item.get("allocationCvPercent"))
        for item in matrix
    ]
    score_cvs = [value for value in score_cvs if value is not None]
    allocation_cvs = [
        value for value in allocation_cvs if value is not None
    ]

    threshold_failure(
        "Worst matrix score",
        max(score_cvs) if score_cvs else None,
        args.max_matrix_score_cv_percent,
        threshold_failures,
    )
    threshold_failure(
        "Worst matrix allocation",
        max(allocation_cvs) if allocation_cvs else None,
        args.max_matrix_allocation_cv_percent,
        threshold_failures,
    )
    threshold_failure(
        "Soak throughput",
        as_float(
            soak.get("throughputOpsPerSecond", {}).get(
                "cvPercent"
            )
        ),
        args.max_soak_throughput_cv_percent,
        threshold_failures,
    )
    threshold_failure(
        "Soak p99",
        as_float(
            soak.get("p99Micros", {}).get("cvPercent")
        ),
        args.max_soak_p99_cv_percent,
        threshold_failures,
    )

    write_reports(
        args.output_dir,
        bundles,
        environment,
        matrix,
        soak,
        errors,
        threshold_failures,
        thresholds,
    )

    if errors or threshold_failures:
        for item in errors + threshold_failures:
            print(f"ERROR: {item}")
        return 1

    print(
        "V2-D.2 repeatability analysis completed "
        + (
            "with explicit thresholds"
            if any(
                value is not None
                for value in thresholds.values()
            )
            else "in report-only mode"
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
