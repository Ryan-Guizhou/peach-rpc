#!/usr/bin/env python3
"""Positive and negative fixtures for OTRYX RPC scoped Java lint."""

import unittest

import check_java_conventions as lint


class JavaConventionsTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.rules = lint.load_rules()
        cls.runtime = (
            "otryx-core/src/main/java/io/peach/rpc/core/Demo.java")
        cls.transport = (
            "otryx-transport-vertx/src/main/java/io/peach/rpc/transport/vertx/Demo.java")
        cls.test_path = (
            "otryx-transport-vertx/src/test/java/io/peach/rpc/transport/vertx/DemoTest.java")

    def findings(self, path, source):
        return {f["rule"] for f in lint.scan_source(
            path, source, self.rules)}

    def test_rejects_system_exit_and_process_execution(self):
        ids = self.findings(
            self.runtime,
            "void run() { System.exit(1); new ProcessBuilder(\"sh\"); }")
        self.assertIn("J001", ids)
        self.assertIn("J002", ids)

    def test_rejects_print_stack_trace_and_system_stdout(self):
        ids = self.findings(
            self.runtime, "void run(Exception e) { e.printStackTrace(); System.out.println(e); }")
        self.assertIn("J003", ids)
        self.assertIn("J004", ids)

    def test_only_transport_runtime_blocks_sleep(self):
        body = "void run() { Thread.sleep(100); }"
        self.assertIn("T001", self.findings(self.transport, body))
        self.assertNotIn("T001", self.findings(self.runtime, body))
        self.assertNotIn("T001", self.findings(self.test_path, body))

    def test_comments_strings_and_text_blocks_are_not_false_positives(self):
        source = (
            'String example = "System.exit(0)";\n'
            '// System.out.println("message");\n'
            '/* e.printStackTrace(); Thread.sleep(30); */\n'
            'String documentation = """\n'
            'Runtime.getRuntime().exec("unsafe");\n'
            '""";\n'
        )
        self.assertFalse(self.findings(self.transport, source))

    def test_rejects_multiline_logger_concat_or_chinese(self):
        code = (
            'LOGGER.warn(\n    "Request failed. key=" + key);\n'
            'logger.error(\n    "请求失败, key={}", key);\n'
        )
        found = self.findings(self.runtime, code)
        self.assertIn("L001", found)
        self.assertIn("L002", found)

    def test_allows_english_parameterized_log(self):
        code = (
            'LOGGER.warn("Task rejected. methodId={}, reason={}", '
            'methodId, reason);\n'
        )
        self.assertFalse(self.findings(self.runtime, code))

    def test_allows_compile_time_constant_log_segments(self):
        code = (
            'LOGGER.warn("Etcd lease expired; "'
            ' + "recovering: leaseId={}", leaseId);'
        )
        self.assertFalse(self.findings(self.runtime, code))

    def test_cli_harness_is_not_treated_as_runtime(self):
        cli = "benchmarks/rpc-comparison/common/src/main/java/ComparisonHarness.java"
        self.assertFalse(self.findings(
            cli, 'System.out.println("Benchmark completed");'))

    def test_warns_not_blocks_review_of_catch_throwable(self):
        out = lint.scan_source(
            self.runtime,
            "try {run();} catch (Throwable failure) { record(failure); }",
            self.rules)
        self.assertEqual(["R001"], [x["rule"] for x in out])
        self.assertEqual("warning", out[0]["severity"])

    def test_rejects_core_adapter_imports_only_inside_core(self):
        code = 'import io.vertx.core.Vertx;\nimport org.springframework.context.ApplicationContext;\n'
        results = self.findings(self.runtime, code)
        self.assertIn("B001", results)
        self.assertNotIn("B001", self.findings(self.transport, code))
        self.assertNotIn("B001", self.findings(self.test_path, code))
        self.assertNotIn(
            "B001",
            self.findings(self.runtime, "import com.peachsoft.otryx.registry.ServiceDiscovery;"))

    def test_rejects_unbounded_queues_and_cached_executors(self):
        code = (
            "new LinkedBlockingQueue<>();\n"
            "Executors.newCachedThreadPool();\n"
        )
        result = self.findings(self.runtime, code)
        self.assertIn("R002", result)
        self.assertIn("R003", result)
        self.assertNotIn(
            "R002", self.findings(self.runtime,
                                  "new LinkedBlockingQueue<>(1024);"))

    def test_rejects_eager_formatting_in_logs(self):
        bad = 'LOGGER.warn(String.format("Request %s failed", requestId));'
        good = 'LOGGER.warn("Request {} failed", requestId);'
        self.assertIn("L003", self.findings(self.runtime, bad))
        self.assertNotIn("L003", self.findings(self.runtime, good))

    def test_sensitive_logging_is_review_only(self):
        result = lint.scan_source(
            self.runtime,
            'LOGGER.info("User token logged", token);',
            self.rules,
        )
        self.assertIn("L004", [x["rule"] for x in result])
        self.assertTrue(all(x["severity"] == "warning" for x in result))
        self.assertNotIn(
            "L004",
            self.findings(self.runtime,
                          'LOGGER.info("Connection established. id={}", id);'))

    def test_interface_prefix_is_reviewed_not_blocked(self):
        result = lint.scan_source(
            self.runtime, "public interface IConnectionFactory {}",
            self.rules)
        self.assertEqual(["N001"], [x["rule"] for x in result])
        self.assertEqual("warning", result[0]["severity"])

    def test_line_number_is_stable(self):
        out = lint.scan_source(
            self.runtime,
            "class Demo {\n void run() {\n System.exit(1);\n }\n}",
            self.rules)
        self.assertEqual(3, out[0]["line"])


if __name__ == "__main__":
    unittest.main()
