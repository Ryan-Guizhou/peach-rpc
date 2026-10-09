#!/usr/bin/env python3
"""Aggregate opt-in Checkstyle reports without silently passing missing data."""

import argparse
from collections import Counter
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def summarize(report_files: list[Path], root: Path) -> dict:
    if not report_files:
        raise ValueError("No Checkstyle XML reports found")

    findings = []
    scanned_files = set()
    for report_file in sorted(report_files):
        document = ET.parse(report_file).getroot()
        if document.tag != "checkstyle":
            raise ValueError(f"Not a Checkstyle XML report: {report_file}")

        for entry in document.findall("file"):
            raw_name = entry.get("name")
            if not raw_name:
                raise ValueError(f"Unnamed source in {report_file}")
            source = Path(raw_name)
            if source.is_absolute():
                try:
                    source = source.relative_to(root.resolve())
                except ValueError:
                    # Never export a local absolute path from an untrusted report.
                    source = Path(source.name)
            name = source.as_posix()
            scanned_files.add(name)

            for violation in entry.findall("error"):
                checker = violation.get("source") or "unknown"
                findings.append({
                    "file": name,
                    "line": int(violation.get("line") or 0),
                    "column": int(violation.get("column") or 0),
                    "severity": violation.get("severity") or "unknown",
                    "check": checker.rsplit(".", 1)[-1],
                    "message": violation.get("message") or "",
                })
    findings.sort(key=lambda item: (
        item["file"], item["line"], item["column"], item["check"]))
    return {
        "schema": "peach.rpc.checkstyle.audit.v1",
        "reports": len(report_files),
        "source_files": len(scanned_files),
        "violations": len(findings),
        "by_check": dict(sorted(Counter(
            item["check"] for item in findings).items())),
        "by_severity": dict(sorted(Counter(
            item["severity"] for item in findings).items())),
        "findings": findings,
    }


def markdown(report: dict) -> str:
    lines = [
        "# Peach RPC Checkstyle baseline (advisory only)",
        "",
        "**Existing violations are not yet a blocking gate.** "
        "This report is a measurement, not proof that every Java source "
        "is compliant with all naming, log, concurrency or API rules.",
        "",
        f"- XML reports: {report['reports']}",
        f"- Source files: {report['source_files']}",
        f"- Violations: {report['violations']}",
        "",
        "| Check | Findings |",
        "|---|---:|",
    ]
    for checker, count in sorted(
            report["by_check"].items(),
            key=lambda item: (-item[1], item[0])):
        lines.append(f"| `{checker}` | {count} |")
    lines.extend([
        "",
        "See checkstyle-audit.json for per-file line/column details. "
        "Review and classify baseline items before enabling a blocking gate.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--markdown", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    reports = [
        path for path in root.rglob("checkstyle-result.xml")
        if path.parent.name == "target" and ".git" not in path.parts
    ]
    try:
        summary = summarize(reports, root)
    except (ValueError, ET.ParseError, OSError) as error:
        print(f"Checkstyle report aggregation failed: {error}", file=sys.stderr)
        return 1
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    args.markdown.write_text(markdown(summary), encoding="utf-8")
    print(
        f'Checkstyle advisory: {summary["violations"]} findings '
        f'across {summary["source_files"]} files in {summary["reports"]} reports.')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
