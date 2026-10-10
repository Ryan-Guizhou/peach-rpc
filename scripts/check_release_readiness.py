#!/usr/bin/env python3
"""Static RC1 / GA / patch release-readiness gate for OTRYX RPC."""

from __future__ import annotations

import argparse
import json
import re
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
    "docs/design/requirements-blueprint.md",
    "docs/design/technical-solution.md",
    "docs/features.md",
    "docs/design/detailed-design.md",
    "docs/design/project-structure.md",
    "docs/getting-started.md",
    "docs/faq.md",
    "docs/wire-compatibility.md",
    "docs/upgrade-rollback.md",
    "docs/configuration.md",
    "docs/reference/production-observability.md",
    "docs/archive/releases/release-policy.md",
    "docs/archive/releases/release-readiness.md",
    "docs/reference/capacity-planning.md",
    "docs/security.md",
    "docs/archive/releases/release-notes-1.0.0-RC1.md",
    "docs/archive/releases/release-notes-1.0.0.md",
    "docs/release-status.properties",
    "deploy/observability/grafana/otryx-dashboard.json",
    "deploy/observability/prometheus/otryx-alerts.example.yml",
    "otryx-core/src/main/java/io/peach/rpc/api/RpcTypeIds.java",
    "otryx-core/src/main/java/io/peach/rpc/api/RpcSchemaFingerprint.java",
    "otryx-core/src/main/java/io/peach/rpc/api/RpcCompatibilityMetadata.java",
    "otryx-core/src/main/java/io/peach/rpc/observability/RpcFailureClassifier.java",
    ".github/workflows/etcd-chaos.yml",
    ".github/workflows/nacos-chaos.yml",
    ".github/workflows/rolling-compatibility.yml",
    ".github/workflows/release.yml",
)

FIXED_VERSIONS = {
    "rc1": "1.0.0-RC1",
    "ga": "1.0.0",
}
PATCH_VERSION = re.compile(r"^1\.0\.([1-9][0-9]*)$")


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
    parser.add_argument(
        "--stage",
        choices=("rc1", "ga", "patch"),
        default="ga",
    )
    parser.add_argument("--version")
    args = parser.parse_args()

    if args.stage in FIXED_VERSIONS:
        version = args.version or FIXED_VERSIONS[args.stage]
        if version != FIXED_VERSIONS[args.stage]:
            fail(
                f"Stage {args.stage!r} requires version "
                f"{FIXED_VERSIONS[args.stage]!r}, got {version!r}"
            )
    else:
        version = args.version or ""
        if not PATCH_VERSION.fullmatch(version):
            fail(
                "Patch release requires a stable 1.0.x version greater "
                f"than 1.0.0, got {version!r}"
            )

    missing = [relative for relative in REQUIRED_FILES if not (ROOT / relative).is_file()]
    if missing:
        fail("Missing release-readiness assets: " + ", ".join(missing))

    status = properties(ROOT / "docs" / "release-status.properties")
    if status.get("release_candidate") != "1.0.0-RC1":
        fail("RC status version must be 1.0.0-RC1")
    if status.get("wire") != "v1":
        fail("Wire v1 must be frozen for 1.0.x")

    if args.stage == "rc1" and status.get("version") != "1.0.0":
        fail(
            "Historical RC1 readiness must be evaluated from the "
            "1.0.0 release source tree"
        )
    if args.stage == "ga" and status.get("version") != "1.0.0":
        fail(
            "Historical GA readiness must be evaluated from the "
            "1.0.0 release source tree"
        )
    if args.stage in ("ga", "patch") and status.get("project") != "ga":
        fail(f"{args.stage} release requires project=ga")
    if args.stage == "patch" and status.get("version") != version:
        fail(
            "Patch release-status version must match the release version: "
            f"status={status.get('version')!r}, release={version!r}"
        )

    pom = ET.parse(ROOT / "pom.xml").getroot()
    revision = pom.findtext("m:properties/m:revision", namespaces=NS)
    if args.stage == "ga" and revision != "1.0.0":
        fail(f"GA root Maven revision must be 1.0.0, got {revision!r}")
    if args.stage == "patch" and revision != version:
        fail(
            f"Patch root Maven revision must be {version!r}, "
            f"got {revision!r}"
        )

    dashboard = json.loads(
        (ROOT / "deploy/observability/grafana/otryx-dashboard.json").read_text(
            encoding="utf-8"
        )
    )
    if dashboard.get("uid") != "otryx-production":
        fail("Unexpected Grafana dashboard UID")
    if len(dashboard.get("panels", [])) < 6:
        fail("Production dashboard is incomplete")

    alerts = (
        ROOT / "deploy/observability/prometheus/otryx-alerts.example.yml"
    ).read_text(encoding="utf-8")
    for alert in (
        "OtryxRpcTlsHandshakeFailures",
        "OtryxRpcRegistryFailures",
        "OtryxRpcHeartbeatTimeouts",
        "OtryxRpcProviderAdmissionRejections",
        "OtryxRpcCircuitStuckOpen",
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
    if args.stage == "patch" and version not in changelog:
        fail(f"CHANGELOG must contain patch release {version}")

    if args.stage == "rc1":
        notes = ROOT / "docs/archive/releases/release-notes-1.0.0-RC1.md"
    elif args.stage == "ga":
        notes = ROOT / "docs/archive/releases/release-notes-1.0.0.md"
    else:
        notes = ROOT / f"docs/archive/releases/release-notes-{version}.md"
        if not notes.is_file():
            fail(
                "Missing patch release notes: "
                f"{notes.relative_to(ROOT)}"
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
        "OTRYX RPC release-readiness static checks passed: "
        f"stage={args.stage}, version={version}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
