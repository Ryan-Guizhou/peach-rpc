#!/usr/bin/env python3
"""Self-tests for OTRYX repository identity checks."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from check_repository_identity import URL, validate


def fixture(root: Path) -> None:
    (root / "docs").mkdir()
    (root / "pom.xml").write_text(
        '<?xml version="1.0"?><project xmlns="http://maven.apache.org/POM/4.0.0">'
        '<groupId>com.peachsoft.otryx</groupId><url>' + URL + '</url>'
        '<scm><url>' + URL + '</url>'
        '<connection>scm:git:' + URL + '.git</connection>'
        '<developerConnection>scm:git:ssh://git@github.com/'
        'Ryan-Guizhou/otryx-rpc.git</developerConnection></scm></project>',
        encoding="utf-8")
    (root / "docs/getting-started.md").write_text(
        'git clone ' + URL + '.git\ncd otryx-rpc\n', encoding="utf-8")
    (root / "docs/maven.md").write_text(
        'com.peachsoft => peachsoft.com; com.peachsoft.otryx => otryx.peachsoft.com',
        encoding="utf-8")
    (root / "docs/publication-readiness.md").write_text(
        '5533429 商标 尚未得到 Central Verified com.peachsoft.otryx',
        encoding="utf-8")


class IdentityTests(unittest.TestCase):
    def test_valid_repo(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            self.assertEqual(validate(root), [])

    def test_old_github_url_is_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            path = root / "pom.xml"
            path.write_text(path.read_text().replace(URL, URL[:-4]), encoding="utf-8")
            self.assertTrue(any("pom.xml" in error for error in validate(root)))

    def test_wrong_namespace_mapping_is_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            path = root / "docs/maven.md"
            path.write_text(path.read_text() + "\n验证 `io.peach` 能发布 com.peachsoft.otryx", encoding="utf-8")
            self.assertTrue(any("reverse-DNS" in error for error in validate(root)))

    def test_missing_migration_preflight_is_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            (root / "docs/publication-readiness.md").unlink()
            self.assertTrue(any("missing" in error for error in validate(root)))


if __name__ == "__main__":
    unittest.main()
