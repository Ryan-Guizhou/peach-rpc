#!/usr/bin/env python3
"""High-confidence, scope-aware Java API/log convention gate.

Not a Java AST parser. Blocking is limited to explicit scoped patterns;
all historical violations are reported separately in --audit mode.
"""

from __future__ import annotations

import argparse
import bisect
import fnmatch
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RULES = ROOT / "config/java-api-rules.json"


def mask(text: str, hide_strings: bool) -> str:
    """Blank Java comments; optionally blank strings/chars, preserving offsets."""
    output = list(text)
    n = len(text)
    i = 0

    def erase(start: int, end: int) -> None:
        for index in range(start, end):
            if output[index] not in ("\n", "\r"):
                output[index] = " "

    while i < n:
        if text.startswith("//", i):
            end = text.find("\n", i)
            if end == -1:
                end = n
            erase(i, end)
            i = end
        elif text.startswith("/*", i):
            found = text.find("*/", i + 2)
            end = found + 2 if found != -1 else n
            erase(i, end)
            i = end
        elif text.startswith('"""', i):
            found = text.find('"""', i + 3)
            end = found + 3 if found != -1 else n
            if hide_strings:
                erase(i, end)
            i = end
        elif text[i] in ('"', "'"):
            quote = text[i]
            start = i
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                elif text[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
            if hide_strings:
                erase(start, min(i, n))
        else:
            i += 1
    return "".join(output)


def load_rules(path: Path = RULES) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported Java lint rule version")
    rules = data.get("rules")
    if not isinstance(rules, list) or not rules:
        raise ValueError("Missing Java lint rules")
    ids = set()
    for rule in rules:
        if rule["id"] in ids:
            raise ValueError("Duplicate rule ID")
        ids.add(rule["id"])
        if rule.get("severity") not in ("error", "warning"):
            raise ValueError("Invalid severity: " + rule["id"])
        if rule.get("scan") not in ("code", "with-strings"):
            raise ValueError("Invalid scan mode: " + rule["id"])
        if not rule.get("paths") or not isinstance(rule["paths"], list):
            raise ValueError("Missing path scope: " + rule["id"])
        re.compile(rule["pattern"], re.MULTILINE)
    return rules


def scan_source(relative_path: str, source: str, rules: list[dict]) -> list[dict]:
    code = mask(source, hide_strings=True)
    readable = mask(source, hide_strings=False)
    newline_offsets = [index for index, ch in enumerate(source) if ch == "\n"]
    findings = []
    for rule in rules:
        if not any(fnmatch.fnmatchcase(relative_path, p) for p in rule["paths"]):
            continue
        clean = code if rule["scan"] == "code" else readable
        for matched in re.finditer(rule["pattern"], clean, re.MULTILINE):
            findings.append({
                "file": relative_path,
                "line": bisect.bisect_left(newline_offsets, matched.start()) + 1,
                "rule": rule["id"],
                "severity": rule["severity"],
                "message": rule["message"],
            })
    return sorted(findings, key=lambda x: (x["line"], x["rule"]))


def changed_paths(base: str, root: Path = ROOT) -> list[str]:
    command = ["git", "diff", "--name-only", "--diff-filter=ACMR",
               f"{base}...HEAD", "--"]
    result = subprocess.run(
        command, cwd=root, text=True, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=True)
    return [
        name.strip().replace("\\", "/")
        for name in result.stdout.splitlines()
        if name.endswith(".java")
    ]


def inspect(paths: list[str], root: Path, rules: list[dict]) -> list[dict]:
    findings = []
    for relative in sorted(set(paths)):
        file = root / relative
        if not file.is_file():
            continue
        findings.extend(scan_source(
            relative, file.read_text(encoding="utf-8"), rules))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--audit", action="store_true")
    mode.add_argument("--changed", action="store_true")
    parser.add_argument("--base", help="Base commit SHA, required for --changed")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    try:
        rules = load_rules()
        if args.changed:
            if not args.base:
                parser.error("--changed requires --base")
            paths = changed_paths(args.base)
        else:
            paths = [
                p.relative_to(ROOT).as_posix()
                for p in ROOT.rglob("*.java")
                if "target" not in p.parts and ".git" not in p.parts
            ]
        findings = inspect(paths, ROOT, rules)
        report = {
            "schema": "otryx.rpc.java-conventions.audit.v1",
            "mode": "audit" if args.audit else "changed",
            "files_scanned": len(set(paths)),
            "errors": sum(f["severity"] == "error" for f in findings),
            "warnings": sum(f["severity"] == "warning" for f in findings),
            "findings": findings,
        }
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(
                json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8")
        for item in findings[:80]:
            print(f'{item["file"]}:{item["line"]}: '
                  f'{item["severity"]} {item["rule"]}: {item["message"]}')
        print(f'Checked {len(set(paths))} Java files: '
              f'{report["errors"]} errors, {report["warnings"]} warnings.')
        return 1 if args.changed and report["errors"] else 0
    except (ValueError, OSError, subprocess.CalledProcessError,
            json.JSONDecodeError) as error:
        print(f"Java convention check failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
