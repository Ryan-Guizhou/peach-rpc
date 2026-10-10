#!/usr/bin/env python3
"""Validate canonical OTRYX repository identity without network or credentials."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = "Ryan-Guizhou/otryx-rpc"
URL = "https://github.com/" + REPO
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def validate(root: Path = ROOT) -> list[str]:
    """Return concrete identity/documentation issues for one repository root."""
    errors: list[str] = []
    pom_path = root / "pom.xml"
    docs_path = root / "docs/getting-started.md"
    maven_path = root / "docs/reference/maven.md"
    guard_path = root / "docs/publication-readiness.md"

    for path in (pom_path, docs_path, maven_path, guard_path):
        if not path.is_file():
            errors.append(f"Required identity file is missing: {path.relative_to(root)}")
    if errors:
        return errors

    try:
        pom = ET.parse(pom_path).getroot()
    except ET.ParseError as exc:
        return [f"Invalid root pom.xml: {exc}"]

    expected = {
        "m:groupId": "com.peachsoft.otryx",
        "m:url": URL,
        "m:scm/m:url": URL,
        "m:scm/m:connection": "scm:git:" + URL + ".git",
        "m:scm/m:developerConnection": "scm:git:ssh://git@github.com/" + REPO + ".git",
    }
    for xpath, value in expected.items():
        actual = pom.findtext(xpath, namespaces=NS)
        if actual != value:
            errors.append(f"pom.xml {xpath} expected {value!r}, got {actual!r}")

    getting_started = docs_path.read_text(encoding="utf-8")
    if "git clone " + URL + ".git" not in getting_started:
        errors.append("Getting started must clone the canonical repository.")
    if "cd otryx-rpc" not in getting_started:
        errors.append("Getting started must enter the correct checkout directory.")
    if "github.com/Ryan-Guizhou/otryx.git" in getting_started:
        errors.append("Getting started contains the former nonexistent repository URL.")

    maven = maven_path.read_text(encoding="utf-8")
    if "peachsoft.com" not in maven or "otryx.peachsoft.com" not in maven:
        errors.append("Maven instructions must document the correct DNS namespace.")
    if "验证 `io.peach`" in maven or "rpc.peach.io" in maven:
        errors.append("Maven instructions still claim an invalid reverse-DNS mapping.")

    readiness = guard_path.read_text(encoding="utf-8")
    for expected_token in ("5533429", "未", "com.peachsoft.otryx", "商标", "Verified"):
        if expected_token not in readiness:
            errors.append(f"Publication readiness is missing {expected_token!r}.")
    return errors


def main() -> int:
    problems = validate()
    for problem in problems:
        print("ERROR:", problem)
    if problems:
        return 1
    print("OTRYX repository identity and pre-release evidence: validated (not licensed).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
