#!/usr/bin/env python3
"""Summarize V2-D.2 JMH JSON evidence into CSV and Markdown."""

from __future__ import annotations

import csv
import json
import re
import sys
from pathlib import Path

NAME = re.compile(
    r"(?P<mode>sample|thrpt)-p(?P<payload>\d+)-c(?P<connections>\d+)-t(?P<threads>\d+)\.json"
)


def metric(data: dict, key: str) -> float | None:
    item = data.get("secondaryMetrics", {}).get(key)
    return None if item is None else item.get("score")


def percentile(primary: dict, key: str) -> float | None:
    values = primary.get("scorePercentiles", {})
    candidates = [key, key.replace(".0", "")]
    if key == "50.0":
        candidates.append("0.5")
    elif key == "99.0":
        candidates.append("0.99")
    elif key == "99.9":
        candidates.append("0.999")
    for candidate in candidates:
        if candidate in values:
            return values[candidate]
    return None


def load_rows(root: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for path in sorted(root.glob("*.json")):
        match = NAME.fullmatch(path.name)
        if not match:
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        if not payload:
            continue
        result = payload[0]
        primary = result["primaryMetric"]
        rows.append(
            {
                "mode": match.group("mode"),
                "payload_bytes": int(match.group("payload")),
                "connections": int(match.group("connections")),
                "threads": int(match.group("threads")),
                "score": primary.get("score"),
                "score_unit": primary.get("scoreUnit"),
                "p50": percentile(primary, "50.0"),
                "p99": percentile(primary, "99.0"),
                "p999": percentile(primary, "99.9"),
                "alloc_b_op": metric(result, "gc.alloc.rate.norm"),
                "alloc_mb_s": metric(result, "gc.alloc.rate"),
                "gc_count": metric(result, "gc.count"),
                "gc_time_ms": metric(result, "gc.time"),
            }
        )
    return rows


def write_csv(root: Path, rows: list[dict[str, object]]) -> None:
    output = root / "summary.csv"
    fields = [
        "mode",
        "payload_bytes",
        "connections",
        "threads",
        "score",
        "score_unit",
        "p50",
        "p99",
        "p999",
        "alloc_b_op",
        "alloc_mb_s",
        "gc_count",
        "gc_time_ms",
    ]
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def value(item: object) -> str:
    if item is None:
        return "-"
    if isinstance(item, float):
        return f"{item:.3f}"
    return str(item)


def write_markdown(root: Path, rows: list[dict[str, object]]) -> None:
    output = root / "summary.md"
    lines = [
        "# V2-D.2 Benchmark Summary",
        "",
        "| Mode | Payload | Connections | Threads | Score | Unit | p50 | p99 | p99.9 | B/op |",
        "|---|---:|---:|---:|---:|---|---:|---:|---:|---:|",
    ]
    for row in rows:
        lines.append(
            "| {mode} | {payload_bytes} | {connections} | {threads} | {score} | "
            "{score_unit} | {p50} | {p99} | {p999} | {alloc_b_op} |".format(
                **{key: value(item) for key, item in row.items()}
            )
        )
    lines.extend(
        [
            "",
            "> This file reports measurements only. It does not declare a performance winner or a production SLO.",
            "",
        ]
    )
    output.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: summarize_v2d2_results.py <result-directory>")
    root = Path(sys.argv[1])
    rows = load_rows(root)
    if not rows:
        raise SystemExit(f"No V2-D.2 JMH JSON results found in {root}")
    write_csv(root, rows)
    write_markdown(root, rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
