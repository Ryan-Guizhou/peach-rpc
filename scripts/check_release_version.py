#!/usr/bin/env python3
"""Validate Peach RPC release channel and semantic version."""

from __future__ import annotations

import argparse
import sys

EXPECTED = {
    "rc1": "1.0.0-RC1",
    "ga": "1.0.0",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", choices=tuple(EXPECTED), required=True)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()

    expected = EXPECTED[args.stage]
    if args.version != expected:
        print(
            f"ERROR: stage {args.stage!r} requires version {expected!r}, "
            f"got {args.version!r}"
        )
        return 1

    print(f"Peach RPC release version accepted: stage={args.stage}, version={args.version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
