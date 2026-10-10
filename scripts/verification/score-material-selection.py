"""Score saved rankings, without any network or supplier calls.

Input: {configuration: <manifest configuration>, datasetSha256: <manifest sha256>,
        rows: [{queryId, arm, chunks: [stable chunk id], elapsedMs, failure?}]}.
This scorer cannot certify that rankings came from a supplier; retain the gateway ledger separately.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from statistics import mean

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "backend/src/test/resources/material-selection-20261003"

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

def fragments(source):
    text, start, number = source["text"], 0, 0
    while start < len(text):
        end = min(len(text), start + 900)
        yield source["id"] + ":" + str(number), (source["id"], start, end)
        if end == len(text):
            break
        start, number = end - 120, number + 1

def score(rankings, split):
    manifest = load(DATA / "manifest.json")
    for name, digest in manifest["sha256"].items():
        if hashlib.sha256((DATA / name).read_bytes()).hexdigest() != digest:
            raise ValueError("Frozen dataset hash mismatch: " + name)
    if rankings.get("configuration") != manifest["configuration"] or rankings.get("datasetSha256") != manifest["sha256"]:
        raise ValueError("Rankings do not match the frozen configuration and dataset")
    queries = load(DATA / (split + "-queries.json"))
    # Holdout answers are opened only after the configuration and dataset have been checked.
    answers = {answer["id"]: answer for answer in load(DATA / (split + "-answers.json"))}
    corpus = load(DATA / "corpus.json")
    locations = dict(fragment for source in corpus for fragment in fragments(source))
    visible = {source["id"] for source in corpus if source["visibility"] == "CURRENT"}
    arms = manifest["configuration"]["arms"]
    rows = {}
    for row in rankings["rows"]:
        key = (row["queryId"], row["arm"])
        if key in rows or row["arm"] not in arms:
            raise ValueError("Duplicate or unknown evaluation row")
        if len(row.get("chunks", [])) > 40 or len(row.get("chunks", [])) != len(set(row.get("chunks", []))):
            raise ValueError("Invalid candidate list")
        if any(chunk not in locations for chunk in row.get("chunks", [])):
            raise ValueError("Unknown source position")
        rows[key] = row
    results, details = {}, {}
    for arm in arms:
        recalls, ndcgs, irrelevant, latencies, missing, failures, leakage = [], [], [], [], 0, 0, 0
        for query in queries:
            row = rows.get((query["id"], arm))
            if row is None:
                missing += 1
                continue
            failures += bool(row.get("failure"))
            ranked = row.get("chunks", [])[:8]
            leakage += sum(locations[chunk][0] not in visible for chunk in row.get("chunks", []))
            answer = answers[query["id"]]
            required = answer["evidence"]
            found = sum(any(locations[chunk][0] == evidence["source"] and locations[chunk][1] <= evidence["start"] and locations[chunk][2] >= evidence["end"] for chunk in ranked) for evidence in required)
            recall = found / len(required) if required else None
            grades = answer["relevance"]
            dcg = sum((2 ** grades.get(chunk, 0) - 1) / math.log2(index + 2) for index, chunk in enumerate(ranked))
            ideal = sum((2 ** grade - 1) / math.log2(index + 2) for index, grade in enumerate(sorted(grades.values(), reverse=True)[:8]))
            ndcg = dcg / ideal if ideal else None
            if query["mode"] == "fact" and required:
                recalls.append(recall)
                ndcgs.append(ndcg)
            if query["mode"] == "fact" and not required:
                irrelevant.append(len(ranked))
            if isinstance(row.get("elapsedMs"), (int, float)) and row["elapsedMs"] >= 0:
                latencies.append(row["elapsedMs"])
            details[(arm, query["id"])] = {"recall": recall, "nDCG": ndcg, "leakage": sum(locations[chunk][0] not in visible for chunk in ranked)}
        executed = len(queries) - missing
        results[arm] = {"evidenceRecallAt8": mean(recalls) if recalls else None, "nDCGAt8": mean(ndcgs) if ndcgs else None, "permissionOrVersionLeaks": leakage, "missingQueries": missing, "executedQueries": executed, "failedQueries": failures, "failureRate": failures / executed if executed else None, "meanElapsedMs": mean(latencies) if latencies else None, "meanUnrelatedResultsOnUnanswerableQueries": mean(irrelevant) if irrelevant else None, "inspiration": "Proxy observations only; anonymous author ratings not collected"}
    selected, reason, rerank = "basic", "Selection requires complete held-out supplier results and a reconciled gateway ledger", False
    if split == "holdout" and not any(result["missingQueries"] for result in results.values()):
        eligible = lambda arm: results[arm]["permissionOrVersionLeaks"] == 0 and results[arm]["evidenceRecallAt8"] >= .9 and not results[arm]["failedQueries"]
        standard, flash = results["standard"], results["flash"]
        critical = [query for query in queries if query["group"] in {"entity_alias", "fictional_term", "negation_condition", "cross_paragraph", "version_permission"} and answers[query["id"]]["evidence"]]
        flash_regression = any(details[("flash", query["id"])]["recall"] < details[("standard", query["id"])]["recall"] for query in critical)
        if eligible("flash") and abs(flash["evidenceRecallAt8"] - standard["evidenceRecallAt8"]) <= .03 and abs(flash["nDCGAt8"] - standard["nDCGAt8"]) <= .03 and not flash_regression:
            selected, reason = "flash", "Flash meets the engineering evidence thresholds without critical-case regression"
        elif eligible("standard"):
            selected, reason = "standard", "Standard meets the engineering evidence thresholds; Flash preference conditions did not pass"
        if selected != "basic":
            reranked = results[selected + "-rerank"]
            rerank = eligible(selected + "-rerank") and reranked["nDCGAt8"] - results[selected]["nDCGAt8"] >= .03 and reranked["evidenceRecallAt8"] >= results[selected]["evidenceRecallAt8"]
        if results["basic"]["permissionOrVersionLeaks"]:
            selected, reason, rerank = "BLOCKED", "Base retrieval leaked permission or version data", False
    return {"dataset": manifest["version"], "split": split, "queries": len(queries), "metrics": results, "engineeringRecommendation": {"embedding": selected, "rerank": rerank, "reason": reason}, "limitations": ["Synthetic fixtures do not establish overall novel-writing quality", "This offline scorer does not independently prove supplier origin or costs", "Anonymous author ratings remain required for inspiration quality"]}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("rankings", type=Path)
    parser.add_argument("--split", choices=["development", "holdout"], required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = score(load(args.rankings), args.split)
    if args.output.exists():
        raise SystemExit("Keep existing evidence; choose a new output path")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output.resolve()), "recommendation": result["engineeringRecommendation"]}, ensure_ascii=False))
