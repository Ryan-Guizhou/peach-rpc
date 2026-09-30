#!/usr/bin/env python3
"""Finalize V2-D.2-E2 analysis without inventing performance decisions."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

DECISION_KEYS = (
    "bufferOwnership",
    "frameAccumulatorCopy",
    "pendingRequestFuture",
    "generatedPath",
    "concurrencyHotspots",
    "compression",
)
ALLOWED = {"DO", "DEFER", "NO_CHANGE"}


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--e1-handoff", type=Path, required=True)
    parser.add_argument("--repeatability-report", type=Path, required=True)
    parser.add_argument("--baseline-candidate", type=Path, required=True)
    parser.add_argument("--decisions", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    errors: list[str] = []
    e1 = load(args.e1_handoff)
    repeatability = load(args.repeatability_report)
    baseline = load(args.baseline_candidate)

    if e1.get("status") != "PASS":
        errors.append("E1 handoff must be PASS")
    if repeatability.get("status") != "PASS":
        errors.append("Repeatability report must be PASS")
    if baseline.get("status") != "CANDIDATE":
        errors.append("Baseline candidate status must be CANDIDATE")

    decisions: dict[str, dict[str, str]] = {}
    if args.decisions is None:
        errors.append(
            "Evidence-backed optimization decisions are required; "
            "do not infer them automatically"
        )
    else:
        raw = load(args.decisions)
        for key in DECISION_KEYS:
            item = raw.get(key)
            if not isinstance(item, dict):
                errors.append(f"Missing decision object: {key}")
                continue
            decision = str(item.get("decision", ""))
            evidence = str(item.get("evidence", "")).strip()
            if decision not in ALLOWED:
                errors.append(
                    f"Decision {key} must be one of {sorted(ALLOWED)}"
                )
            if not evidence:
                errors.append(f"Decision {key} requires evidence")
            decisions[key] = {
                "decision": decision,
                "evidence": evidence,
            }

    status = "PASS" if not errors else "BLOCKED"
    report = {
        "schemaVersion": 1,
        "stage": "V2-D.2-E2",
        "status": status,
        "commit": baseline.get("commit", "unknown"),
        "runnerId": baseline.get("runnerId", "unknown"),
        "runCount": baseline.get("runCount", 0),
        "decisions": decisions,
        "errors": errors,
        "nextStage": "V2-D.3" if status == "PASS" else "V2-D.2-E2",
        "productionSloEstablished": False,
    }

    args.output_dir.mkdir(parents=True, exist_ok=True)
    json_path = args.output_dir / "e2-handoff.json"
    md_path = args.output_dir / "e2-handoff.md"
    json_path.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    lines = [
        "# V2-D.2-E2 Evidence Analysis Handoff",
        "",
        f"**Status:** {status}",
        "",
        f"- Commit: `{report['commit']}`",
        f"- Runner: `{report['runnerId']}`",
        f"- Controlled runs: {report['runCount']}",
        f"- Next stage: `{report['nextStage']}`",
        "",
        "## Optimization decisions",
        "",
        "| Candidate | Decision | Evidence |",
        "|---|---|---|",
    ]
    for key in DECISION_KEYS:
        item = decisions.get(key, {})
        lines.append(
            f"| {key} | {item.get('decision', 'PENDING')} | "
            f"{item.get('evidence', '-')} |"
        )
    if errors:
        lines.extend(["", "## Blocking issues", ""])
        lines.extend(f"- {error}" for error in errors)
    lines.extend(
        [
            "",
            "> PASS only authorizes the evidence-backed V2-D.3 scope. "
            "It does not establish a Production SLO.",
            "",
        ]
    )
    md_path.write_text("\n".join(lines), encoding="utf-8")

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("V2-D.2-E2 handoff passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
