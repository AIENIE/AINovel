#!/usr/bin/env python3
"""Merge additional i18n keys into the three locale files.

Usage: merge_i18n.py <additions.json>
additions.json shape:
{
  "zh-CN": { "key": "值", ... },
  "zh-TW": { "key": "值", ... },
  "en":    { "key": "值", ... }
}
Keys already present are skipped (their existing value wins).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "i18n" / "locales"
FILES = {
    "zh-CN": ROOT / "zh-CN.ts",
    "zh-TW": ROOT / "zh-TW.ts",
    "en": ROOT / "en.ts",
}

def load_keys(path: Path):
    text = path.read_text(encoding="utf-8")
    keys = set(re.findall(r'^\s*"([^"]+)":', text, flags=re.M))
    return keys, text

def merge(path: Path, additions: dict):
    keys, text = load_keys(path)
    lines = text.splitlines(keepends=True)
    # find the final closing "};" line index
    close_idx = None
    for i in range(len(lines) - 1, -1, -1):
        if re.match(r"\s*\};\s*$", lines[i]):
            close_idx = i
            break
    if close_idx is None:
        raise SystemExit(f"cannot find closing marker in {path}")

    insert_lines = []
    for key in sorted(additions):
        if key in keys:
            continue
        value = additions[key]
        insert_lines.append(f'  "{key}": "{value}",\n')
    if not insert_lines:
        return
    # prepend a blank line before the closing brace for readability
    lines[close_idx:close_idx] = ["\n"] + insert_lines
    path.write_text("".join(lines), encoding="utf-8")
    print(f"{path.name}: inserted {len(insert_lines)} keys")

def main():
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    for lang, additions in data.items():
        if lang not in FILES:
            raise SystemExit(f"unknown lang {lang}")
        merge(FILES[lang], additions)

if __name__ == "__main__":
    main()
