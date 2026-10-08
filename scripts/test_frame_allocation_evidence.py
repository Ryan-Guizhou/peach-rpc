#!/usr/bin/env python3
"""Self-tests for PR-E JMH allocation provenance and parser safety."""

import unittest

from compare_frame_allocation import compare, read_results


def jmh_row(mode, payload, fragmented, allocation=64.0):
    metric = {
        "benchmark": "io.peach.rpc.transport.vertx.FrameAccumulatorBenchmark.accumulate",
        "jmhVersion": "1.37",
        "jdkVersion": "21",
        "mode": mode,
        "params": {
            "payloadSize": str(payload),
            "fragmented": str(fragmented).lower(),
        },
        "primaryMetric": {
            "score": 120.0,
            "scoreUnit": "ns/op",
            "scorePercentiles": {"99.0": 300.0} if mode == "sample" else {},
        },
        "secondaryMetrics": {
            "gc.alloc.rate.norm": {
                "score": allocation,
                "scoreUnit": "B/op",
            }
        },
    }
    return metric


def scenario_matrix():
    baseline = {}
    current = {}
    for mode in ("avgt", "sample"):
        for payload in (64, 16384, 1048576):
            for fragmented in (False, True):
                key = (mode, str(payload), str(fragmented).lower())
                before = jmh_row(mode, payload, fragmented)
                after = jmh_row(mode, payload, fragmented, allocation=32.0)
                baseline[key] = {
                    "allocation_b_op": before["secondaryMetrics"]["gc.alloc.rate.norm"]["score"],
                    "score_ns_op": before["primaryMetric"]["score"],
                    "p99_ns_op": 300.0 if mode == "sample" else None,
                    "jdk_version": "21",
                    "jmh_version": "1.37",
                }
                current[key] = {
                    "allocation_b_op": after["secondaryMetrics"]["gc.alloc.rate.norm"]["score"],
                    "score_ns_op": after["primaryMetric"]["score"],
                    "p99_ns_op": 300.0 if mode == "sample" else None,
                    "jdk_version": "21",
                    "jmh_version": "1.37",
                }
    return baseline, current


def metadata():
    return {
        "baseline_sha": "aaa",
        "candidate_sha": "bbb",
        "evidence_class": "shared-ci-smoke",
        "java_version": "21",
        "runner_id": "github-shared",
    }


class ComparisonEvidenceTest(unittest.TestCase):
    def test_complete_matrix_creates_disclaimed_table(self):
        before, after = scenario_matrix()
        report = compare(before, after, metadata())
        self.assertIn("SHARED-RUNNER SMOKE ONLY", report)
        self.assertIn("B/op", report)
        self.assertIn("-50.00%", report)
        self.assertIn("sample", report)

    def test_rejects_mismatched_scenarios(self):
        before, after = scenario_matrix()
        after.pop(("sample", "64", "false"))
        with self.assertRaisesRegex(ValueError, "matrices differ"):
            compare(before, after, metadata())

    def test_rejects_unproven_controlled_claim(self):
        before, after = scenario_matrix()
        env = metadata()
        env["evidence_class"] = "controlled"
        with self.assertRaisesRegex(ValueError, "locked runner"):
            compare(before, after, env)

    def test_rejects_jdk_version_mismatch(self):
        before, after = scenario_matrix()
        after[("avgt", "64", "false")]["jdk_version"] = "22"
        with self.assertRaisesRegex(ValueError, "JDK mismatch"):
            compare(before, after, metadata())

    def test_rejects_missing_profiler_and_p99(self):
        import json
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.json"
            row = jmh_row("sample", 64, False)
            row["secondaryMetrics"] = {}
            path.write_text(json.dumps([row]), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "gc.alloc.rate.norm missing"):
                read_results(path)
            row = jmh_row("sample", 64, False)
            row["primaryMetric"]["scorePercentiles"] = {}
            path.write_text(json.dumps([row]), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Missing sample p99"):
                read_results(path)

    def test_rejects_negative_allocation(self):
        import json
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.json"
            path.write_text(
                json.dumps([jmh_row("avgt", 16384, False, -1.0)]),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "Invalid JMH allocation"):
                read_results(path)


if __name__ == "__main__":
    unittest.main()
