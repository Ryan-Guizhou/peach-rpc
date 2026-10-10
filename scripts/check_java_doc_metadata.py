#!/usr/bin/env python3
"""Audit OTRYX type-level Peach Cloud Javadoc metadata without inventing history.

This is a source-text guard, not a Java AST parser. Checkstyle and DocLint remain
responsible for Java grammar and public API documentation correctness.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from datetime import datetime
from pathlib import Path

from check_java_conventions import mask

ROOT = Path(__file__).resolve().parents[1]
TYPE = re.compile(
    r"@interface\s+(?P<annotation>[A-Za-z_$][\w$]*)"
    r"|\b(?:class|interface|enum|record)\s+(?P<type>[A-Za-z_$][\w$]*)"
)
JAVADOC = re.compile(r"/\*\*[\s\S]*?\*/")
REQUIRED = ("Author", "Version", "CreateTime")
VERSION = re.compile(r"\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?")
DATE = re.compile(r"\d{4}/\d{1,2}/\d{1,2} \d{2}:\d{2}")
TAG = re.compile(r"(?m)^\s*\*\s*@(?P<tag>Author|Version|CreateTime)\s+(?P<value>[^\r\n]*)")
SKIP_DIRS = {".git", "target", "node_modules", "__pycache__"}


def tracked_java_sources(root: Path = ROOT) -> list[str]:
    """Return existing hand-maintained Java sources, including tests and benchmark CLI."""
    return sorted(
        path.relative_to(root).as_posix()
        for path in root.rglob("*.java")
        if not any(part in SKIP_DIRS for part in path.relative_to(root).parts)
    )


def changed_java_sources(base: str, root: Path = ROOT) -> list[str]:
    """Return added/modified/renamed Java sources relative to a Git base commit."""
    output = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMR", f"{base}...HEAD", "--"],
        cwd=root, text=True, capture_output=True, check=True,
    ).stdout
    return sorted(set(
        item for item in output.splitlines() if item.endswith(".java")
    ))


def top_level_types(source: str):
    """Yield lexical top-level type names and offsets; ignore strings and nested types."""
    code = mask(source, hide_strings=True)
    previous = 0
    depth = 0
    for match in TYPE.finditer(code):
        for char in code[previous:match.start()]:
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
        previous = match.start()
        if depth == 0:
            yield match.group("annotation") or match.group("type"), match.start()


def nearest_javadoc(source: str, offset: int) -> str | None:
    """Find the directly associated documentation block before type/annotation lines."""
    candidates = list(JAVADOC.finditer(source, 0, offset))
    if not candidates:
        return None
    match = candidates[-1]
    middle = mask(source[match.end():offset], hide_strings=True)
    # A semicolon or another type means the previous block belongs to something else.
    if ";" in middle or TYPE.search(middle):
        return None
    return match.group(0)


def check_metadata(doc: str | None) -> list[str]:
    """Validate metadata presence, uniqueness and basic syntax without guessing values."""
    if doc is None:
        return ["type Javadoc is missing"]
    values: dict[str, list[str]] = {name: [] for name in REQUIRED}
    for match in TAG.finditer(doc):
        values[match.group("tag")].append(match.group("value").strip())
    errors = []
    for name in REQUIRED:
        found = values[name]
        if len(found) != 1:
            errors.append(f"@{name} must appear exactly once")
            continue
        val = found[0]
        if (not val or "<" in val or ">" in val
                or val.lower() in {"unknown", "tbd", "todo", "n/a", "pending"}):
            errors.append(f"@{name} has an unverified placeholder or empty value")
        elif name == "Version" and not VERSION.fullmatch(val):
            errors.append("@Version must use a version such as 1.0.0-SNAPSHOT")
        elif name == "CreateTime":
            if not DATE.fullmatch(val):
                errors.append("@CreateTime must use yyyy/M/d HH:mm")
            else:
                try:
                    datetime.strptime(val, "%Y/%m/%d %H:%M")
                except ValueError:
                    errors.append("@CreateTime must be a real date and time")
    return errors


def scan_source(path: str, source: str) -> tuple[int, list[dict]]:
    """Collect type metadata findings with useful line and symbol locations."""
    findings = []
    count = 0
    for name, offset in top_level_types(source):
        count += 1
        line = source.count("\n", 0, offset) + 1
        for message in check_metadata(nearest_javadoc(source, offset)):
            findings.append({
                "file": path, "line": line, "type": name, "message": message,
            })
    return count, findings


def inspect(paths: list[str], root: Path = ROOT) -> tuple[int, list[dict]]:
    total = 0
    findings = []
    for relative in sorted(set(paths)):
        file = root / relative
        if not file.is_file():
            continue
        scanned, found = scan_source(relative, file.read_text(encoding="utf-8"))
        total += scanned
        findings.extend(found)
    return total, findings


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--audit", action="store_true", help="report historical gaps without failing")
    mode.add_argument("--changed", action="store_true", help="enforce all changed Java files")
    mode.add_argument("--enforce-all", action="store_true", help="enforce all Java files after migration")
    parser.add_argument("--base", help="Git base SHA for --changed")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    if args.changed and not args.base:
        parser.error("--changed requires --base")
    paths = changed_java_sources(args.base) if args.changed else tracked_java_sources()
    count, findings = inspect(paths)
    report = {
        "schema": "otryx.rpc.javadoc-metadata.v1",
        "mode": "changed" if args.changed else "enforce-all" if args.enforce_all else "audit",
        "files_scanned": len(paths),
        "top_level_types": count,
        "findings": findings,
    }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    print(f"[javadoc-metadata] {report['mode']}: {len(paths)} Java files, "
          f"{count} top-level types, {len(findings)} findings")
    for entry in findings[:30]:
        print(f"  {entry['file']}:{entry['line']} ({entry['type']}): {entry['message']}")
    if len(findings) > 30:
        print(f"  ... {len(findings) - 30} additional findings in report")
    return 1 if findings and (args.changed or args.enforce_all) else 0


if __name__ == "__main__":
    raise SystemExit(main())
