#!/usr/bin/env python3
"""Peach RPC repository quality checks."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
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
    "peach-rpc-examples",
    "peach-rpc-benchmarks",
)
SECTION = re.compile(r"<!--\s*doc-section:([a-zA-Z0-9_.-]+)\s*-->")
CAPABILITY_STATUS = re.compile(
    r"<!--\s*capability-status:([a-zA-Z0-9_.-]+)=([a-zA-Z0-9_.-]+)\s*-->"
)
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
CHINESE = re.compile(r"[\u4e00-\u9fff]")
LOGGER_WITH_CHINESE = re.compile(r"LOGGER\.(?:trace|debug|info|warn|error)\([^\n]*[\u4e00-\u9fff]")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    raise SystemExit(1)



STATUS_DOCS = {
    "README.md": (
        "project",
        "v2-c3",
        "v2-d",
        "v2-d2",
        "v2-e1",
        "v2-e2",
        "v2-f1",
        "v2-f2",
        "v2-g1",
        "v2-g2",
    ),
    "README.en-US.md": (
        "project",
        "v2-c3",
        "v2-d",
        "v2-d2",
        "v2-e1",
        "v2-e2",
        "v2-f1",
        "v2-f2",
        "v2-g1",
        "v2-g2",
    ),
    "docs/production-roadmap.md": (
        "project",
        "v2-c3",
        "v2-d",
        "v2-d2",
        "v2-d2-e2",
        "v2-d3",
        "v2-d4",
        "v2-e1",
        "v2-e2",
        "v2-f1",
        "v2-f2",
        "v2-g1",
        "v2-g2",
    ),
    "docs/readiness.md": (
        "project",
        "v2-c3",
        "v2-d",
        "v2-d2",
        "v2-d2-e2",
        "v2-d3",
        "v2-d4",
        "v2-e1",
        "v2-e2",
        "v2-f1",
        "v2-f2",
        "v2-g1",
        "v2-g2",
    ),
    "docs/version-roadmap-to-ga.md": (
        "project",
        "v2-d2-e2",
        "v2-d3",
        "v2-d4",
        "v2-e1",
        "v2-e2",
        "v2-f1",
        "v2-f2",
        "v2-g1",
        "v2-g2",
    ),
    "docs/performance.md": ("v2-d", "v2-d2"),
    "docs/performance-kernel-v2d.md": ("v2-d", "v2-d2"),
    "docs/performance-evidence-v2d2.md": ("v2-d", "v2-d2"),
    "docs/security.md": ("v2-c3",),
    "docs/observability.md": ("v2-c3",),
    "docs/wire-compatibility.md": ("v2-e1", "v2-e2"),
    "docs/upgrade-rollback.md": ("v2-e2", "v2-g2"),
    "docs/production-configuration.md": ("v2-g2",),
    "docs/production-observability.md": ("v2-g1",),
    "docs/release-policy.md": ("v2-g2",),
}
STALE_DOC_SNIPPETS = {
    "README.md": (
        "V2-C.3 当前分支",
    ),
    "README.en-US.md": (
        "current V2-C.3 branch",
    ),
    "docs/production-roadmap.md": (
        "V2-C.3 在当前 PR 分支",
        "状态：Partial（实现已完成，最终 CI/Chaos 验收中）",
        "当前 V2-D 分支已经完成",
    ),
    "docs/readiness.md": (
        "## V2-C.3 当前开发分支",
    ),
}


def load_capability_status() -> dict[str, str]:
    path = ROOT / "docs" / "capability-status.properties"
    if not path.is_file():
        fail("Missing docs/capability-status.properties")
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"Invalid capability status line: {raw}")
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def check_capability_status_consistency() -> None:
    expected = load_capability_status()
    for relative, required_keys in STATUS_DOCS.items():
        path = ROOT / relative
        if not path.is_file():
            fail(f"Missing status document: {relative}")
        text = path.read_text(encoding="utf-8")
        markers = dict(CAPABILITY_STATUS.findall(text))
        for key in required_keys:
            expected_value = expected.get(key)
            if expected_value is None:
                fail(f"Capability status source is missing key: {key}")
            actual_value = markers.get(key)
            if actual_value != expected_value:
                fail(
                    f"Capability status drift in {relative}: "
                    f"{key}={actual_value!r}, expected {expected_value!r}"
                )
        for snippet in STALE_DOC_SNIPPETS.get(relative, ()):
            if snippet in text:
                fail(
                    f"Stale capability wording in {relative}: {snippet}"
                )

def check_readme_parity() -> None:
    zh_path = ROOT / "README.md"
    en_path = ROOT / "README.en-US.md"
    if not zh_path.is_file() or not en_path.is_file():
        fail("README.md and README.en-US.md are both required")
    zh_sections = SECTION.findall(zh_path.read_text(encoding="utf-8"))
    en_sections = SECTION.findall(en_path.read_text(encoding="utf-8"))
    if zh_sections != en_sections:
        fail(f"README section markers differ: ZH={zh_sections}, EN={en_sections}")


def check_chinese_first_docs() -> None:
    for path in [ROOT / "CONTRIBUTING.md", *(ROOT / "docs").rglob("*.md")]:
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
    pom = ET.parse(ROOT / "pom.xml")
    root = pom.getroot()
    modules = tuple(node.text for node in root.findall("m:modules/m:module", NS))
    if modules != EXPECTED_MODULES:
        fail(f"Unexpected Maven modules: {modules}")
    for module in modules:
        module_dir = ROOT / module
        if not (module_dir / "pom.xml").is_file():
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
    check_readme_parity()
    check_capability_status_consistency()
    check_chinese_first_docs()
    check_markdown_links()
    check_maven_reactor()
    check_core_boundaries()
    check_java_hygiene()
    print("Peach RPC repository checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
