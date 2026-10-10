#!/usr/bin/env python3
"""Render every repository Mermaid block and documentation SVG with real engines.

This is intentionally separate from check_project.py: file existence or XML
well-formedness alone is not evidence that a diagram can be previewed.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote

from PIL import Image
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
FENCED_MERMAID = re.compile(
    r"(?m)^[ \t]*(?P<fence>`{3,}|~{3,})mermaid[^\n]*\n"
    r"(?P<source>[\s\S]*?)^[ \t]*(?P=fence)[ \t]*$"
)
START_MERMAID = re.compile(r"(?m)^[ \t]*(?:`{3,}|~{3,})mermaid\b")
MARKDOWN_IMAGE = re.compile(r"!\[[^\]]*\]\(([^)]+)\)")
HTML_IMAGE = re.compile(r"""<img\b[^>]*\bsrc=["']([^"']+)["']""", re.IGNORECASE)
GRAPHICS = {
    "rect", "path", "circle", "ellipse", "polygon", "polyline",
    "line", "text", "image", "use",
}


def check_pixels(path: Path, label: str) -> None:
    with Image.open(path) as opened:
        image = opened.convert("RGBA")
        if image.width < 32 or image.height < 32:
            raise ValueError(f"{label}: rendered image is unexpectedly small")
        if image.getcolors(maxcolors=1) is not None:
            raise ValueError(f"{label}: raster appears blank / one solid color")


def check_svg(path: Path, temp: Path) -> None:
    data = path.read_bytes()
    if b"<!DOCTYPE" in data.upper():
        raise ValueError(f"{path}: SVG must not contain a DOCTYPE")
    root = ET.fromstring(data)
    if root.tag != "{http://www.w3.org/2000/svg}svg":
        raise ValueError(f"{path}: SVG root is invalid")
    vb = root.attrib.get("viewBox", "").replace(",", " ").split()
    if len(vb) != 4 or any(float(num) <= 0 for num in vb[2:]):
        raise ValueError(f"{path}: missing/invalid SVG viewBox")
    shapes = 0
    for node in root.iter():
        local = node.tag.rsplit("}", 1)[-1]
        if local in GRAPHICS:
            shapes += 1
        for key, value in node.attrib.items():
            if key.rsplit("}", 1)[-1] in {"href", "src"} and value.startswith(
                ("http:", "https:", "//", "file:")
            ):
                raise ValueError(f"{path}: SVG has an external resource: {value}")
    if shapes == 0:
        raise ValueError(f"{path}: SVG contains no renderable graphical elements")
    png = temp / (path.stem + "-" + str(abs(hash(str(path)))) + ".png")
    cairosvg.svg2png(bytestring=data, write_to=str(png), output_width=960)
    check_pixels(png, str(path.relative_to(ROOT)))


def find_markdown() -> list[Path]:
    return sorted(
        path for path in ROOT.rglob("*.md")
        if not any(part in {"target", "node_modules", ".git"} for part in path.parts)
    )


def validate_images(path: Path, source: str) -> None:
    for raw in MARKDOWN_IMAGE.findall(source) + HTML_IMAGE.findall(source):
        target = unquote(raw.split("#", 1)[0].split("?", 1)[0].strip("<> \t"))
        if not target or target.startswith(("http://", "https://", "data:")):
            continue
        resolved = (path.parent / target).resolve()
        if not resolved.is_relative_to(ROOT.resolve()) or not resolved.is_file():
            raise ValueError(f"{path.relative_to(ROOT)}: missing embedded image {raw}")


def render_mermaid(path: Path, index: int, source: str, args: argparse.Namespace,
                   temp: Path) -> None:
    name = path.relative_to(ROOT).as_posix().replace("/", "_").replace(".", "_")
    diagram = temp / f"{name}-{index}.mmd"
    png = temp / f"{name}-{index}.png"
    diagram.write_text(source.strip() + "\n", encoding="utf-8")
    command = [
        str(args.mmdc.resolve()), "-i", str(diagram), "-o", str(png), "-b", "transparent",
        "-p", str(args.puppeteer_config.resolve()),
    ]
    result = subprocess.run(
        command, capture_output=True, text=True, timeout=40, check=False,
        cwd=ROOT,
    )
    if result.returncode:
        detail = (result.stderr or result.stdout).strip()[-1800:]
        raise RuntimeError(
            f"{path.relative_to(ROOT)} diagram #{index}: Mermaid render failed: {detail}"
        )
    if not png.is_file():
        raise RuntimeError(f"{path.relative_to(ROOT)} diagram #{index}: no PNG output")
    check_pixels(png, f"{path.relative_to(ROOT)} Mermaid #{index}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mmdc", type=Path, required=True)
    parser.add_argument("--puppeteer-config", type=Path, required=True)
    args = parser.parse_args()
    if not args.mmdc.is_file() or not args.puppeteer_config.is_file():
        parser.error("Mermaid CLI executable and Puppeteer configuration are required")
    errors: list[str] = []
    count = 0
    docs = find_markdown()
    with tempfile.TemporaryDirectory(prefix="otryx-doc-preview-") as directory:
        temp = Path(directory)
        svgs = sorted((ROOT / "docs/images").rglob("*.svg"))
        for svg in svgs:
            try:
                check_svg(svg, temp)
            except Exception as error:
                errors.append(str(error))
        for path in docs:
            source = path.read_text(encoding="utf-8")
            try:
                validate_images(path, source)
            except Exception as error:
                errors.append(str(error))
            blocks = list(FENCED_MERMAID.finditer(source))
            if len(blocks) != len(START_MERMAID.findall(source)):
                errors.append(f"{path.relative_to(ROOT)}: unterminated Mermaid fence")
            for index, match in enumerate(blocks, start=1):
                count += 1
                try:
                    render_mermaid(path, index, match.group("source"), args, temp)
                except Exception as error:
                    errors.append(str(error))
                else:
                    print(f"OK: {path.relative_to(ROOT)} Mermaid #{index}", flush=True)
    print(
        f"Checked {len(docs)} Markdown files, {count} Mermaid blocks "
        f"and {len(svgs)} SVG sources."
    )
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("All documentation previews rendered successfully.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
