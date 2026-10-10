#!/usr/bin/env python3
"""Self-test V2-D.2-E2 and V2-D.4 performance gates."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(*args: str, success: bool = True) -> None:
    result = subprocess.run(
        [sys.executable, *args],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if success and result.returncode != 0:
        raise AssertionError(result.stdout)
    if not success and result.returncode == 0:
        raise AssertionError("Expected failure:\n" + result.stdout)


def write(path: Path, value: dict) -> None:
    path.write_text(
        json.dumps(value, indent=2) + "\n",
        encoding="utf-8",
    )


def candidate(
    commit: str,
    throughput: float,
    p99: float,
    sample_score: float,
    throughput_score: float,
) -> dict:
    return {
        "schemaVersion": 1,
        "status": "CANDIDATE",
        "commit": commit,
        "runnerId": "perf-01",
        "runCount": 3,
        "environment": {
            "host_fingerprint_sha256": "host-a",
        },
        "soakBaseline": {
            "throughputOpsPerSecond": throughput,
            "p99Micros": p99,
            "p999Micros": p99 * 1.5,
        },
        "matrixBaseline": [
            {
                "point": {
                    "family": "payload",
                    "benchmark": "echo",
                    "mode": "sample",
                    "payload_bytes": "256",
                    "connections": "1",
                    "threads": "1",
                },
                "scoreMedian": sample_score,
                "allocationMedianBytesPerOp": 128.0,
            },
            {
                "point": {
                    "family": "payload",
                    "benchmark": "echo",
                    "mode": "thrpt",
                    "payload_bytes": "256",
                    "connections": "1",
                    "threads": "1",
                },
                "scoreMedian": throughput_score,
                "allocationMedianBytesPerOp": 128.0,
            },
        ],
    }


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="otryx-e2-d4-") as raw:
        root = Path(raw)
        e1 = root / "e1.json"
        repeatability = root / "repeatability.json"
        baseline = root / "baseline.json"
        decisions = root / "decisions.json"

        write(e1, {"status": "PASS"})
        write(
            repeatability,
            {
                "status": "PASS",
                "runCount": 3,
                "errors": [],
                "thresholdFailures": [],
            },
        )
        write(
            baseline,
            candidate(
                "before",
                50_000.0,
                300.0,
                10.0,
                100_000.0,
            ),
        )
        write(
            decisions,
            {
                key: {
                    "decision": "NO_CHANGE",
                    "evidence": "Synthetic self-test evidence",
                }
                for key in (
                    "bufferOwnership",
                    "frameAccumulatorCopy",
                    "pendingRequestFuture",
                    "generatedPath",
                    "concurrencyHotspots",
                    "compression",
                )
            },
        )

        e2_out = root / "e2"
        run(
            "scripts/analyze_v2d2_e2.py",
            "--e1-handoff",
            str(e1),
            "--repeatability-report",
            str(repeatability),
            "--baseline-candidate",
            str(baseline),
            "--decisions",
            str(decisions),
            "--output-dir",
            str(e2_out),
        )
        e2 = json.loads(
            (e2_out / "e2-handoff.json").read_text(
                encoding="utf-8"
            )
        )
        if e2.get("status") != "PASS":
            raise AssertionError(e2)

        run(
            "scripts/analyze_v2d2_e2.py",
            "--e1-handoff",
            str(e1),
            "--repeatability-report",
            str(repeatability),
            "--baseline-candidate",
            str(baseline),
            "--output-dir",
            str(root / "e2-blocked"),
            success=False,
        )

        before = root / "before.json"
        after = root / "after.json"
        write(
            before,
            candidate(
                "before",
                50_000.0,
                300.0,
                10.0,
                100_000.0,
            ),
        )
        write(
            after,
            candidate(
                "after",
                51_000.0,
                295.0,
                9.8,
                102_000.0,
            ),
        )
        d4_out = root / "d4"
        run(
            "scripts/finalize_v2d4.py",
            "--before",
            str(before),
            "--after",
            str(after),
            "--max-latency-regression-percent",
            "5",
            "--max-allocation-regression-percent",
            "5",
            "--max-throughput-regression-percent",
            "5",
            "--output-dir",
            str(d4_out),
        )
        d4 = json.loads(
            (d4_out / "v2d4-closure.json").read_text(
                encoding="utf-8"
            )
        )
        if d4.get("status") != "PASS":
            raise AssertionError(d4)

        regressed = root / "regressed.json"
        write(
            regressed,
            candidate(
                "regressed",
                40_000.0,
                400.0,
                13.0,
                70_000.0,
            ),
        )
        run(
            "scripts/finalize_v2d4.py",
            "--before",
            str(before),
            "--after",
            str(regressed),
            "--max-latency-regression-percent",
            "5",
            "--max-allocation-regression-percent",
            "5",
            "--max-throughput-regression-percent",
            "5",
            "--output-dir",
            str(root / "d4-fail"),
            success=False,
        )

    print("V2-D.2-E2 / V2-D.4 tooling self-test passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
