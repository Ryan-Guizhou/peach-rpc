#!/usr/bin/env python3
"""Positive and negative test fixtures for type-level Javadoc metadata rules."""

import unittest

import check_java_doc_metadata as lint


VALID = """/**
 * 提供 RPC 请求调度契约。
 *
 * @Author Mr Shu
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/10/10 09:30
 */
"""


class JavadocMetadataTest(unittest.TestCase):

    def test_javadoc_plugin_declares_all_custom_type_tags(self):
        pom = (lint.ROOT / "pom.xml").read_text(encoding="utf-8")
        for name in lint.REQUIRED:
            self.assertIn(f"<name>{name}</name>", pom)
        self.assertIn("<placement>t</placement>", pom)

    def findings(self, source):
        _, violations = lint.scan_source("otryx-core/src/main/java/Example.java", source)
        return [v["message"] for v in violations]

    def test_complete_type_metadata_is_accepted(self):
        source = VALID + '@Deprecated\npublic final class Example {}\n'
        self.assertEqual([], self.findings(source))

    def test_record_annotation_and_interface_are_supported(self):
        for declaration in (
            "public record Example(int count) {}",
            "public interface Example {}",
            "public enum Example { VALUE; }",
            "public @interface Example {}",
        ):
            with self.subTest(declaration=declaration):
                self.assertFalse(self.findings(VALID + declaration))

    def test_missing_tags_are_reported(self):
        messages = self.findings("/** 示例类型。 */\npublic class Example {}\n")
        for name in lint.REQUIRED:
            self.assertIn(f"@{name} must appear exactly once", messages)

    def test_no_type_doc_is_reported(self):
        self.assertIn("type Javadoc is missing",
                      self.findings("package com.example;\npublic class Example {}"))

    def test_placeholder_values_are_rejected(self):
        self.assertTrue(any("placeholder" in v for v in self.findings(
            VALID.replace("@Author Mr Shu", "@Author TODO") + "class Example {}"
        )))

    def test_invalid_calendar_date_is_rejected(self):
        self.assertTrue(any("real date" in v for v in self.findings(
            VALID.replace("2026/10/10 09:30", "2026/13/45 09:30") + "class Example {}"
        )))

    def test_invalid_date_format_is_rejected(self):
        self.assertTrue(any("yyyy/M/d" in v for v in self.findings(
            VALID.replace("2026/10/10 09:30", "2026-10-10") + "class Example {}"
        )))

    def test_duplicate_tag_is_rejected(self):
        source = VALID.replace(" * @Version", " * @Author Someone Else\n * @Version")
        self.assertTrue(any("@Author must appear exactly once" in v
                            for v in self.findings(source + "public class Example {}")))

    def test_version_format_is_checked(self):
        source = VALID.replace("1.0.0-SNAPSHOT", "future")
        self.assertTrue(any("@Version" in v for v in
                            self.findings(source + "public class Example {}")))

    def test_strings_comments_and_nested_types_are_not_top_level(self):
        source = (VALID + """
public class Example {
    String literal = "class Fake {}";
    /** 嵌套类型由主类型管理。 */
    static class Nested {}
}
""")
        count, findings = lint.scan_source("Example.java", source)
        self.assertEqual(1, count)
        self.assertEqual([], findings)

    def test_multiple_top_level_types_are_independently_checked(self):
        source = VALID + "class A {}\nclass B {}\n"
        count, findings = lint.scan_source("Example.java", source)
        self.assertEqual(2, count)
        self.assertEqual("B", findings[0]["type"])
        self.assertIn("missing", findings[0]["message"])

    def test_package_info_has_no_type(self):
        count, findings = lint.scan_source("package-info.java", "/** 包描述。 */\npackage x;")
        self.assertEqual((0, []), (count, findings))

    def test_newline_annotations_do_not_detach_type_javadoc(self):
        source = (VALID + """@Deprecated
@SuppressWarnings("unchecked")
public final class Example {}
""")
        self.assertEqual([], self.findings(source))

    def test_prior_javadoc_before_import_is_not_reused(self):
        source = VALID + "import java.util.List;\nclass Example {}\n"
        self.assertIn("type Javadoc is missing", self.findings(source))


if __name__ == "__main__":
    unittest.main()
