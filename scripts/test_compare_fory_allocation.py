#!/usr/bin/env python3
"""Reject forged, incomplete and non-comparable Fory allocation evidence."""

import json
import tempfile
import unittest
from pathlib import Path

from compare_fory_allocation import (
    METHODS, compare, load_comparison, markdown,
)


def row(method, mode, allocation=48.0):
    return {
        "benchmark": "com.peachsoft.otryx.benchmarks.ForyArgumentEncodingBenchmark." + method,
        "jdkVersion": "21",
        "jmhVersion": "1.37",
        "mode": mode,
        "primaryMetric": {
            "score": 180.0,
            "scoreUnit": "ns/op",
            "scorePercentiles": {"99.0": 410.0} if mode == "sample" else {},
        },
        "secondaryMetrics": {
            "gc.alloc.rate.norm": {"score": allocation, "scoreUnit": "B/op"},
        },
    }


def evidence(root, runs=3):
    for side, allocation in (("baseline", 48.0), ("candidate", 32.0)):
        for index in range(1, runs + 1):
            folder = root / side / f"run-{index:02d}"
            folder.mkdir(parents=True)
            for mode in ("avgt", "sample"):
                (folder / f"{mode}.json").write_text(json.dumps(
                    [row(method, mode, allocation) for method in sorted(METHODS)]
                ), encoding="utf-8")


def metadata(controlled=False):
    return {
        "baseline_sha": "a" * 40,
        "candidate_sha": "b" * 40,
        "evidence_class": "controlled-micro" if controlled else "shared-ci-smoke",
        "runner_id": "fixed-runner",
        "physical_host_fingerprint": "host-fingerprint",
    }


class ForyAllocationEvidenceTest(unittest.TestCase):
    def test_three_pair_comparison_is_report_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            report = compare(
                load_comparison(root / "baseline"),
                load_comparison(root / "candidate"),
                metadata(controlled=True),
            )
            self.assertEqual(report["status"], "REPORT_ONLY")
            self.assertEqual(report["run_count"], 3)
            self.assertEqual(len(report["methods"]), 8)
            self.assertIn("not RPC p99", markdown(report))

    def test_controlled_single_pair_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root, runs=1)
            with self.assertRaisesRegex(ValueError, ">=3"):
                compare(
                    load_comparison(root / "baseline"),
                    load_comparison(root / "candidate"),
                    metadata(controlled=True),
                )

    def test_mismatched_run_sets_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            (root / "candidate" / "run-03" / "sample.json").unlink()
            with self.assertRaises((ValueError, OSError)):
                compare(
                    load_comparison(root / "baseline"),
                    load_comparison(root / "candidate"),
                    metadata(),
                )

    def test_missing_allocation_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            path = root / "baseline" / "run-01" / "avgt.json"
            values = json.loads(path.read_text(encoding="utf-8"))
            values[0]["secondaryMetrics"].clear()
            path.write_text(json.dumps(values), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "gc.alloc.rate.norm"):
                load_comparison(root / "baseline")

    def test_invalid_source_sha_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            env = metadata()
            env["baseline_sha"] = "not-a-sha"
            with self.assertRaisesRegex(ValueError, "40-character"):
                compare(
                    load_comparison(root / "baseline"),
                    load_comparison(root / "candidate"),
                    env,
                )

    def test_differing_jdk_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence(root)
            path = root / "candidate" / "run-02" / "avgt.json"
            rows = json.loads(path.read_text(encoding="utf-8"))
            rows[0]["jdkVersion"] = "22"
            path.write_text(json.dumps(rows), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "JDK/JMH"):
                compare(
                    load_comparison(root / "baseline"),
                    load_comparison(root / "candidate"),
                    metadata(),
                )


if __name__ == "__main__":
    unittest.main()
