#!/usr/bin/env python3
"""Repository quality gate for Peach RPC 1.0.x."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SECTION = re.compile(r"<!--\s*doc-section:([a-zA-Z0-9_.-]+)\s*-->")
RELEASE_STATUS = re.compile(
    r"<!--\s*release-status:([a-zA-Z0-9_.-]+)=([a-zA-Z0-9_.-]+)\s*-->"
)
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
CHINESE = re.compile(r"[\u4e00-\u9fff]")
LOGGER_WITH_CHINESE = re.compile(
    r"LOGGER\.(?:trace|debug|info|warn|error)\([^\n]*[\u4e00-\u9fff]"
)

EXPECTED_MODULES = (
    "peach-rpc-core",
    "peach-rpc-codegen",
    "peach-rpc-codec-fory",
    "peach-rpc-transport-vertx",
    "peach-rpc-registry-etcd",
    "peach-rpc-registry-nacos",
    "peach-rpc-proxy-cglib",
    "peach-rpc-proxy-bytebuddy",
    "peach-rpc-observability-micrometer",
    "peach-rpc-observability-opentelemetry",
    "peach-rpc-observability-jfr",
    "peach-rpc-spring-boot-autoconfigure",
    "peach-rpc-spring-boot-starter",
    "peach-rpc-spring-boot-starter-lite",
    "peach-rpc-examples",
    "peach-rpc-benchmarks",
)

FORBIDDEN_CORE_IMPORTS = (
    "io.vertx.",
    "io.etcd.",
    "com.alibaba.nacos.",
    "net.sf.cglib.",
    "org.apache.fory.",
    "org.springframework.",
    "io.micrometer.",
    "io.opentelemetry.",
    "jdk.jfr.",
)

REQUIRED_FILES = (
    "README.md",
    "README.en-US.md",
    "LICENSE",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SECURITY.md",
    "CHANGELOG.md",
    "ROADMAP.md",
    "docs/requirements-blueprint.md",
    "docs/technical-solution.md",
    "docs/features.md",
    "docs/detailed-design.md",
    "docs/project-structure.md",
    "docs/getting-started.md",
    "docs/architecture.md",
    "docs/protocol.md",
    "docs/wire-compatibility.md",
    "docs/starter.md",
    "docs/spi.md",
    "docs/registry-nacos.md",
    "docs/security.md",
    "docs/observability.md",
    "docs/production-observability.md",
    "docs/production-configuration.md",
    "docs/performance.md",
    "docs/performance-evidence.md",
    "docs/capacity-planning.md",
    "docs/upgrade-rollback.md",
    "docs/release-policy.md",
    "docs/release-readiness.md",
    "docs/maven.md",
    "docs/development.md",
    "docs/faq.md",
    "docs/release-notes-1.0.0-RC1.md",
    "docs/release-notes-1.0.0.md",
    "docs/release-status.properties",
    ".github/workflows/ci.yml",
    ".github/workflows/release-readiness.yml",
    ".github/workflows/release.yml",
    ".github/workflows/rolling-compatibility.yml",
    ".github/workflows/etcd-chaos.yml",
    ".github/workflows/nacos-chaos.yml",
    "scripts/check_central_publication.py",
)

DEPRECATED_DOCS = (
    "docs/high-performance-kernel-v2-plan.md",
    "docs/high-performance-kernel-v2b.md",
    "docs/implementation-plan.md",
    "docs/performance-evidence-v2d2.md",
    "docs/performance-kernel-v2d.md",
    "docs/production-kernel-v2b1-phase2.md",
    "docs/production-kernel-v2b1.md",
    "docs/production-kernel-v2b2.md",
    "docs/production-kernel-v2c2.md",
    "docs/production-kernel-v2c3-plan.md",
    "docs/production-roadmap.md",
    "docs/readiness.md",
    "docs/v2d2-e1-controlled-evidence.md",
    "docs/version-roadmap-to-ga.md",
)


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    raise SystemExit(1)


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"Invalid properties line: {raw}")
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def check_required_files() -> None:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).is_file()]
    if missing:
        fail("Missing required project files: " + ", ".join(missing))
    stale = [path for path in DEPRECATED_DOCS if (ROOT / path).exists()]
    if stale:
        fail("Deprecated stage documents must be removed: " + ", ".join(stale))


def check_release_status() -> None:
    status = properties(ROOT / "docs" / "release-status.properties")
    expected = {
        "project": "ga",
        "release_candidate": "1.0.0-RC1",
        "wire": "v1",
        "java": "21",
    }
    for key, value in expected.items():
        if status.get(key) != value:
            fail(f"Unexpected release status: {key}={status.get(key)!r}, expected {value!r}")

    version = status.get("version", "")
    if not re.fullmatch(r"1\.0\.\d+", version):
        fail(
            "Stable source version must be a 1.0.x release, "
            f"got {version!r}"
        )

    notes = ROOT / "docs" / f"release-notes-{version}.md"
    if not notes.is_file():
        fail(
            "Missing release notes for current source version: "
            f"{notes.relative_to(ROOT)}"
        )

    pom = ET.parse(ROOT / "pom.xml").getroot()
    revision = pom.findtext("m:properties/m:revision", namespaces=NS)
    if revision != version:
        fail(
            f"Maven revision {revision!r} does not match "
            f"release status version {version!r}"
        )

    for relative in ("README.md", "README.en-US.md"):
        text = (ROOT / relative).read_text(encoding="utf-8")
        markers = dict(RELEASE_STATUS.findall(text))
        for key in ("project", "version", "wire"):
            if markers.get(key) != status[key]:
                fail(
                    f"Release status drift in {relative}: "
                    f"{key}={markers.get(key)!r}, expected {status[key]!r}"
                )


def check_readme_parity() -> None:
    zh = (ROOT / "README.md").read_text(encoding="utf-8")
    en = (ROOT / "README.en-US.md").read_text(encoding="utf-8")
    zh_sections = SECTION.findall(zh)
    en_sections = SECTION.findall(en)
    if not zh_sections or zh_sections != en_sections:
        fail(f"README section markers differ: ZH={zh_sections}, EN={en_sections}")


def check_public_docs_are_ga_clean() -> None:
    forbidden = (
        "0.1.0-SNAPSHOT",
        "capability-status:v2-",
        "project=preview",
        "Production Roadmap / Capability Matrix",
    )
    paths = [ROOT / "README.md", ROOT / "README.en-US.md"]
    paths.extend(ROOT.glob("docs/*.md"))
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for token in forbidden:
            if token in text:
                fail(f"Stale pre-1.0 wording in {path.relative_to(ROOT)}: {token}")


def check_chinese_first_docs() -> None:
    for path in [ROOT / "CONTRIBUTING.md", *(ROOT / "docs").glob("*.md")]:
        if not CHINESE.search(path.read_text(encoding="utf-8")):
            fail(f"Chinese-first documentation expected: {path.relative_to(ROOT)}")


def check_markdown_links() -> None:
    for path in ROOT.rglob("*.md"):
        if "target" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        for raw in MARKDOWN_LINK.findall(text):
            target = raw.split("#", 1)[0].strip()
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            if not (path.parent / target).resolve().exists():
                fail(f"Broken local link in {path.relative_to(ROOT)}: {raw}")


def check_maven_reactor() -> None:
    root = ET.parse(ROOT / "pom.xml").getroot()
    modules = tuple(node.text for node in root.findall("m:modules/m:module", NS))
    if modules != EXPECTED_MODULES:
        fail(f"Unexpected Maven modules: {modules}")
    for module in modules:
        if not (ROOT / module / "pom.xml").is_file():
            fail(f"Missing module pom.xml: {module}")


def check_core_boundaries() -> None:
    for path in (ROOT / "peach-rpc-core" / "src/main/java").rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for prefix in FORBIDDEN_CORE_IMPORTS:
            if f"import {prefix}" in text:
                fail(f"Forbidden infrastructure import in {path.relative_to(ROOT)}: {prefix}")


def check_java_hygiene() -> None:
    for path in ROOT.glob("peach-rpc-*/src/**/*.java"):
        text = path.read_text(encoding="utf-8")
        if "System.out" in text or "System.err" in text:
            fail(f"System output is not allowed: {path.relative_to(ROOT)}")
        if LOGGER_WITH_CHINESE.search(text):
            fail(f"Runtime log message must be English: {path.relative_to(ROOT)}")
        for number, line in enumerate(text.splitlines(), 1):
            if line.rstrip() != line:
                fail(f"Trailing whitespace: {path.relative_to(ROOT)}:{number}")
            if "\t" in line:
                fail(f"Tab indentation: {path.relative_to(ROOT)}:{number}")
            if len(line) > 120:
                fail(f"Java line exceeds 120 characters: {path.relative_to(ROOT)}:{number}")
            stripped = line.strip()
            if stripped.startswith("import ") and stripped.endswith(".*;"):
                fail(f"Wildcard import is not allowed: {path.relative_to(ROOT)}:{number}")


def main() -> int:
    check_required_files()
    check_release_status()
    check_readme_parity()
    check_public_docs_are_ga_clean()
    check_chinese_first_docs()
    check_markdown_links()
    check_maven_reactor()
    check_core_boundaries()
    check_java_hygiene()
    print("Peach RPC 1.0.x repository checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
