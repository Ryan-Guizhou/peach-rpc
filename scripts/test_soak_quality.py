#!/usr/bin/env python3
"""Regression tests for controlled soak policy and actual concurrency evidence."""

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from check_soak_quality import check_soak


def valid_soak():
    return {
        "concurrency": 10_000,
        "payloadBytes": 256,
        "connectionsPerEndpoint": 4,
        "durationSeconds": 1805.0,
        "throughputOpsPerSecond": 2000.0,
        "successes": 9900,
        "errors": 100,
        "errorRate": 0.01,
        "maxLogicalInflight": 9000,
        "p50Micros": 110.0,
        "p99Micros": 410.0,
        "p999Micros": 850.0,
        "gcCountDelta": 10,
        "gcTimeMillisDelta": 120,
        "processCpuCoresAverage": 2.1,
        "errorsByType": {"RpcTimeoutException": 100},
    }


class SoakQualityTest(unittest.TestCase):
    def test_controlled_with_explicit_policy_passes(self):
        result = check_soak(
            valid_soak(),
            "controlled",
            max_error_rate=0.02,
            min_observed_inflight=8000,
        )
        self.assertEqual(result["status"], "POLICY_PASS")

    def test_one_success_with_many_errors_fails_policy(self):
        data = valid_soak()
        data.update(successes=1, errors=9999, errorRate=0.9999)
        data["errorsByType"] = {"RpcTimeoutException": 9999}
        result = check_soak(
            data,
            "controlled",
            max_error_rate=0.01,
            min_observed_inflight=8000,
        )
        self.assertIn("exceeds maxErrorRate", " ".join(result["validationErrors"]))

    def test_configured_concurrency_is_not_observed_concurrency(self):
        data = valid_soak()
        data["maxLogicalInflight"] = 9
        result = check_soak(
            data,
            "controlled",
            max_error_rate=0.02,
            min_observed_inflight=8000,
        )
        self.assertEqual(result["status"], "FAIL")
        self.assertIn("Observed inflight", " ".join(result["validationErrors"]))

    def test_controlled_without_policy_fails_closed(self):
        result = check_soak(valid_soak(), "controlled")
        self.assertEqual(result["status"], "FAIL")
        self.assertIn("requires maxErrorRate", " ".join(result["validationErrors"]))

    def test_smoke_is_never_policy_pass(self):
        result = check_soak(valid_soak(), "smoke")
        self.assertEqual(result["status"], "REPORT_ONLY")

    def test_rate_disagreement_and_bad_breakdown_fail(self):
        data = valid_soak()
        data["errorRate"] = 0.0001
        data["errorsByType"] = {"RpcTimeoutException": 50}
        result = check_soak(data, "smoke")
        self.assertEqual(result["status"], "FAIL")
        self.assertIn("disagrees", " ".join(result["validationErrors"]))

    def test_invalid_percentiles_and_unrealistic_inflight_fail(self):
        data = valid_soak()
        data["maxLogicalInflight"] = 11_000
        data["p99Micros"] = 900.0
        data["p999Micros"] = 400.0
        result = check_soak(data, "smoke")
        self.assertEqual(result["status"], "FAIL")
        self.assertIn("Latency percentiles", " ".join(result["validationErrors"]))

    def test_p99_policy_rejects_excessive_latency(self):
        result = check_soak(
            valid_soak(), "controlled",
            max_error_rate=0.02,
            min_observed_inflight=8000,
            max_p99_micros=300.0,
        )
        self.assertEqual(result["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
