"""Collect frozen rankings with production recall in an existing isolated MySQL schema.

Basic mode never constructs an AI client. --live explicitly uses the develop gateway's
persistent evaluation budget; it neither creates nor resets that budget. Credentials
are read from the existing private YAML/env pair and remain in the child process.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

import pymysql

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/config-pair"))
from config_pair import parse_env
from read_configuration import read


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment-file", required=True, type=Path)
    parser.add_argument("--schema", required=True)
    parser.add_argument("--run", required=True)
    parser.add_argument("--split", required=True, choices=["development", "holdout"])
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--user-id", type=int)
    args = parser.parse_args()
    if sys.platform != "win32":
        parser.error("Windows native validation required")
    if not re.fullmatch(r"aienie_novel_audit_test_[A-Za-z0-9_]+", args.schema):
        parser.error("An existing isolated test schema is required")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", args.run):
        parser.error("Stable evaluation run required")
    if args.live and (args.user_id is None or args.user_id <= 0):
        parser.error("--live requires the authorized gateway user ID")
    pair = args.environment_file.resolve()
    application = Path(str(pair) + ".application.yml")
    if not pair.is_file() or not application.is_file() or pair.is_symlink() or application.is_symlink():
        parser.error("Existing regular private configuration pair required")
    artifacts = (ROOT / "artifacts/retrieval-evidence-20261003").resolve()
    output = args.output.resolve()
    if not output.is_relative_to(artifacts):
        parser.error("Output must be inside this round's artifact directory")
    output.parent.mkdir(parents=True, exist_ok=True)
    values = read(ROOT, "local", application)
    secrets = parse_env(pair.read_text(encoding="utf-8-sig"))
    # All dependencies retain the matrix's develop/local endpoints.
    if values.get("MYSQL_HOST") != "localmysql.testhut.top" or int(values.get("MYSQL_PORT", 0)) != 23306:
        parser.error("Matrix MySQL endpoint required")
    username = secrets.get("MYSQL_USER", values.get("MYSQL_USER"))
    password = secrets.get("MYSQL_PASSWORD")
    if not username or not password:
        parser.error("Private MySQL credentials required")
    with pymysql.connect(host=values["MYSQL_HOST"], port=23306, user=username, password=password,
                         database=args.schema, charset="utf8mb4", connect_timeout=10) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT DATABASE()")
            if cursor.fetchone()[0] != args.schema:
                parser.error("Isolated schema identity mismatch")
    env = os.environ.copy()
    # parse_env rejects process injection keys; no secret is rendered or saved here.
    env.update(secrets)
    env.update({
        "SPRING_PROFILES_ACTIVE": "local",
        "SPRING_CONFIG_ADDITIONAL_LOCATION": application.as_uri(),
        "AIENIE_AUDIT_MYSQL_URL": f"jdbc:mysql://localmysql.testhut.top:23306/{args.schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        "AIENIE_AUDIT_MYSQL_USERNAME": username,
        "AIENIE_AUDIT_MYSQL_PASSWORD": password,
        "AIENIE_SELECTION_MODE": "live" if args.live else "basic",
        "AIENIE_SELECTION_RUN": args.run,
        "AIENIE_SELECTION_SPLIT": args.split,
        "AIENIE_SELECTION_OUTPUT": str(output),
    })
    if args.live:
        env["AIENIE_SELECTION_USER_ID"] = str(args.user_id)
    else:
        env.pop("AIENIE_SELECTION_USER_ID", None)
    log = output.with_suffix(".log")
    started = time.monotonic()
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(["mvn.cmd", "-q", "-Dtest=MaterialSelectionMysqlTest", "test"],
                                cwd=ROOT / "backend", env=env, stdout=stream, stderr=subprocess.STDOUT)
    record = {"run": args.run, "mode": env["AIENIE_SELECTION_MODE"], "split": args.split,
              "schema": args.schema, "exitCode": result.returncode,
              "seconds": round(time.monotonic() - started, 1), "output": str(output), "log": str(log)}
    if output.exists():
        record["rankingsSha256"] = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix(".execution.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(record, ensure_ascii=False))
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
