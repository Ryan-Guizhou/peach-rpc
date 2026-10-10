#!/usr/bin/env python3
"""Scope-aware, auditable checker for protocol/configuration magic literals.

This lightweight lexical detector is intentionally not a Java AST parser.
It catches high-confidence API positions rather than every Java string literal.
"""

from __future__ import annotations

import argparse
import bisect
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

from check_java_conventions import mask

ROOT = Path(__file__).resolve().parents[1]

RULES = {
    "ML001": (
        "Use a named compile-time SPI extension identifier",
        re.compile(r'@Extension\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
    "ML002": (
        "Use a named registry provider option key",
        re.compile(r'\bproviderOption\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
    "ML003": (
        "Use a named protocol/configuration Map.entry key",
        re.compile(r'\bMap\.entry\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
    "ML004": (
        "Use a named protocol/configuration Map.of key",
        re.compile(r'\bMap\.of\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
    "ML005": (
        "Use a named JSON field key",
        re.compile(r'\.\s*path\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
    "ML006": (
        "Use a named metric tag key",
        re.compile(r'\.\s*tag\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
    "ML007": (
        "Use a named environment/JVM property key",
        re.compile(r'\bSystem\s*\.\s*(?:getenv|getProperty)\s*\(\s*"([^"\n]+)"'),
    ),
    "ML008": (
        "Use a named HTTP header key",
        re.compile(r'\.\s*(?:getHeader|putHeader|setHeader|header)\s*\(\s*"([a-zA-Z][\w.-]+)"'),
    ),
}


def production_path(relative: str) -> bool:
    """Limit blocking policy to hand-written production Java sources."""
    normalized = "/" + relative.replace("\\", "/")
    return "/src/main/java/" in normalized and relative.endswith(".java")


def scan_source(relative: str, source: str) -> list[dict]:
    """Report a high-confidence literal use, preserving line numbers."""
    if not production_path(relative):
        return []
    readable = mask(source, hide_strings=False)
    newline_offsets = [i for i, ch in enumerate(source) if ch == "\n"]
    findings = []
    for rule_id, (message, regex) in RULES.items():
        for occurrence in regex.finditer(readable):
            findings.append({
                "file": relative,
                "line": bisect.bisect_left(newline_offsets, occurrence.start()) + 1,
                "rule": rule_id,
                "literal": occurrence.group(1),
                "message": message,
            })
    return sorted(findings, key=lambda item: (item["line"], item["rule"]))


def changed_java(base: str) -> list[str]:
    """Read changed Java source paths without scanning deleted files."""
    raw = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMR",
         f"{base}...HEAD", "--"],
        cwd=ROOT, check=True, text=True, capture_output=True,
    ).stdout
    return [path.strip() for path in raw.splitlines()
            if production_path(path.strip())]


def scan_files(paths: list[str], root: Path = ROOT) -> list[dict]:
    """Scan existing paths only; keep stable ordering for CI evidence."""
    findings = []
    for relative in sorted(set(paths)):
        file = root / relative
        if file.is_file():
            findings.extend(scan_source(
                relative, file.read_text(encoding="utf-8")))
    return findings


def main() -> int:
    """Expose nonblocking audit, changed-file check and repository gate."""
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--audit", action="store_true")
    mode.add_argument("--changed", action="store_true")
    mode.add_argument("--enforce-all", action="store_true")
    parser.add_argument("--base", help="Git merge-base input for --changed")
    parser.add_argument("--report", type=Path, help="JSON audit report path")
    args = parser.parse_args()
    if args.changed and not args.base:
        parser.error("--changed requires --base")

    try:
        if args.changed:
            paths = changed_java(args.base)
        else:
            paths = [p.relative_to(ROOT).as_posix()
                     for p in ROOT.rglob("*.java")
                     if production_path(p.relative_to(ROOT).as_posix())
                     and "target" not in p.parts and ".git" not in p.parts]
        findings = scan_files(paths)
        counts = Counter(item["rule"] for item in findings)
        report = {
            "schema": "otryx.rpc.magic-literals.audit.v1",
            "mode": "audit" if args.audit else (
                "changed" if args.changed else "enforce-all"),
            "files_scanned": len(set(paths)),
            "total_findings": len(findings),
            "counts": dict(sorted(counts.items())),
            "findings": findings,
        }
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(
                json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
        for finding in findings[:80]:
            print(f"{finding['file']}:{finding['line']} "
                  f"{finding['rule']} literal={finding['literal']!r}: "
                  f"{finding['message']}")
        print(f"Magic-literal scan: {len(set(paths))} production Java files, "
              f"{len(findings)} findings, mode={report['mode']}")
        return 1 if not args.audit and findings else 0
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"Magic-literal check failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
