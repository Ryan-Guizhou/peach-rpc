#!/usr/bin/env python3
"""Regression tests for the shared Cursor/Codex MCP adapter generator."""

import json
import tempfile
import unittest
from pathlib import Path

import sync_agent_mcp as mcp


class AgentMcpTest(unittest.TestCase):

    def setUp(self):
        self.servers = mcp.load_spec()

    def test_cursor_excludes_mysql_and_never_embeds_secrets(self):
        data = json.loads(mcp.render_cursor(self.servers))
        self.assertNotIn("mysql", data["mcpServers"])
        self.assertIn("github", data["mcpServers"])
        self.assertEqual(
            "${env:GITHUB_PERSONAL_ACCESS_TOKEN}",
            data["mcpServers"]["github"]["env"]["GITHUB_PERSONAL_ACCESS_TOKEN"],
        )
        self.assertNotIn("password=", mcp.render_cursor(self.servers).lower())

    def test_codex_disables_mysql_and_maps_workspace(self):
        config = mcp.render_codex(self.servers)
        self.assertIn(
            '[mcp_servers."mysql"]\ncommand = "cmd"\n'
            'args = ["/c", "npx", "-y", "mcp-server-mysql@latest"]\n'
            "enabled = false", config)
        self.assertIn('"--path", "."', config)
        self.assertNotIn("GITHUB_PERSONAL_ACCESS_TOKEN =", config)

    def test_generated_configs_remain_synced(self):
        self.assertEqual([], mcp.synchronize(check=True))

    def test_check_detects_drift_without_writing(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / ".agents/config").mkdir(parents=True)
            (root / ".agents/config/agent-mcp.json").write_text(
                mcp.SOURCE.read_text(encoding="utf-8"), encoding="utf-8")
            self.assertEqual(
                [".cursor/mcp.json", ".codex/config.toml"],
                mcp.synchronize(check=True, root=root))
            mcp.synchronize(root=root)
            self.assertEqual([], mcp.synchronize(check=True, root=root))
            cursor = root / ".cursor/mcp.json"
            cursor.write_text("{}\n", encoding="utf-8")
            self.assertEqual(
                [".cursor/mcp.json"],
                mcp.synchronize(check=True, root=root))
            self.assertEqual("{}\n", cursor.read_text(encoding="utf-8"))

    def test_rejects_enabled_mysql(self):
        with tempfile.TemporaryDirectory() as folder:
            copy = json.loads(mcp.SOURCE.read_text(encoding="utf-8"))
            copy["servers"][0]["enabled_by_default"] = True
            path = Path(folder) / "spec.json"
            path.write_text(json.dumps(copy), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "MySQL"):
                mcp.load_spec(path)

    def test_rejects_static_secret_field(self):
        with tempfile.TemporaryDirectory() as folder:
            copy = json.loads(mcp.SOURCE.read_text(encoding="utf-8"))
            copy["servers"][1]["static_env"] = {
                "GITHUB_ACCESS_TOKEN": "not-allowed"}
            path = Path(folder) / "spec.json"
            path.write_text(json.dumps(copy), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "secrets"):
                mcp.load_spec(path)


if __name__ == "__main__":
    unittest.main()
