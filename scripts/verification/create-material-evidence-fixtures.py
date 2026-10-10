"""Create deterministic, synthetic evidence fixtures once; never rewrite a frozen dataset."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "backend/src/test/resources/material-selection-20261003"
if DESTINATION.exists():
    raise SystemExit("Frozen fixture directory already exists; create a separately versioned dataset instead.")
DESTINATION.mkdir(parents=True)

def write(name, value):
    path = DESTINATION / name
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return hashlib.sha256(path.read_bytes()).hexdigest()

def chunks(source):
    text = source["text"]
    result = []
    start = 0
    while start < len(text):
        end = min(len(text), start + 900)
        result.append({"id": source["id"] + ":" + str(len(result)), "source": source["id"], "start": start, "end": end, "text": text[start:end]})
        if end == len(text):
            break
        start = end - 120
    return result

groups = ["entity_alias", "fictional_term", "paraphrase", "negation_condition", "cross_paragraph", "inspiration", "no_answer", "version_permission"]
case_order = sorted(range(1, 26), key=lambda number: hashlib.sha256(f"ainovel-evidence-v1:{number}".encode()).hexdigest())
development_cases = set(case_order[:15])
corpus, queries, answers = [], {"development": [], "holdout": []}, {"development": [], "holdout": []}

for number in range(1, 26):
    case = f"case-{number:02}"
    bridge, alias, tea, actor, token = f"雨桥{number:02}", f"青梧渡{number:02}", f"雨桥{number:02}茶馆", f"巡桥人阿{number:02}", f"墨铃签{number:02}"
    condition = f"{bridge}日出后禁止通行，日落后也不是一律开放：只有雨停且持有{token}才可过桥。"
    learned = f"第二章中，{actor}尚不知道过桥暗号；他误以为白天可以过桥，这只是人物信念。"
    acquired = f"第三章末，守桥人当面告诉{actor}暗号；他从此时才获知，不能倒推为第二章已经知道。"
    sources = [
        {"id": case + ":bridge-v2", "material": case + ":bridge", "version": 2, "visibility": "CURRENT", "title": bridge + "新规", "text": f"版本二有效规约。{bridge}又名{alias}，是一座桥，不是同名茶馆。\n{condition}\n{token}是守桥人签发的通行凭证，不是货币。", "entity": bridge, "aliases": [alias]},
        {"id": case + ":tea", "material": case + ":tea", "version": 1, "visibility": "CURRENT", "title": tea, "text": f"{tea}与{bridge}不是同一地点，也没有青梧渡的别名。茶馆白天营业，雨夜闭门。门檐积水，纸灯晃动；掌柜擦去杯沿的雾，远处木桥没有脚步声。", "entity": tea, "aliases": []},
        {"id": case + ":chronicle", "material": case + ":chronicle", "version": 1, "visibility": "CURRENT", "title": actor + "知情顺序", "text": learned + "\n\n" + (f"河岸勘测编号{number:02}：记载水位与旧木桩，不提供通行授权。\n" * 45) + "\n" + acquired, "entity": actor, "aliases": []},
        {"id": case + ":withheld", "material": case + (":bridge" if number % 3 == 1 else ":withheld"), "version": 1, "visibility": {0: "REVOKED", 1: "OLD_VERSION", 2: "OTHER_OWNER"}[number % 3], "title": bridge + "受限记录", "text": f"{bridge}绝密旧记录：白天无条件放行，秘密花名为玄鸦{number:02}。本条不可作为当前作者的有效证据。", "entity": bridge, "aliases": []},
    ]
    corpus.extend(sources)
    samples = [
        (f"{alias}的通行规约", sources[0], condition),
        (f"{token}究竟是什么凭证", sources[0], f"{token}是守桥人签发的通行凭证，不是货币。"),
        (f"{bridge}天黑后想走到对岸，要满足什么限制", sources[0], condition),
        (f"{bridge}太阳升起后能否直接放行？下雨的夜里是否也允许", sources[0], condition),
        (f"{actor}第二章是否已知道暗号？什么时候真正知道", sources[2], [learned, acquired]),
        (f"想写{tea}附近的雨夜、寂静和迟疑，有什么氛围参考", sources[1], "门檐积水，纸灯晃动；掌柜擦去杯沿的雾，远处木桥没有脚步声。"),
        (f"{bridge}曾停靠的月亮飞艇型号是什么", None, None),
        ((f"{bridge}旧版白天放行规则目前还有效吗" if number % 3 == 1 else f"{bridge}秘密花名玄鸦{number:02}的原文"), sources[0] if number % 3 == 1 else None, condition if number % 3 == 1 else None),
    ]
    split = "development" if number in development_cases else "holdout"
    for index, (query, source, quotes) in enumerate(samples):
        identity = f"{case}:{groups[index]}"
        queries[split].append({"id": identity, "case": case, "group": groups[index], "mode": "inspiration" if index == 5 else "fact", "query": query})
        required = [] if quotes is None else quotes if isinstance(quotes, list) else [quotes]
        evidence, relevant = [], {}
        if source:
            for quote in required:
                position = source["text"].index(quote)
                evidence.append({"source": source["id"], "quote": quote, "start": position, "end": position + len(quote)})
                for chunk in chunks(source):
                    if chunk["start"] <= position and chunk["end"] >= position + len(quote):
                        relevant[chunk["id"]] = 3
        answers[split].append({"id": identity, "evidence": evidence, "relevance": relevant, "answerable": bool(source), "inspirationProxyOnly": index == 5})

pairs = []
for kind, reference, normal, defect, decision in [
    ("belief", "阿岚以为桥白天开放，但桥规写明白天禁行。", "阿岚相信白天可以过桥。", "桥的客观规约是白天开放。", "CONTRADICTED"),
    ("negation", "守桥人没有把钥匙交给阿岚。", "阿岚尚未得到钥匙。", "阿岚已从守桥人处接过钥匙。", "CONTRADICTED"),
    ("condition", "雨停且持墨铃签时才可过桥。", "雨停后，持签的阿岚过桥。", "暴雨中，没有凭证的阿岚按规约过桥。", "CONTRADICTED"),
    ("time", "桥在第二日修复；第一日仍然断裂。", "第二日修复后阿岚过桥。", "第一日阿岚走过完整的桥面。", "CONTRADICTED"),
    ("homonym", "雨桥茶馆白天营业；雨桥桥梁白天禁行。", "阿岚白天进入雨桥茶馆。", "因为同名茶馆营业，所以桥梁白天开放。", "CONTRADICTED"),
    ("knowledge", "暗号只在第三章末当面告诉阿岚；此前没有其他获知来源。", "第三章末阿岚才知道暗号。", "第二章阿岚已熟知暗号。", "CONTRADICTED"),
    ("revision", "本次冻结的是资料版本二：白天禁行。版本一曾写白天开放，现已失效。", "按版本二白天禁行。", "按有效规约白天无条件开放。", "CONTRADICTED"),
]:
    pairs.append({"id": kind, "reference": reference, "normal": {"text": normal, "expected": "SUPPORTED"}, "defect": {"text": defect, "expected": decision}})
pairs += [{"id": "insufficient", "reference": "桥规未记载飞艇。", "normal": {"text": "飞艇型号无法判断。", "expected": "INSUFFICIENT"}, "defect": {"text": "飞艇型号一定是银鸢。", "expected": "INSUFFICIENT"}}]
files = {"corpus.json": corpus, "development-queries.json": queries["development"], "holdout-queries.json": queries["holdout"], "development-answers.json": answers["development"], "holdout-answers.json": answers["holdout"], "paired-checks.json": pairs}
hashes = {name: write(name, value) for name, value in files.items()}
config = {"dimensions": 1024, "chunk": "cp900-120-v1", "inputTemplate": "document-v1", "rrfConstant": 60, "candidateLimit": 40, "finalLimit": 8, "arms": ["basic", "standard", "flash", "standard-rerank", "flash-rerank"]}
write("manifest.json", {"version": "synthetic-evidence-v1", "sourceGroups": 25, "documents": len(corpus), "queries": 200, "development": 120, "holdout": 80, "groups": groups, "configuration": config, "sha256": hashes, "limits": {"rmb": 100, "externalAttempts": 1000}, "provenance": "Deterministic fictional engineering fixtures, not author-rated real-world quality evidence", "holdoutRule": "Freeze configuration before evaluation. Do not read holdout answers while tuning. Never silently replace this dataset."})
print(f"Frozen {len(corpus)} documents, 200 queries and {len(pairs)} paired checks: {DESTINATION}")
