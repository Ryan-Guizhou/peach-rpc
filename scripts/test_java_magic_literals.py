#!/usr/bin/env python3
"""Positive and negative fixtures for OTRYX magic-literal audit."""

import unittest

import check_java_magic_literals as lint


SOURCE = "otryx-core/src/main/java/com/peachsoft/otryx/Demo.java"
TEST = "otryx-core/src/test/java/com/peachsoft/otryx/DemoTest.java"


class MagicLiteralTest(unittest.TestCase):
    def rules(self, code: str, path: str = SOURCE) -> set[str]:
        return {f["rule"] for f in lint.scan_source(path, code)}

    def test_rejects_literal_spi_registry_and_protocol_keys(self):
        source = (
            '@Extension("memory")\n'
            'var result = opts.providerOption("leaseTtlSeconds", "30");\n'
            'var values = Map.of("instance", Map.of("status", "UP"));\n'
            'var other = Map.entry("nacosGroup", group);\n'
            'var json = body.path("application");\n'
        )
        self.assertEqual(
            {"ML001", "ML002", "ML003", "ML004", "ML005"},
            self.rules(source),
        )

    def test_rejects_metric_env_and_header_keys(self):
        text = (
            'Gauge.builder(name).tag("state", "OPEN");\n'
            'System.getProperty("java.version");\n'
            'System.getenv("OTRYX_RPC_PORT");\n'
            'request.header("Authorization", token);\n'
        )
        self.assertEqual({"ML006", "ML007", "ML008"}, self.rules(text))

    def test_named_constants_are_accepted(self):
        text = (
            '@Extension(Protocol.TYPE)\n'
            'var a = opts.providerOption(Keys.LEASE_SECONDS, "30");\n'
            'Map.entry(Keys.INSTANCE, value);\n'
            'Map.of(Keys.STATUS, "UP");\n'
            'var b = json.path(Keys.APPLICATION);\n'
            'Gauge.builder(name).tag(MetricTags.STATUS, "UP");\n'
            'System.getProperty(SystemKeys.JAVA_VERSION);\n'
            'request.header(HeaderNames.AUTHORIZATION, token);\n'
        )
        self.assertEqual(set(), self.rules(text))

    def test_comments_and_test_sources_are_excluded(self):
        source = (
            '// @Extension("memory")\n'
            '/* Map.entry("nacosGroup", group); */\n'
            'String example = "providerOption(\\\"nacosGroup\\\", value)";\n'
        )
        self.assertEqual(set(), self.rules(source))
        self.assertEqual(set(), self.rules(
            '@Extension("memory")', TEST))

    def test_line_numbers_preserved(self):
        self.assertEqual(
            [2], [found["line"] for found in lint.scan_source(
                SOURCE, 'class Demo {\n Map.of("field", value);\n}')])

    def test_non_semantic_literal_is_permitted(self):
        text = ('throw new IllegalArgumentException("Invalid RPC state");\n'
                'LOGGER.info("RPC endpoint started");\n'
                'String separator = "/";\n')
        self.assertEqual(set(), self.rules(text))


if __name__ == "__main__":
    unittest.main()
