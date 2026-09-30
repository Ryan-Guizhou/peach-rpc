#!/usr/bin/env python3
"""Finalize V2-D.2-E1 evidence collection without defining E2 thresholds."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def run_tool(*args: str) -> None:
    result = subprocess.run(
        [sys.executable, *args],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        raise ValueError(result.stdout.strip())


def validate_bundle(root: Path) -> dict[str, object]:
    required = (
        root / "environment.properties",
        root / "matrix" / "summary.csv",
        root / "soak.json",
        root / "validation-report.json",
        root / "decision-inputs.json",
        root / "evidence-manifest.json",
    )
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise ValueError(
            "Incomplete E1 evidence bundle: " + ", ".join(missing)
        )

    run_tool(
        "scripts/manage_v2d2_evidence_manifest.py",
        "verify",
        "--bundle",
        str(root),
    )

    validation = json.loads(
        (root / "validation-report.json").read_text(encoding="utf-8")
    )
    if validation.get("status") != "PASS":
        raise ValueError(f"Evidence validation is not PASS: {root}")

    environment = properties(root / "environment.properties")
    if environment.get("evidence_class") != "controlled":
        raise ValueError(f"Evidence is not controlled: {root}")
    if environment.get("host_fingerprint_sha256") in {
        None,
        "",
        "unknown",
    }:
        raise ValueError(f"Evidence has no host fingerprint: {root}")

    soak = json.loads((root / "soak.json").read_text(encoding="utf-8"))
    if int(soak.get("concurrency", 0)) < 10000:
        raise ValueError(f"E1 soak concurrency is below 10000: {root}")
    if float(soak.get("durationSeconds", 0.0)) < 1800.0:
        raise ValueError(f"E1 soak duration is below 1800s: {root}")
    if int(soak.get("successes", 0)) <= 0:
        raise ValueError(f"E1 soak has no successful requests: {root}")

    return {
        "root": str(root),
        "runId": environment.get("run_id", "unknown"),
        "commit": environment.get("commit", "unknown"),
        "runnerId": environment.get("runner_id", "unknown"),
        "hostFingerprintSha256":
            environment.get("host_fingerprint_sha256", "unknown"),
        "concurrency": int(soak.get("concurrency", 0)),
        "durationSeconds": float(soak.get("durationSeconds", 0.0)),
        "throughputOpsPerSecond":
            soak.get("throughputOpsPerSecond"),
        "p99Micros": soak.get("p99Micros"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--run",
        action="append",
        type=Path,
        required=True,
        help="Controlled E1 evidence bundle; repeat at least 3 times",
    )
    parser.add_argument("--min-runs", type=int, default=3)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    errors: list[str] = []
    bundles: list[dict[str, object]] = []

    if len(args.run) < args.min_runs:
        errors.append(
            f"At least {args.min_runs} controlled evidence runs are required"
        )

    for root in args.run:
        try:
            bundles.append(validate_bundle(root))
        except (ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))

    run_ids = [str(item["runId"]) for item in bundles]
    if any(run_id in {"", "unknown"} for run_id in run_ids):
        errors.append("Every E1 evidence bundle requires a non-unknown run_id")
    if len(run_ids) != len(set(run_ids)):
        errors.append("E1 evidence run_id values must be unique")

    repeatability_dir = args.output_dir / "repeatability-report-only"
    repeatability: dict[str, object] = {}
    if not errors and len(bundles) >= args.min_runs:
        command = ["scripts/compare_v2d2_evidence.py"]
        for root in args.run:
            command.extend(["--run", str(root)])
        command.extend(["--output-dir", str(repeatability_dir)])
        try:
            run_tool(*command)
            repeatability = json.loads(
                (repeatability_dir / "repeatability-report.json").read_text(
                    encoding="utf-8"
                )
            )
            if repeatability.get("status") != "REPORT_ONLY":
                errors.append(
                    "E1 requires threshold-free REPORT_ONLY repeatability; "
                    f"found {repeatability.get('status')!r}"
                )
            if repeatability.get("errors"):
                errors.append(
                    "Repeatability contains comparability failures"
                )
        except (ValueError, json.JSONDecodeError, FileNotFoundError) as exc:
            errors.append(str(exc))

    status = "PASS" if not errors else "FAIL"
    first = bundles[0] if bundles else {}
    report = {
        "schemaVersion": 1,
        "status": status,
        "stage": "V2-D.2-E1",
        "runCount": len(bundles),
        "runIds": run_ids,
        "commit": first.get("commit", "unknown"),
        "runnerId": first.get("runnerId", "unknown"),
        "hostFingerprintSha256":
            first.get("hostFingerprintSha256", "unknown"),
        "bundles": bundles,
        "repeatabilityStatus":
            repeatability.get("status", "NOT_RUN"),
        "matrixPointCount":
            repeatability.get("matrixPointCount"),
        "errors": errors,
        "nextStage":
            "V2-D.2-E2" if status == "PASS" else "V2-D.2-E1",
        "productionSloEstablished": False,
    }

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "e1-handoff.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    lines = [
        "# V2-D.2-E1 Evidence Handoff",
        "",
        f"**Status:** {status}",
        "",
        f"- Controlled runs: {len(bundles)}",
        f"- Commit: `{report['commit']}`",
        f"- Runner ID: `{report['runnerId']}`",
        f"- Host fingerprint SHA-256: "
        f"`{report['hostFingerprintSha256']}`",
        f"- Repeatability: `{report['repeatabilityStatus']}`",
        f"- Next stage: `{report['nextStage']}`",
        "",
        "## Runs",
        "",
        "| Run ID | Concurrency | Duration s | Throughput ops/s | p99 us |",
        "|---|---:|---:|---:|---:|",
    ]
    for item in bundles:
        lines.append(
            f"| {item['runId']} | {item['concurrency']} | "
            f"{item['durationSeconds']} | "
            f"{item.get('throughputOpsPerSecond', '-')} | "
            f"{item.get('p99Micros', '-')} |"
        )

    if errors:
        lines.extend(["", "## Failures", ""])
        lines.extend(f"- {error}" for error in errors)

    lines.extend(
        [
            "",
            "> PASS closes only the V2-D.2-E1 evidence-collection gate. "
            "It does not define repeatability thresholds, promote a "
            "performance baseline, or establish a Production SLO.",
            "",
        ]
    )
    (args.output_dir / "e1-handoff.md").write_text(
        "\n".join(lines),
        encoding="utf-8",
    )

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    print(
        "V2-D.2-E1 evidence collection gate passed; "
        "handoff is ready for V2-D.2-E2"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
