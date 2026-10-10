#!/usr/bin/env python3
"""Check checked-in OTRYX RPC skill entrypoints and Agent routing consistency."""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKILLS = (
    "using-otryx-java-engineering",
    "using-otryx-compatibility",
    "using-otryx-performance",
    "review-otryx-changes",
)


def validate(root=ROOT):
    errors = []
    guide = (root / "AGENTS.md").read_text(encoding="utf-8")
    for name in SKILLS:
        folder = root / ".agents/skills" / name
        entry = folder / "SKILL.md"
        metadata = folder / "agents/openai.yaml"
        if not entry.is_file() or not metadata.is_file():
            errors.append(f"Missing Skill files: {name}")
            continue
        content = entry.read_text(encoding="utf-8")
        match = re.match(
            r"\A---\nname: ([a-z0-9-]+)\ndescription: ([^\n]+)\n---\n",
            content,
        )
        if not match or match.group(1) != name:
            errors.append(f"Invalid Skill frontmatter: {name}")
        else:
            try:
                description = json.loads(match.group(2))
                if not isinstance(description, str) or len(description) < 30:
                    errors.append(f"Invalid Skill description: {name}")
            except (json.JSONDecodeError, TypeError):
                errors.append(f"Skill description must be a quoted YAML scalar: {name}")
        if re.search(r"\[TODO(?::|\])|\bEXAMPLE_ASSET\b", content):
            errors.append(f"Unedited scaffold: {name}")
        if len(content.splitlines()) > 500:
            errors.append(f"Skill exceeds loading guideline: {name}")
        if name not in guide:
            errors.append(f"AGENTS.md does not route {name}")
        yaml = metadata.read_text(encoding="utf-8")
        if ("interface:" not in yaml or
                "display_name:" not in yaml or
                "short_description:" not in yaml):
            errors.append(f"Missing Skill UI metadata: {name}")
    return errors


def main():
    try:
        errors = validate()
    except (OSError, UnicodeDecodeError) as error:
        print(f"Invalid repository Skills: {error}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print("ERROR: " + error, file=sys.stderr)
        return 1
    print(f"Verified {len(SKILLS)} routed repository Skills.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
