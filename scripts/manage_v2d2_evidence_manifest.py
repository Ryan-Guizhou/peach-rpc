#!/usr/bin/env python3
"""Create or verify an auditable SHA-256 manifest for one evidence bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

MANIFEST_NAME = "evidence-manifest.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def files(root: Path) -> list[dict[str, object]]:
    entries = []
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        relative = path.relative_to(root).as_posix()
        if relative == MANIFEST_NAME:
            continue
        entries.append(
            {
                "path": relative,
                "sizeBytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    return entries


def create(root: Path) -> int:
    environment_path = root / "environment.properties"
    if not environment_path.is_file():
        raise SystemExit("Evidence bundle is missing environment.properties")
    environment = properties(environment_path)
    manifest = {
        "schemaVersion": 1,
        "createdAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "runId": environment.get("run_id", "unknown"),
        "commit": environment.get("commit", "unknown"),
        "runnerId": environment.get("runner_id", "unknown"),
        "evidenceClass": environment.get("evidence_class", "unknown"),
        "fileCount": 0,
        "totalBytes": 0,
        "files": files(root),
    }
    manifest["fileCount"] = len(manifest["files"])
    manifest["totalBytes"] = sum(
        int(entry["sizeBytes"]) for entry in manifest["files"]
    )
    (root / MANIFEST_NAME).write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"Evidence manifest created: {root / MANIFEST_NAME}")
    return 0


def verify(root: Path) -> int:
    manifest_path = root / MANIFEST_NAME
    if not manifest_path.is_file():
        raise SystemExit(f"Evidence manifest is missing: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected = {
        entry["path"]: entry
        for entry in manifest.get("files", [])
    }
    actual_entries = files(root)
    actual = {entry["path"]: entry for entry in actual_entries}

    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    changed = sorted(
        path
        for path in set(expected) & set(actual)
        if expected[path].get("sha256") != actual[path].get("sha256")
        or int(expected[path].get("sizeBytes", -1))
        != int(actual[path].get("sizeBytes", -2))
    )

    if missing or extra or changed:
        messages = []
        if missing:
            messages.append("missing=" + ",".join(missing))
        if extra:
            messages.append("extra=" + ",".join(extra))
        if changed:
            messages.append("changed=" + ",".join(changed))
        raise SystemExit("Evidence manifest verification failed: " + "; ".join(messages))

    print("Evidence manifest verification passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("create", "verify"))
    parser.add_argument("--bundle", type=Path, required=True)
    args = parser.parse_args()
    if args.action == "create":
        return create(args.bundle)
    return verify(args.bundle)


if __name__ == "__main__":
    raise SystemExit(main())
