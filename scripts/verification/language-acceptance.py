"""Explicit local acceptance setup and ledger export. Never resets or tops up a run.

No model calls are made by setup/ledger. Private configuration and database credentials
remain outside the repository; exported evidence excludes passwords and bearer tokens.
"""
import argparse
import configparser
import json
import os
import re
import subprocess
from pathlib import Path
import pymysql
import yaml

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = (Path(os.environ.get("AIENIE_RUNTIME_ROOT", ROOT.parent.parent / "aienie-runtime")) / 'private/app-secrets')
RUN = "language-20261007-v1"
SCHEMA = "aienie_novel_audit_test_language_20261007"
EVIDENCE = ROOT / "doc/verification/language-20261007"

def secrets(path):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    return values

def root_connection(database=None):
    config = configparser.ConfigParser(interpolation=None)
    config.read((Path(os.environ.get("AIENIE_RUNTIME_ROOT", ROOT.parent.parent / "aienie-runtime")) / 'private/credentials/database/aienie-devvm-mysql-root.cnf'), encoding="utf-8-sig")
    client = config["client"]
    return pymysql.connect(host="localmysql.testhut.top", port=23306, user=client["user"], password=client["password"], database=database, charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, connect_timeout=10)

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, default=str)+"\n", encoding="utf-8")

def setup():
    assert re.fullmatch(r"aienie_novel_audit_test_[a-z0-9_]+", SCHEMA)
    # Dedicated local validation configuration, not a backup or a change to the regular launch pair.
    source = PRIVATE / "ainovel.env"
    target = PRIVATE / f"ainovel-{RUN}.env"
    config = yaml.safe_load(Path(str(source)+".application.yml").read_text(encoding="utf-8-sig"))
    config.setdefault("app", {})["language"] = {"enabled": True, "generation-enabled": True, "diagnosis-enabled": True, "input-token-budget": 24000}
    config["app"].setdefault("ai", {})["validation"] = {"run-id": RUN, "maximum-calls": 40, "maximum-provider-attempts": 120, "provider-attempts-per-rpc": 3, "local-quality-only": True}
    target.write_text(source.read_text(encoding="utf-8-sig"), encoding="utf-8")
    Path(str(target)+".application.yml").write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
    with root_connection() as db:
        with db.cursor() as q:
            q.execute(f"CREATE DATABASE IF NOT EXISTS `{SCHEMA}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        db.commit()
    with root_connection("ainovel") as db:
        with db.cursor() as q:
            q.execute("SELECT * FROM ai_validation_budgets WHERE id=%s", (RUN,))
            existing = q.fetchone()
            if existing:
                assert existing["call_limit"] == 40 and existing["provider_attempt_limit"] == 120, "Existing budget differs: never reset it"
            else:
                q.execute("INSERT INTO ai_validation_budgets(id,used,call_limit,provider_attempt_limit,reserved_provider_attempts) VALUES(%s,0,40,120,0)", (RUN,))
        db.commit()
    print(json.dumps({"run": RUN, "schema": SCHEMA, "configuration": str(target), "modelCalls": 0, "action": "configuration and independent ledger prepared; no inference"}))

def ledger():
    with root_connection("ainovel") as db:
        with db.cursor() as q:
            q.execute("SELECT * FROM ai_validation_budgets WHERE id=%s", (RUN,)); budget = q.fetchone()
            q.execute("SELECT attempt,request_id,model,status,operation_kind,reserved_provider_attempts,created_at FROM ai_validation_calls WHERE run_id=%s ORDER BY attempt", (RUN,)); calls = q.fetchall()
    save(EVIDENCE/"ledger.json", {"budget": budget, "calls": calls, "note": "reservedProviderAttempts is a worst-case bound, not measured provider sends"})
    print(json.dumps({"budget": budget, "recordCount": len(calls)}, default=str))


def evidence():
    """Only this run's fixture prompts/results; never export credentials or account balances."""
    with root_connection("ainovel") as db:
        with db.cursor() as q:
            q.execute("SELECT attempt,request_id,model,status,operation_kind,reserved_provider_attempts,request_json,result_json FROM ai_validation_calls WHERE run_id=%s ORDER BY attempt", (RUN,))
            calls=q.fetchall()
    for item in calls:
        for key in ("request_json","result_json"):
            if item[key]: item[key]=json.loads(item[key])
    save(EVIDENCE/"model-calls.json",calls)
    for path in EVIDENCE.glob("generation-G*.json"):
        item=json.loads(path.read_text(encoding="utf-8"))
        item.get("response",{}).pop("remainingCredits",None)
        save(path,item)
    markers=("H2-SECRET-LANGUAGE-20261007","H2-CARD-SECRET-LANGUAGE-20261007","H2隐藏资料标记")
    checks=[]
    for item in calls:
        key=item["request_id"] or ""
        if "language_check:" in key or "language_patch:" in key:
            raw=json.dumps(item["request_json"],ensure_ascii=False)
            checks.append({"attempt":item["attempt"],"requestId":key,"hiddenMarkersAbsent":all(m not in raw for m in markers)})
    save(EVIDENCE/"h2-request-isolation.json",{"checks":checks,"allPassed":all(c["hiddenMarkersAbsent"] for c in checks),"scope":"synthetic hidden markers in this acceptance fixture; not a proof about arbitrary hidden information"})
    print(json.dumps({"callsExported":len(calls),"languageRequestsChecked":len(checks),"hiddenMarkersAbsent":all(c["hiddenMarkersAbsent"] for c in checks)}))

def database_test_environment():
    """Called by a private PowerShell process; credentials are never printed to tool output."""
    config = configparser.ConfigParser(interpolation=None)
    config.read((Path(os.environ.get("AIENIE_RUNTIME_ROOT", ROOT.parent.parent / "aienie-runtime")) / 'private/credentials/database/aienie-devvm-mysql-root.cnf'), encoding="utf-8-sig")
    return {"AIENIE_AUDIT_MYSQL_URL": f"jdbc:mysql://localmysql.testhut.top:23306/{SCHEMA}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false", "AIENIE_AUDIT_MYSQL_USERNAME": config["client"]["user"], "AIENIE_AUDIT_MYSQL_PASSWORD": config["client"]["password"]}

def freeze():
    import xml.etree.ElementTree as ET
    report=next((ROOT/"backend/target/surefire-reports").glob("TEST-*.xml"))
    properties=ET.parse(report).findall("./properties/property")
    classpath=next(p.attrib["value"] for p in properties if p.attrib["name"]=="java.class.path")
    subprocess.run(["java","--class-path",classpath,str(ROOT/"scripts/verification/LanguageExperiment.java"),str(EVIDENCE/"frozen-prompts-v2.json")],check=True,cwd=ROOT)

if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("command", choices=["setup","ledger","evidence","mysql-test","freeze","l2-backend"]); args=parser.parse_args()
    if args.command == "setup": setup()
    elif args.command == "ledger": ledger()
    elif args.command == "evidence": evidence()
    elif args.command == "freeze": freeze()
    elif args.command == "l2-backend":
        # Opt-in external capacity/backfill suites require independent empty schemas.
        result=subprocess.run(["pwsh","-NoProfile","-File",str(ROOT/"scripts/windows/Test-Local.ps1"),"-Level","L2","-Component","Backend"],cwd=ROOT,env=os.environ.copy())
        raise SystemExit(result.returncode)
    else:
        import subprocess
        result=subprocess.run(["mvn.cmd","-q","-Dtest=LanguageQualityMysqlTest,CreatorWorkflowMysqlTest","test"],cwd=ROOT/"backend",env={**os.environ,**database_test_environment()})
        raise SystemExit(result.returncode)
