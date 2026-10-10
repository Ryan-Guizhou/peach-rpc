#!/usr/bin/env python3
"""Enforce the OTRYX 2.0 breaking-namespace migration contract."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
EXPECTED_KEYS = (
    "peach.rpc.protocol.version",
    "peach.rpc.schema.version",
    "peach.rpc.schema.fingerprint",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def main() -> int:
    pom = ET.parse(ROOT / "pom.xml").getroot()
    require(pom.findtext("m:groupId", namespaces=NS) == "com.peachsoft.otryx",
            "Incorrect OTRYX groupId")
    require(pom.findtext("m:artifactId", namespaces=NS) == "otryx-parent",
            "Incorrect OTRYX parent artifactId")
    require(pom.findtext("m:properties/m:revision", namespaces=NS) == "2.0.0-SNAPSHOT",
            "Incorrect development version")
    status = (ROOT / "docs/release-status.properties").read_text(encoding="utf-8")
    require("project=migration" in status and "version=2.0.0-SNAPSHOT" in status,
            "Development source status must explicitly be migration")
    require(not list(ROOT.glob("peach-rpc-*")), "Legacy module directories remain")

    for module in ROOT.glob("otryx-*"):
        for path in module.rglob("*"):
            if not path.is_file() or "target" in path.parts:
                continue
            if "/src/" not in ("/" + path.as_posix().split(module.name, 1)[-1]):
                continue
            if path.suffix not in (".java", ".xml", ".properties", ".imports", ".yml", ".yaml", ".json", ".Processor"):
                continue
            value = path.read_text(encoding="utf-8")
            require("io.peach.rpc" not in value,
                    "Legacy Java namespace remains in " + str(path.relative_to(ROOT)))
            if path.suffix == ".java":
                require("/io/peach/rpc/" not in path.as_posix(),
                        "Legacy Java source path remains: " + str(path.relative_to(ROOT)))

    compat = (ROOT / "otryx-core/src/main/java/com/peachsoft/otryx/api/"
              "RpcCompatibilityMetadata.java").read_text(encoding="utf-8")
    for token in EXPECTED_KEYS:
        require('"' + token + '"' in compat,
                "Frozen registry key changed: " + token)
    props = (ROOT / "otryx-spring-boot-autoconfigure/src/main/java/com/peachsoft/otryx/"
             "spring/autoconfigure/OtryxRpcProperties.java").read_text(encoding="utf-8")
    require('@ConfigurationProperties(prefix = "otryx.rpc")' in props,
            "Spring Boot config prefix did not migrate")
    processor = (ROOT / "otryx-codegen/src/main/java/com/peachsoft/otryx/codegen/"
                 "OtryxRpcContractProcessor.java").read_text(encoding="utf-8")
    require('@SupportedAnnotationTypes("com.peachsoft.otryx.api.OtryxRpcContract")' in processor,
            "Processor must discover new contract annotation")

    for relative in ("docs/images/brand/otryx-banner.svg",
                     "docs/images/mascot/otti-main.svg",
                     "docs/images/architecture/system-overview.svg",
                     "docs/images/architecture/control-data-plane.svg",
                     "docs/images/flows/rpc-lifecycle.svg"):
        img = ROOT / relative
        require(img.is_file(), "Missing image: " + relative)
        require(ET.parse(img).getroot().tag.endswith("svg"),
                "Invalid SVG image: " + relative)

    for relative in ("docs/brand-guidelines.md", "docs/migration.md"):
        require((ROOT / relative).is_file(), "Missing migration doc: " + relative)
    require((ROOT / "docs/archive/releases/release-notes-1.0.1.md").is_file(),
            "Historical release notes must survive renaming")
    print("OTRYX 2.0 migration readiness passed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as exc:
        print("ERROR:", exc)
        sys.exit(1)
