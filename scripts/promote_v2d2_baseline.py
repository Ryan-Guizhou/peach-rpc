#!/usr/bin/env python3
"""Promote a passing V2-D.2 repeatability report into a baseline candidate."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repeatability-report",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        required=True,
    )
    args = parser.parse_args()

    report = json.loads(
        args.repeatability_report.read_text(encoding="utf-8")
    )
    if report.get("status") != "PASS":
        raise SystemExit(
            "Baseline candidate requires a repeatability "
            "report with status PASS"
        )
    if int(report.get("runCount", 0)) < 3:
        raise SystemExit(
            "Baseline candidate requires at least 3 controlled runs"
        )
    if report.get("errors") or report.get("thresholdFailures"):
        raise SystemExit(
            "Baseline candidate cannot contain comparability "
            "or threshold failures"
        )

    environment = report.get("environment", {})
    soak = report.get("soakVariability", {})
    matrix = report.get("matrixVariability", [])

    candidate = {
        "schemaVersion": 1,
        "status": "CANDIDATE",
        "source": str(args.repeatability_report),
        "commit": environment.get("commit", "unknown"),
        "runnerId": environment.get("runner_id", "unknown"),
        "runCount": report.get("runCount"),
        "runs": report.get("runs", []),
        "thresholds": report.get("thresholds", {}),
        "environment": environment,
        "soakBaseline": {
            metric: values.get("median")
            for metric, values in soak.items()
            if isinstance(values, dict)
        },
        "matrixBaseline": [
            {
                "point": item.get("point", {}),
                "scoreMedian": item.get("scoreMedian"),
                "allocationMedianBytesPerOp":
                    item.get("allocationMedianBytesPerOp"),
                "scoreCvPercent": item.get("scoreCvPercent"),
                "allocationCvPercent":
                    item.get("allocationCvPercent"),
            }
            for item in matrix
        ],
        "promotionState":
            "requires-human-review-before-production-baseline",
    }

    args.output_dir.mkdir(parents=True, exist_ok=True)
    json_path = args.output_dir / "baseline-candidate.json"
    markdown_path = args.output_dir / "baseline-candidate.md"

    json_path.write_text(
        json.dumps(candidate, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    lines = [
        "# V2-D.2 Baseline Candidate",
        "",
        "**Status:** CANDIDATE",
        "",
        f"- Commit: `{candidate['commit']}`",
        f"- Runner ID: `{candidate['runnerId']}`",
        f"- Controlled runs: {candidate['runCount']}",
        f"- Matrix points: {len(candidate['matrixBaseline'])}",
        "",
        "## Soak medians",
        "",
        "| Metric | Median |",
        "|---|---:|",
    ]
    for metric, value in candidate["soakBaseline"].items():
        lines.append(f"| {metric} | {value} |")

    lines.extend(
        [
            "",
            "## Promotion rule",
            "",
            "This file is a baseline **candidate**, not a Production SLO.",
            "",
            "Promotion requires engineering review of:",
            "",
            "- runner/environment suitability;",
            "- threshold policy rationale;",
            "- fault and TLS evidence;",
            "- allocation/GC interpretation;",
            "- capacity-planning implications;",
            "- Buffer ownership and Future/PendingRequest decisions.",
            "",
        ]
    )
    markdown_path.write_text(
        "\n".join(lines),
        encoding="utf-8",
    )

    print(f"Baseline candidate generated: {json_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
