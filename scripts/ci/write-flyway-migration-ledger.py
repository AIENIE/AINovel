#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import pathlib
import re
import sys


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        raise SystemExit("usage: write-flyway-migration-ledger.py REPOSITORY OUTPUT")
    repository = pathlib.Path(argv[1]).resolve(strict=True)
    target = pathlib.Path(argv[2])
    source = repository / "backend/src/main/resources/db/migration"
    entries: list[dict[str, str]] = []
    seen: set[int] = set()
    for path in source.glob("V*__*.sql"):
        if path.is_symlink() or not path.is_file():
            raise SystemExit(f"unsafe migration: {path}")
        match = re.fullmatch(r"V([0-9]+)__(.+)\.sql", path.name)
        if match is None or int(match.group(1)) in seen:
            raise SystemExit(f"invalid migration: {path.name}")
        seen.add(int(match.group(1)))
        entries.append({
            "path": f"release/migrations/sql/{path.name}",
            "sha256": "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest(),
            "version": match.group(1),
        })
    entries.sort(key=lambda item: int(item["version"]))
    if [int(item["version"]) for item in entries] != list(range(1, 16)):
        raise SystemExit("AINovel Flyway chain must remain contiguous V1..V15")
    value = {
        "authorization": {
            "minimum_release_manifest_version": 4,
            "outer_signature_required": True,
            "restore_point_required_before_execute": True,
        },
        "canonical_component_id": "ai-novel",
        "latest_version": "15",
        "location": "filesystem:/app/release/migrations/sql",
        "migrations": entries,
        "schema_version": "aienie-production-flyway-ledger-v2",
    }
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, sort_keys=True, separators=(",", ":")), encoding="utf-8", newline="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
