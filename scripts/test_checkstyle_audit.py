#!/usr/bin/env python3
"""Checkstyle audit schema and failure-path regression tests."""

import re
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

from summarize_checkstyle_audit import markdown, summarize


class CheckstyleAuditTest(unittest.TestCase):

    def test_collects_source_line_and_checker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "checkstyle-result.xml"
            source = root / "peach-rpc-core/src/main/java/Demo.java"
            report.write_text(
                '<checkstyle version="10.0">'
                f'<file name="{source}">'
                '<error line="12" column="3" severity="warning" '
                'message="Missing documentation" '
                'source="com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocTypeCheck"/>'
                "</file></checkstyle>",
                encoding="utf-8")
            result = summarize([report], root)
            self.assertEqual(1, result["reports"])
            self.assertEqual(1, result["source_files"])
            self.assertEqual(1, result["violations"])
            self.assertEqual(12, result["findings"][0]["line"])
            self.assertEqual(
                "peach-rpc-core/src/main/java/Demo.java",
                result["findings"][0]["file"])
            self.assertIn(
                "MissingJavadocTypeCheck",
                markdown(result))

    def test_report_is_explicitly_advisory(self):
        report = {
            "reports": 1,
            "source_files": 4,
            "violations": 0,
            "by_check": {},
        }
        self.assertIn("advisory only", markdown(report))
        self.assertIn("not yet a blocking gate", markdown(report))

    def test_production_javadocs_are_audited_without_test_fixture_noise(self):
        config = (Path(__file__).resolve().parents[1] /
                  "config/peach-rpc-checkstyle.xml")
        root = ET.parse(config).getroot()
        modules = list(root.iter("module"))
        self.assertIn("MissingJavadocType",
                      [m.get("name") for m in modules])
        self.assertIn("MissingJavadocMethod",
                      [m.get("name") for m in modules])
        audited_checks = {
            "FileTabCharacter", "LineLength", "AvoidStarImport",
            "TypeName", "MethodName", "MemberName", "ParameterName",
            "ConstantName", "MissingJavadocType",
            "MissingJavadocMethod",
        }
        for check in modules:
            if check.get("name") not in audited_checks:
                continue
            settings = {p.get("name"): p.get("value")
                        for p in check.findall("property")}
            self.assertEqual(
                "warning", settings.get("severity"),
                f"Advisory check {check.get('name')} must not emit errors")
        filters = [m for m in modules
                   if m.get("name") == "SuppressionSingleFilter"]
        self.assertEqual(1, len(filters))
        properties = {p.get("name"): p.get("value")
                      for p in filters[0].findall("property")}
        self.assertIn("MissingJavadoc", properties["checks"])
        self.assertTrue(re.search(
            properties["files"],
            "/workspace/peach-rpc-core/src/test/java/Fixture.java"))
        self.assertFalse(re.search(
            properties["files"],
            "/workspace/peach-rpc-core/src/main/java/PublicApi.java"))

    def test_missing_report_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "No Checkstyle"):
            summarize([], Path("."))

    def test_invalid_xml_root_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "invalid.xml"
            file.write_text("<unrelated/>", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Not a Checkstyle"):
                summarize([file], root)

    def test_missing_filename_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "missing.xml"
            file.write_text(
                '<checkstyle version="10"><file><error line="2"/></file></checkstyle>',
                encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Unnamed source"):
                summarize([file], root)


if __name__ == "__main__":
    unittest.main()
