#!/usr/bin/env python3
"""Aggregate opt-in Checkstyle reports without silently passing missing data."""

import argparse
from collections import Counter
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def reactor_module_directories(root: Path) -> set[Path]:
    """Resolve every Maven reactor module, excluding stand-alone tools.

    The reactor is defined by nested pom.xml modules, not filesystem globs.
    """
    root = root.resolve()
    pending = [root]
    visited = set()
    while pending:
        folder = pending.pop()
        if folder in visited:
            raise ValueError(f"Duplicate Maven reactor module: {folder}")
        if not folder.is_relative_to(root):
            raise ValueError("Maven module path escapes repository root")
        visited.add(folder)
        pom = folder / "pom.xml"
        if not pom.is_file():
            raise ValueError(f"Missing Maven module POM: {pom}")
        project = ET.parse(pom).getroot()
        namespace = ""
        if project.tag.startswith("{"):
            namespace = project.tag.split("}", 1)[0] + "}"
        if project.tag != namespace + "project":
            raise ValueError(f"Unexpected Maven POM root: {pom}")
        modules = project.find(namespace + "modules")
        if modules is None:
            continue
        for child in modules.findall(namespace + "module"):
            name = (child.text or "").strip()
            if not name:
                raise ValueError(f"Empty Maven module in {pom}")
            resolved = (folder / name).resolve()
            if not resolved.is_relative_to(root):
                raise ValueError(f"Maven module escapes repository root: {name}")
            pending.append(resolved)
    return visited


def validate_reactor_reports(report_files: list[Path], root: Path) -> None:
    """Fail closed when a module was silently skipped during the audit."""
    expected = {
        module / "target" / "checkstyle-result.xml"
        for module in reactor_module_directories(root)
    }
    actual = {report.resolve() for report in report_files}
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing or extra:
        messages = []
        if missing:
            messages.append(
                "Missing Checkstyle module reports: " +
                ", ".join(str(path.relative_to(root.resolve())) for path in missing))
        if extra:
            messages.append("Unexpected Checkstyle reports: " +
                            ", ".join(str(path) for path in extra))
        raise ValueError("; ".join(messages))


def validate_source_coverage(root: Path, report: dict) -> None:
    """Reject silently unscanned Java source files in the Maven reactor."""
    root = root.resolve()
    expected = set()
    for module in reactor_module_directories(root):
        for scope in ("main", "test"):
            source_root = module / "src" / scope / "java"
            if source_root.is_dir():
                expected.update(
                    java.relative_to(root).as_posix()
                    for java in source_root.rglob("*.java")
                    if java.is_file()
                )
    observed = set(report["scanned_paths"])
    missing = sorted(expected - observed)
    extra = sorted(observed - expected)
    if missing or extra:
        details = []
        if missing:
            details.append("Unscanned Java source files: " + ", ".join(missing[:12]))
        if extra:
            details.append("Unexpected Java sources in XML: " + ", ".join(extra[:12]))
        raise ValueError("; ".join(details))


def validate_strict_gate(report: dict) -> None:
    """Reject any violation, including Checkstyle warning-severity findings."""
    if report["reports"] < 1 or report["source_files"] < 1:
        raise ValueError("Checkstyle strict gate requires nonempty audit evidence")
    count = report["violations"]
    if count:
        details = ", ".join(
            f'{f["file"]}:{f["line"]} {f["check"]}'
            for f in report["findings"][:8]
        )
        raise ValueError(
            f"Checkstyle strict gate rejected {count} violation(s): {details}")


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
        "scanned_paths": sorted(scanned_files),
        "violations": len(findings),
        "by_check": dict(sorted(Counter(
            item["check"] for item in findings).items())),
        "by_severity": dict(sorted(Counter(
            item["severity"] for item in findings).items())),
        "findings": findings,
    }


def markdown(report: dict, enforced: bool = False) -> str:
    title = ("# Peach RPC Checkstyle (strict CI gate)"
             if enforced else "# Peach RPC Checkstyle baseline (advisory only)")
    explanation = (
        "**All configured Checkstyle findings block this CI run.** "
        "This is a scoped naming, import, Javadoc and formatting gate, "
        "not a semantic correctness or sensitive-data-flow proof."
        if enforced else
        "**Existing violations are not yet a blocking gate.** "
        "This report is a measurement, not proof that every Java source "
        "is compliant with all naming, log, concurrency or API rules.")
    lines = [
        title,
        "",
        explanation,
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
    parser.add_argument("--enforce-zero", action="store_true",
                        help="Fail if any Checkstyle findings are present")
    args = parser.parse_args()
    root = args.root.resolve()
    reports = [
        path for path in root.rglob("checkstyle-result.xml")
        if path.parent.name == "target" and ".git" not in path.parts
    ]
    try:
        validate_reactor_reports(reports, root)
        summary = summarize(reports, root)
    except (ValueError, ET.ParseError, OSError) as error:
        print(f"Checkstyle report aggregation failed: {error}", file=sys.stderr)
        return 1
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    args.markdown.write_text(
        markdown(summary, enforced=args.enforce_zero), encoding="utf-8")
    if args.enforce_zero:
        try:
            validate_source_coverage(root, summary)
            validate_strict_gate(summary)
        except ValueError as error:
            print(f"Checkstyle quality gate failed: {error}", file=sys.stderr)
            return 1
    mode = "strict gate" if args.enforce_zero else "advisory"
    print(
        f'Checkstyle {mode}: {summary["violations"]} findings '
        f'across {summary["source_files"]} files in {summary["reports"]} reports.')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
