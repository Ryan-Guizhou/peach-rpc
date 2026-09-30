#!/usr/bin/env python3
"""Static engineering gate for Peach RPC release readiness assets."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = (
    "docs/wire-compatibility.md",
    "docs/upgrade-rollback.md",
    "docs/production-configuration.md",
    "docs/production-observability.md",
    "docs/release-policy.md",
    "docs/capacity-planning.md",
    "docs/security.md",
    "deploy/observability/grafana/peach-rpc-dashboard.json",
    "deploy/observability/prometheus/peach-rpc-alerts.example.yml",
    "peach-rpc-core/src/main/java/io/peach/rpc/api/RpcTypeIds.java",
    "peach-rpc-core/src/main/java/io/peach/rpc/api/RpcSchemaFingerprint.java",
    "peach-rpc-core/src/main/java/io/peach/rpc/api/RpcCompatibilityMetadata.java",
    "peach-rpc-core/src/main/java/io/peach/rpc/observability/RpcFailureClassifier.java",
    ".github/workflows/etcd-chaos.yml",
    ".github/workflows/nacos-chaos.yml",
)

REQUIRED_STATUS_KEYS = (
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
)


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    raise SystemExit(1)


def properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"Invalid properties line: {raw}")
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def main() -> int:
    missing = [
        relative
        for relative in REQUIRED_FILES
        if not (ROOT / relative).is_file()
    ]
    if missing:
        fail("Missing release-readiness assets: " + ", ".join(missing))

    status = properties(
        ROOT / "docs" / "capability-status.properties"
    )
    missing_status = [
        key
        for key in REQUIRED_STATUS_KEYS
        if not status.get(key)
    ]
    if missing_status:
        fail(
            "Missing capability status keys: "
            + ", ".join(missing_status)
        )
    if status.get("project") != "preview":
        fail(
            "Engineering readiness must not silently promote "
            "the project beyond Preview"
        )

    dashboard = json.loads(
        (
            ROOT
            / "deploy"
            / "observability"
            / "grafana"
            / "peach-rpc-dashboard.json"
        ).read_text(encoding="utf-8")
    )
    if dashboard.get("uid") != "peach-rpc-production":
        fail("Unexpected Grafana dashboard UID")
    if len(dashboard.get("panels", [])) < 6:
        fail("Production dashboard is incomplete")

    alerts = (
        ROOT
        / "deploy"
        / "observability"
        / "prometheus"
        / "peach-rpc-alerts.example.yml"
    ).read_text(encoding="utf-8")
    for alert in (
        "PeachRpcTlsHandshakeFailures",
        "PeachRpcRegistryFailures",
        "PeachRpcHeartbeatTimeouts",
        "PeachRpcProviderAdmissionRejections",
    ):
        if alert not in alerts:
            fail(f"Missing alert example: {alert}")

    compatibility = (
        ROOT / "docs" / "wire-compatibility.md"
    ).read_text(encoding="utf-8")
    for token in (
        "Stable Type ID",
        "Schema Fingerprint",
        "N+1",
        "LEGACY",
        "INCOMPATIBLE",
    ):
        if token not in compatibility:
            fail(
                "Compatibility documentation is incomplete: "
                + token
            )

    release = (
        ROOT / "docs" / "release-policy.md"
    ).read_text(encoding="utf-8")
    if "1.0.0-RC1" not in release:
        fail("Release policy does not define the RC boundary")
    if "Preview" not in release:
        fail("Release policy does not preserve Preview state")

    print("Peach RPC release-readiness static checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
