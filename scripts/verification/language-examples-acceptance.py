"""Independent 40/120 examples experiment. Existing ledgers and evidence are never reset.

Gateway preflight must be recorded before setup or paid calls. No credentials are exported.
"""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RUN = "language-examples-20261008-v1"
OUT = ROOT / "doc/verification/language-examples-20261008"
spec = importlib.util.spec_from_file_location("base", Path(__file__).with_name("language-acceptance.py"))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
base.RUN, base.EVIDENCE, base.SCHEMA = RUN, OUT, "aienie_novel_audit_test_examples_20261008"

def freeze():
    props = ET.parse(next((ROOT / "backend/target/surefire-reports").glob("TEST-*.xml"))).findall("./properties/property")
    cp = next(p.attrib["value"] for p in props if p.attrib["name"] == "java.class.path")
    subprocess.run(["java", "--class-path", cp, str(ROOT / "scripts/verification/LanguageExamplesExperiment.java"), str(OUT / "frozen-prompts.json")], check=True, cwd=ROOT)
    corpus = {x["id"]: x["text"] for x in json.loads((ROOT / "doc/research/slop-20260926/reading-corpus.json").read_text(encoding="utf-8-sig"))}
    cases = {k: corpus[k] for k in ("S5", "S6")}
    cases["new-problems"] = "仓库里只剩陆文和谢青。陆文把两份出库单摆在桌上，谢青低头核对手里那一份。\n\n核到最后一项，圈出一个数量，把单子递回桌对面。\n\n下午，两人各拿一份新单据坐在桌边，笔都已经拿在手上。\n\n核完数量，签上名字，把单据放进文件袋。"
    cases["new-boundary"] = "叶秋把相片按日期排好，抽出合影，夹进相册。她关上台灯，把相册放回架上。\n\n“带原件？”\n\n“带。”\n\n门外的脚步停了。\n\n她走到门边，刚要伸手，脚步又响了起来，渐渐远去。\n\n她没有开门。隔壁门锁随后响了一声，接着传来邻居的说话声。刚才停在门外的人不一定是来找她的。"
    base.save(OUT / "frozen-diagnostic-cases.json", cases)
    # Predeclared editorial targets, independent of what either diagnosis happens to find.
    revisions = {}
    for name, index in (("S5", 2), ("S6", 0)):
        paragraphs = cases[name].split("\n\n")
        revisions[name] = {"original": paragraphs[index], "context": "\n\n".join(paragraphs[max(0,index-1):index+2]), "issue": {"kind":"LANGUAGE", "category":"PARAGRAPH", "direction":"S5：恢复人物实际的想法并消除无明确所指的表达。S6：补足必要主体和对象，自然承接动作与结果，保留原意。"}}
    base.save(OUT / "frozen-revision-inputs.json", revisions)

if __name__ == "__main__":
    action = sys.argv[1]
    if action == "freeze": freeze()
    elif action == "setup":
        proof = json.loads((OUT / "gateway-preflight.json").read_text(encoding="utf-8"))
        assert proof["providerAttemptUpperBound"] == 3 and proof["verified"], "Verify deployed retry behavior first"
        base.setup()
    elif action == "ledger": base.ledger()
    elif action == "evidence":
        base.evidence()
        calls=json.loads((OUT / "model-calls.json").read_text(encoding="utf-8"))
        markers=("H2-SECRET-LANGUAGE-20261008", "H2-CARD-SECRET-LANGUAGE-20261008", "H2隐藏资料标记")
        checks=[{"attempt":c["attempt"], "requestId":c["request_id"], "hiddenMarkersAbsent":all(m not in json.dumps(c["request_json"],ensure_ascii=False) for m in markers)} for c in calls]
        base.save(OUT / "h2-request-isolation.json", {"markers":markers,"checks":checks,"allPassed":bool(checks) and all(c["hiddenMarkersAbsent"] for c in checks),"scope":"This batch's synthetic hidden markers only; not proof about arbitrary hidden information."})
    elif action == "mysql-test":
        result = subprocess.run(["mvn.cmd", "-q", "-Dtest=LanguageQualityMysqlTest,CreatorWorkflowMysqlTest", "test"], cwd=ROOT / "backend", env={**os.environ, **base.database_test_environment()})
        raise SystemExit(result.returncode)
    else: raise ValueError("Unknown action")
