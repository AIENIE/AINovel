"""Freeze the 2026-09-26 corpus before evaluation. No model calls or credentials."""
import hashlib
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "doc/research/slop-20260926"
OUT.mkdir(parents=True, exist_ok=True)
if (OUT / "samples.json").exists():
    raise SystemExit("Frozen corpus exists; do not overwrite after evaluation.")

def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def save(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

fast_path = "artifacts/closure-repair-20260922/draft-fast.md"
crafted_path = "artifacts/closure-repair-20260922/draft-crafted.md"
fast = (REPO / fast_path).read_text(encoding="utf-8-sig")
crafted = (REPO / crafted_path).read_text(encoding="utf-8-sig")
def scenes(markdown):
    return [(m.group(1), re.split(r"\n## ", m.group(2))[0].strip())
            for m in re.finditer(r"^### ([^\n]+)\n(.*?)(?=^### |\Z)", markdown, re.M | re.S)]
fast_scenes = scenes(fast)
crafted_scenes = scenes(crafted)
frozen = []
for id_, index, group in [("S1", 0, "mixed"), ("S2", 1, "rhetoric-control"), ("S3", 3, "repetition")]:
    title, text = fast_scenes[index]
    frozen.append(dict(id=id_, title=title, group=group, source=fast_path, text=text,
                       genre="悬疑", tone="", provenance="real_model_output", originalSha256=digest(text)))
title, text = crafted_scenes[0]
frozen.append(dict(id="S4", title=title, group="rhetoric-control", source=crafted_path,
                   text=text, genre="悬疑", tone="", provenance="real_model_output", originalSha256=digest(text)))
for id_, name, group in [("S5", "workbench-CRAFTED-original.html", "repetition"),
                         ("S6", "results/B1-candidate-CRAFTED.txt", "mixed")]:
    path = "artifacts/h23-quality-20260922/boundary-v4/" + name
    raw = (REPO / path).read_text(encoding="utf-8-sig")
    text = re.sub(r"<[^>]+>", "", raw.replace("</p><p>", "\n\n")).strip()
    frozen.append(dict(id=id_, title="H2 " + id_, group=group, source=path, text=text,
                       genre="当代叙事", tone="", provenance="real_model_output",
                       originalSha256=digest(text)))
save("scenes.json", frozen)

# Labels describe whether intervention is justified, not whether the author used AI.
# Every family contains two positive and two negative cases. Split fixed before detector runs.
rows = [
 ("negative_parallel", True, "这不是勇气，而是坚持。这不是恐惧，而是成长。这不是终点，而是起点。这不是告别，而是开始。这不是选择，而是命运。", "无信息增量的否定转折堆叠。", ""),
 ("negative_parallel", True, "她推开门。并不是风把她带来，是命运替她做了选择。她抬起头。并不是雨让她落泪，是岁月替她诉说。她关上门。并不是夜令她沉默，是时光替她回答。", "同构解释替代现场信息，词形避开既有而是规则。", ""),
 ("negative_parallel", False, "“不是查无此件。”她抬头看程遥，“是这个编码段从去年三月起就冻结了。这封信在邮政系统里没存在过。”", "否定有新证据，应保留；不评价证据是否足以推出最终结论。", "S2"),
 ("negative_parallel", False, "不是门坏了，而是钥匙拿错了。不是停电，而是保险丝断了。不是人走了，而是灯被挡住了。不是纸丢了，而是夹在账本里。不是锁换了，而是门牌认错了。", "五项独立核验，有对应事实；侦探分析腔。", ""),
 ("dialogue_tail", True, "“走吧。”他的语气里带着疲惫。“去哪？”她的语气里带着疑惑。“回家。”他的语气里带着坚定。“现在？”她的语气里带着担忧。“现在。”他的语气里带着决绝。", "每句台词后重复情绪标签。", ""),
 ("dialogue_tail", True, "“等我。”他说，话音里的温柔几乎要溢出来。“不等。”她说，话音里的悲伤几乎要溢出来。“求你。”他说，话音里的痛苦几乎要溢出来。", "同构尾注且情绪与台词重复，检测同义变体覆盖。", ""),
 ("dialogue_tail", False, "“你欠我的。”程遥的声音追上来。\n\n林砚没回头。“我知道。”", "一句关系冲突后的回应，无密集情绪标注。", "S3"),
 ("dialogue_tail", False, "“线别剪。”他说得很轻，怕门外的人听见。她把剪刀放下，改用指甲挑结。", "轻声有避免被听见的动作目的。", ""),
 ("body_action", True, "他嘴角微微上扬，眼神变得坚定。她嘴角微微上扬，眼神变得坚定。老人嘴角微微上扬，眼神变得坚定。守卫嘴角微微上扬，眼神变得坚定。", "无差异的身体反应替代人物反应。", ""),
 ("body_action", True, "他把杯子拿起又放下。她把书拿起又放下。老人把钥匙拿起又放下。守卫把帽子拿起又放下。桌上的东西没有换过地方，谁也没有开口。", "换名词复用同一动作，缺少节奏与信息变化；作为可争议代理标签保留。", ""),
 ("body_action", False, "她拿起杯子，发现杯底压着纸条，便放下杯子去开灯。", "拿放动作改变发现的信息。", ""),
 ("body_action", False, "线在她手里。她还有线。", "救援关键载体的短促确认，单独出现不应判高风险。", "S3"),
 ("imagery", True, "空气仿佛凝固，时间像是停了下来。空气仿佛凝固，时间像是停了下来。空气仿佛凝固，时间像是停了下来。", "泛化氛围反复替代叙事。", ""),
 ("imagery", True, "目光像刀子划开夜色，沉默像刀子划开空气，回忆像刀子划开心口，告别像刀子划开岁月。", "同一抽象比喻框架密集套用。", ""),
 ("imagery", False, "线结收尾那个反方向，像一根细刺扎在指腹上。", "修复职业、线结与触觉相关，单次意象有来源。", "S2"),
 ("imagery", False, "林砚接过信。纸比体温低。", "具体感知，无泛化比喻。", "fast:6"),
 ("abstract_summary", True, "命运的齿轮开始转动。这一刻，他终于明白了成长的意义。命运的齿轮开始转动。这一刻，她终于明白了成长的意义。命运的齿轮开始转动。", "重复抽象总结。", ""),
 ("abstract_summary", True, "他锁好门，所有的等待都有了答案。她收起伞，所有的离别都有了意义。老人放下笔，所有的沉默都有了归宿。", "动作后逐句虚构抽象升华，不提供新信息。", ""),
 ("abstract_summary", False, "她决定今天不去找。至少今天不去。", "落实当前人物决定与暂缓期限，不应消除这个选择。", "S5"),
 ("abstract_summary", False, "代价：相关记忆永久清除。救援结果：成功。", "作品内记录本条目；形式化不等于模型残留。", "fast:6"),
 ("rhythm_repetition", True, "她把书放回去。她把杯放回去。她把笔放回去。她把伞放回去。她把灯放回去。她把刀放回去。", "短句语法槽位机械复用，不要求逐字完全相同。", ""),
 ("rhythm_repetition", True, "他看了一眼门。又看了一眼窗。再看了一眼钟。她看了一眼门。又看了一眼窗。再看了一眼钟。", "重复观察而无变化。", ""),
 ("rhythm_repetition", False, "铜管深处传来闷响。掌心拍管壁，三快两慢，重复两次。", "信号重复具有验证回应的功能。", "S3"),
 ("rhythm_repetition", False, "她敲了三下。门内响两声。她等到灯灭。钥匙才转动。", "短句但每句推进新事件。", ""),
]
samples = []
for n, (family, positive, text, reason, source) in enumerate(rows):
    local = n % 4
    holdout = local == 3 or (n // 4 >= 4 and local == 1)
    sample = dict(id=f"F{n+1:02}", family=family, split="holdout" if holdout else "development",
                  expectedIntervention=positive, labelAuthority="agent_editorial_judgment",
                  provenance="real_model_excerpt" if source else "agent_constructed",
                  source=source or None, text=text, reason=reason, genre="中文小说", tone="",
                  originalSha256=digest(text))
    if n == 3: sample["tone"] = "侦探分析腔；排除法"
    if n == 19: sample["tone"] = "人物记录本，日志文体"
    if source:
        original = fast_scenes[5][1] if source == "fast:6" else next(s["text"] for s in frozen if s["id"] == source)
        assert text in original, (sample["id"], "excerpt must match source")
    samples.append(sample)
assert sum(s["split"] == "holdout" for s in samples) == 8
save("samples.json", samples)
save("freeze.json", dict(baselineCommit="ad3264611a5d04390f50f539738839f508a313c8",
     samplesSha256=digest((OUT / "samples.json").read_text(encoding="utf-8")),
     scenesSha256=digest((OUT / "scenes.json").read_text(encoding="utf-8")),
     labels="agent judgments, not human gold labels", frozenBeforeEvaluation=True,
     paidRunId="slop-20260926-v1", maximumCalls=20, model="deepseek-flash"))
print("Frozen 24 samples (16 development / 8 holdout) and 6 real scenes.")
