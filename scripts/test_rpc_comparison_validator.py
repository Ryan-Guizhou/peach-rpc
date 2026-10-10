#!/usr/bin/env python3
"""Self-tests for the isolated RPC comparison evidence quality gate."""

import unittest

from validate_rpc_comparison import markdown, validate_pair, validate_result


def sample(framework="otryx"):
    return {
        "schema": "otryx.rpc.comparison.v1",
        "recorded_at": "2026-01-01T00:00:00Z",
        "evidence_class": "smoke",
        "run_id": "test-01",
        "git_sha": "0123456789abcdef",
        "framework": framework,
        "protocol": "tcp-v1" if framework == "otryx" else "dubbo-tcp",
        "serializer": "fory-native" if framework == "otryx" else "hessian2",
        "workload": "sync-byte-array-echo-closed-loop",
        "provider_host": "127.0.0.1",
        "provider_port": 19001 if framework == "otryx" else 19002,
        "concurrency": 16,
        "payload_bytes": 256,
        "warmup_seconds": 2,
        "measurement_seconds": 3,
        "attempts": 100,
        "success": 100,
        "errors": 0,
        "error_rate": 0.0,
        "throughput_qps": 33.33333,
        "qps_per_cpu_core": None,
        "qps_per_client_cpu_core": 50.0,
        "client_process_cpu_cores": 0.66,
        "client_gc_count_delta": 1,
        "client_gc_millis_delta": 2,
        "client_heap_start_bytes": 100000,
        "client_heap_end_bytes": 110000,
        "p50_us": 50.0,
        "p99_us": 100.0,
        "p999_us": 120.0,
        "latency_samples": 100,
        "allocation_bytes_per_op": None,
        "server_cpu_cores": None,
        "server_gc_millis_delta": None,
        "connection_count": None,
        "java_version": "21",
        "os_name": "Linux",
        "os_arch": "amd64",
        "errors_by_type": {},
    }


def environment():
    return {
        "schema": "otryx.rpc.comparison.environment.v1",
        "evidence_class": "smoke",
        "run_id": "test-01",
        "git_sha": "0123456789abcdef",
        "runner_id": "ci-smoke",
        "physical_host_fingerprint": None,
    }


class ComparisonValidatorTest(unittest.TestCase):
    def test_accepts_complete_smoke_without_invented_allocations(self):
        otryx, dubbo = sample(), sample("dubbo")
        validate_pair(otryx, dubbo, environment())
        report = markdown(otryx, dubbo, environment())
        self.assertIn("NOT OFFICIAL PERFORMANCE EVIDENCE", report)
        self.assertIn("not measured", report)

    def test_rejects_inconsistent_scenarios(self):
        otryx, dubbo = sample(), sample("dubbo")
        dubbo["concurrency"] = 32
        with self.assertRaisesRegex(ValueError, "Scenario mismatch"):
            validate_pair(otryx, dubbo, environment())

    def test_rejects_hidden_allocation_omission(self):
        data = sample()
        data.pop("allocation_bytes_per_op")
        with self.assertRaisesRegex(ValueError, "unmeasured-metric"):
            validate_result(data, "otryx")

    def test_rejects_error_breakdown_drift(self):
        data = sample()
        data["attempts"] = 101
        data["errors"] = 1
        data["error_rate"] = 1 / 101
        with self.assertRaisesRegex(ValueError, "error breakdown"):
            validate_result(data, "otryx")

    def test_rejects_unproven_controlled_claim(self):
        otryx, dubbo, env = sample(), sample("dubbo"), environment()
        otryx["evidence_class"] = dubbo["evidence_class"] = "controlled"
        env["evidence_class"] = "controlled"
        env["physical_host_fingerprint"] = "0123"
        with self.assertRaisesRegex(ValueError, "single controlled pair"):
            validate_pair(otryx, dubbo, env)


if __name__ == "__main__":
    unittest.main()
