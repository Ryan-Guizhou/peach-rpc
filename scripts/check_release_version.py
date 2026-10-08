#!/usr/bin/env python3
"""Validate Peach RPC release channel and semantic version."""

from __future__ import annotations

import argparse
import re
import sys

FIXED_VERSIONS = {
    "rc1": "1.0.0-RC1",
    "ga": "1.0.0",
}
PATCH_VERSION = re.compile(r"^1\.0\.([1-9][0-9]*)$")


def validate(stage: str, version: str) -> str | None:
    """Return an error message when the release version is invalid."""
    if stage in FIXED_VERSIONS:
        expected = FIXED_VERSIONS[stage]
        if version != expected:
            return (
                f"stage {stage!r} requires version {expected!r}, "
                f"got {version!r}"
            )
        return None

    if stage == "patch" and not PATCH_VERSION.fullmatch(version):
        return (
            "stage 'patch' requires a stable 1.0.x version greater than "
            f"1.0.0, got {version!r}"
        )
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--stage",
        choices=("rc1", "ga", "patch"),
        required=True,
    )
    parser.add_argument("--version", required=True)
    args = parser.parse_args()

    error = validate(args.stage, args.version)
    if error:
        print(f"ERROR: {error}")
        return 1

    print(
        "Peach RPC release version accepted: "
        f"stage={args.stage}, version={args.version}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
