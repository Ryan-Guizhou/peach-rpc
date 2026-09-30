#!/usr/bin/env python3
"""Static RC1 / GA release-readiness gate for Peach RPC."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

REQUIRED_FILES = (
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
    "docs/faq.md",
    "docs/wire-compatibility.md",
    "docs/upgrade-rollback.md",
    "docs/production-configuration.md",
    "docs/production-observability.md",
    "docs/release-policy.md",
    "docs/release-readiness.md",
    "docs/capacity-planning.md",
    "docs/security.md",
    "docs/release-notes-1.0.0-RC1.md",
    "docs/release-notes-1.0.0.md",
    "deploy/observability/grafana/peach-rpc-dashboard.json",
    "deploy/observability/prometheus/peach-rpc-alerts.example.yml",
    "peach-rpc-core/src/main/java/io/peach/rpc/api/RpcTypeIds.java",
    "peach-rpc-core/src/main/java/io/peach/rpc/api/RpcSchemaFingerprint.java",
    "peach-rpc-core/src/main/java/io/peach/rpc/api/RpcCompatibilityMetadata.java",
    "peach-rpc-core/src/main/java/io/peach/rpc/observability/RpcFailureClassifier.java",
    ".github/workflows/etcd-chaos.yml",
    ".github/workflows/nacos-chaos.yml",
    ".github/workflows/rolling-compatibility.yml",
    ".github/workflows/release.yml",
)

VERSIONS = {
    "rc1": "1.0.0-RC1",
    "ga": "1.0.0",
}


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    raise SystemExit(1)


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep:
            fail(f"Invalid properties line: {raw}")
        values[key.strip()] = value.strip()
    return values


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", choices=tuple(VERSIONS), default="ga")
    parser.add_argument("--version")
    args = parser.parse_args()
    version = args.version or VERSIONS[args.stage]
    if version != VERSIONS[args.stage]:
        fail(
            f"Stage {args.stage!r} requires version {VERSIONS[args.stage]!r}, "
            f"got {version!r}"
        )

    missing = [relative for relative in REQUIRED_FILES if not (ROOT / relative).is_file()]
    if missing:
        fail("Missing release-readiness assets: " + ", ".join(missing))

    status = properties(ROOT / "docs" / "capability-status.properties")
    if status.get("version") != "1.0.0":
        fail("GA status version must be 1.0.0")
    if status.get("release_candidate") != "1.0.0-RC1":
        fail("RC status version must be 1.0.0-RC1")
    if status.get("wire") != "v1":
        fail("Wire v1 must be frozen for 1.0.x")
    if args.stage == "ga" and status.get("project") != "ga":
        fail("GA release requires project=ga")

    pom = ET.parse(ROOT / "pom.xml").getroot()
    revision = pom.findtext("m:properties/m:revision", namespaces=NS)
    if args.stage == "ga" and revision != "1.0.0":
        fail(f"GA root Maven revision must be 1.0.0, got {revision!r}")

    dashboard = json.loads(
        (ROOT / "deploy/observability/grafana/peach-rpc-dashboard.json").read_text(
            encoding="utf-8"
        )
    )
    if dashboard.get("uid") != "peach-rpc-production":
        fail("Unexpected Grafana dashboard UID")
    if len(dashboard.get("panels", [])) < 6:
        fail("Production dashboard is incomplete")

    alerts = (
        ROOT / "deploy/observability/prometheus/peach-rpc-alerts.example.yml"
    ).read_text(encoding="utf-8")
    for alert in (
        "PeachRpcTlsHandshakeFailures",
        "PeachRpcRegistryFailures",
        "PeachRpcHeartbeatTimeouts",
        "PeachRpcProviderAdmissionRejections",
        "PeachRpcCircuitStuckOpen",
    ):
        if alert not in alerts:
            fail(f"Missing alert example: {alert}")

    compatibility = (ROOT / "docs/wire-compatibility.md").read_text(encoding="utf-8")
    for token in (
        "Wire v1",
        "Stable Type ID",
        "Schema Fingerprint",
        "N+1",
        "LEGACY",
        "INCOMPATIBLE",
    ):
        if token not in compatibility:
            fail(f"Compatibility documentation is incomplete: {token}")

    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    if "1.0.0-RC1" not in changelog or "1.0.0" not in changelog:
        fail("CHANGELOG must contain RC1 and GA entries")

    notes = ROOT / (
        "docs/release-notes-1.0.0-RC1.md"
        if args.stage == "rc1"
        else "docs/release-notes-1.0.0.md"
    )
    notes_text = notes.read_text(encoding="utf-8")
    if version not in notes_text:
        fail(f"Release notes do not mention {version}")
    if "TODO" in notes_text or "TBD" in notes_text:
        fail(f"Release notes still contain TODO/TBD: {notes.relative_to(ROOT)}")

    license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
    if "MIT License" not in license_text:
        fail("GA must retain the MIT License")

    print(
        "Peach RPC release-readiness static checks passed: "
        f"stage={args.stage}, version={version}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
