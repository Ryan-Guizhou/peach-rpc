#!/usr/bin/env python3
"""Execute the OTRYX 2.0 namespace migration exactly once."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
archives = ("docs/release-notes-", "docs/engineering/stable-baseline.md",
            "docs/engineering/agent-governance-plan.md",
            "docs/engineering/agent-governance-checkpoint.md")
reserved = ("protocol.version", "schema.version", "schema.fingerprint")
paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
updated = 0
for rel in paths:
    if not rel or rel.startswith(archives) or rel == "scripts/migrate_to_otryx.py":
        continue
    source = root / rel
    if not source.is_file() or source.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".zip", ".pdf", ".jar"):
        continue
    try:
        old = source.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    new = old.replace("io.peach.rpc", "com.peachsoft.otryx")
    new = new.replace("PeachRpc", "OtryxRpc")
    new = re.sub(r"\bPeach(?=[A-Z])", "Otryx", new)
    new = new.replace("peach-rpc", "otryx")
    new = new.replace("Peach RPC", "OTRYX RPC")
    new = new.replace("PEACH_RPC", "OTRYX_RPC")
    new = new.replace("peach.rpc", "otryx.rpc")
    new = re.sub(r"^(\s*)peach:", r"\1otryx:", new, flags=re.M)
    new = new.replace("tools/rpc-comparison/peach", "tools/rpc-comparison/otryx")
    new = new.replace("<module>peach</module>", "<module>otryx</module>")
    new = new.replace("peach.version", "otryx.version")
    for key in reserved:
        new = new.replace("otryx.rpc." + key, "peach.rpc." + key)
    if rel == "pom.xml":
        new = new.replace("<revision>1.0.1</revision>", "<revision>2.0.0-SNAPSHOT</revision>")
    if rel == "tools/rpc-comparison/pom.xml":
        new = new.replace("<otryx.version>1.0.1</otryx.version>",
                          "<otryx.version>2.0.0-SNAPSHOT</otryx.version>")
    if rel == "docs/release-status.properties":
        new = re.sub(r"(?m)^version=.*$", "version=2.0.0-SNAPSHOT", new)
    if rel.startswith("docs/"):
        new = new.replace("1.0.1 Release Prep", "2.0.0-SNAPSHOT Migration")
    if new != old:
        source.write_text(new, encoding="utf-8")
        updated += 1
print("OTRYX namespace migration updated", updated, "tracked files")
