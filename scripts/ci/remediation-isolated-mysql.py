#!/usr/bin/env python3
"""Run opt-in remediation tests in a disposable MySQL schema and always drop it."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import pymysql


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", required=True, type=Path)
    parser.add_argument("--tests", required=True, help="Maven -Dtest selector")
    args = parser.parse_args()
    values = dict(line.split("=", 1) for line in args.env_file.read_text(encoding="utf-8").splitlines()
                  if line.strip() and not line.lstrip().startswith("#"))
    host = values["MYSQL_HOST"]
    port = int(values["MYSQL_PORT"])
    if host not in ("127.0.0.1", "localhost") or not 1 <= port <= 65535:
        raise ValueError("Capacity tests require an isolated loopback MySQL instance")
    name = "aienie_novel_audit_test_" + uuid4().hex
    conn = pymysql.connect(host=host, port=port, user=values["MYSQL_USER"],
                           password=values["MYSQL_PASSWORD"], autocommit=True)
    try:
        with conn.cursor() as cursor:
            cursor.execute(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        env = os.environ.copy()
        env.update({
            "AIENIE_AUDIT_MYSQL_URL": f"jdbc:mysql://{host}:{port}/{name}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
            "AIENIE_AUDIT_MYSQL_USERNAME": values["MYSQL_USER"],
            "AIENIE_AUDIT_MYSQL_PASSWORD": values["MYSQL_PASSWORD"],
        })
        repo = Path(__file__).resolve().parents[2]
        print(f"Isolated MySQL schema: {name}", flush=True)
        return subprocess.call(["mvn.cmd" if os.name == "nt" else "mvn", "-q", f"-Dtest={args.tests}", "test"],
                               cwd=repo / "backend", env=env)
    finally:
        try:
            with conn.cursor() as cursor:
                cursor.execute(f"DROP DATABASE IF EXISTS `{name}`")
            print(f"Dropped isolated MySQL schema: {name}", flush=True)
        finally:
            conn.close()


if __name__ == "__main__":
    sys.exit(main())
