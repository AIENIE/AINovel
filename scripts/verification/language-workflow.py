"""Local author-action acceptance against saved real candidates.

Only the fixture manuscripts created by language-session.py are eligible. Applying
these disposable test candidates exercises the API and is not author quality approval.
The core workflow makes no new inference; the explicit edges probe may queue one
diagnosis before cancelling it, within the same approved global budget.
"""
import importlib.util
import time
import uuid
from pathlib import Path


def run(api, read, save, command):
    if command.get("mode") == "edges":
        return edges(api, read, save)
    state = read("local-fixtures.json")
    name = command["name"]
    stem = name.replace(":", "-")
    mid = state["manuscripts"][name]
    scene = state["scenes"][name]
    report = read("diagnosis-" + name.replace(":", "-") + ".json")
    assert report["manuscriptId"] == mid
    base = "/v2/manuscripts/" + mid
    path = base + "/quality-runs/language/" + report["id"]
    evidence = {"fixture": name, "reportId": report["id"], "checks": [], "modelCalls": 0}

    def record(label, value):
        evidence["checks"].append({"name": label, "result": value})
        save("workflow-" + stem + ".json", evidence)

    def current():
        return api("/v1/manuscripts/" + mid)

    def expected(m):
        return {"expectedBranchId": m["currentBranchId"], "expectedVersion": m["version"]}

    def decide(patch, action, m, key, status=200):
        return api(path + "/patches/" + patch["id"] + "/" + action,
                   expected(m), key="language-l4-" + name + "-" + key, expected=status)

    before = current()
    assert before["title"].startswith("语言验收")
    original = before["sections"][scene]
    save("workflow-source-" + stem + ".json", {"manuscript": before, "report": report})
    check = api(base + "/scenes/" + scene + "/quality-runs/language/operations", expected(before), expected=202)
    assert check["operationId"] == report["operationId"]
    record("same-version diagnosis reuses operation", check)
    patches = {p["issueId"]: p for p in report["patches"]}
    first, second, rejected = [patches[i] for i in command["issues"]]
    for p in (first, second, rejected):
        assert p["status"] == "READY"
        repeated = api(path + "/issues/" + p["issueId"] + "/suggestions/operations", {}, expected=202)
        assert repeated["operationId"] == p["operationId"]
    record("candidate requests recover existing operations", True)
    paragraphs = {i["id"]: i["paragraph"] for i in report["issues"]}
    assert abs(paragraphs[first["issueId"]] - paragraphs[second["issueId"]]) >= 2
    assert first["applicability"] in ("APPLICABLE", "UNCERTAIN")
    assert second["applicability"] in ("APPLICABLE", "UNCERTAIN")

    wrong_branch = {**before, "currentBranchId": str(uuid.uuid4())}
    record("wrong branch refused", decide(first, "accept", wrong_branch, "wrong-branch", 409))
    wrong_version = {**before, "version": before["version"] - 1}
    record("concurrent version refused", decide(first, "accept", wrong_version, "wrong-version", 409))
    other = next(v for v in state["manuscripts"].values() if v != mid)
    cross = "/v2/manuscripts/" + other + "/quality-runs/language/" + report["id"] + "/patches/" + first["id"]
    record("cross-manuscript patch refused", api(cross, expected=404))
    assert current()["sections"][scene] == original

    accepted_first = decide(first, "accept", before, "accept-first")
    assert accepted_first["bodyVersion"] == before["version"] + 1
    assert accepted_first["content"] != original
    assert decide(first, "accept", before, "accept-first") == accepted_first
    record("accept atomically saves a new version and same-key replay", accepted_first)
    record("old version cannot accept twice", decide(first, "accept", before, "accept-again", 409))
    refreshed = api(path)
    assert refreshed["status"] == "STALE" and refreshed["canContinueBatch"]
    second_now = next(p for p in refreshed["patches"] if p["id"] == second["id"])
    assert second_now["applicability"] in ("APPLICABLE", "UNCERTAIN")
    assert next(i for i in refreshed["issues"] if i["id"] == first["issueId"])["availability"] == "STALE"
    record("historical report stale, unaffected candidate rebased", refreshed)

    accepted_second = decide(second, "accept", current(), "accept-second")
    assert accepted_second["bodyVersion"] == accepted_first["bodyVersion"] + 1
    assert accepted_second["patch"]["appliedSnapshotId"] != accepted_first["patch"]["appliedSnapshotId"]
    record("continuous accept saves a distinct version", accepted_second)
    rejected_result = decide(rejected, "reject", current(), "reject-third")
    assert rejected_result["bodyVersion"] == accepted_second["bodyVersion"]
    assert rejected_result["content"] == accepted_second["content"]
    record("reject preserves text and records disposition", rejected_result)

    undone_second = decide(second, "undo", current(), "undo-second")
    undone_first = decide(first, "undo", current(), "undo-first")
    assert undone_second["bodyVersion"] == accepted_second["bodyVersion"] + 1
    assert undone_first["bodyVersion"] == undone_second["bodyVersion"] + 1
    assert undone_first["content"] == original
    ids = [accepted_first["patch"]["appliedSnapshotId"], accepted_second["patch"]["appliedSnapshotId"],
           undone_second["patch"]["undoneSnapshotId"], undone_first["patch"]["undoneSnapshotId"]]
    assert len(set(ids)) == 4
    record("two reverse edits restore exact HTML with four unique snapshots", {"snapshotIds": ids, "version": undone_first["bodyVersion"]})

    m = current()
    edited = api("/v1/manuscripts/" + mid + "/sections/" + scene,
                 {"content": original + "<p>本地验收临时补充。</p>", **expected(m)}, "PUT")
    stale = api(path)
    assert stale["status"] == "STALE" and not stale["canContinueBatch"]
    assert all(p["applicability"] == "STALE" for p in stale["patches"])
    record("ordinary edit invalidates report and every saved patch", {"version": edited["version"], "report": stale})

    branch = api(base + "/branches", {"name": "语言验收分支", "description": "仅本地验证来源绑定", "sourceVersionId": report["source"]["snapshotId"]})
    api(base + "/branches/" + branch["id"] + "/checkout", {})
    stale_branch = api(path)
    assert stale_branch["status"] == "STALE" and not stale_branch["canContinueBatch"]
    record("checkout invalidates old report", {"branch": branch["id"], "status": stale_branch["status"]})
    api(base + "/branches/" + before["currentBranchId"] + "/checkout", {})
    m = current()
    api("/v1/manuscripts/" + mid + "/sections/" + scene, {"content": original, **expected(m)}, "PUT")
    assert current()["sections"][scene] == original
    record("refresh restores persisted history; fixture text restored", api(path))
    return {"workflow": name, "passedChecks": len(evidence["checks"]), "modelCalls": 0}


def edges(api, read, save):
    """One cancellation probe may start at most one call; all other checks are offline/API only."""
    spec = importlib.util.spec_from_file_location("acceptance", Path(__file__).with_name("language-acceptance.py"))
    acceptance = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(acceptance)

    def used():
        with acceptance.root_connection("ainovel") as db:
            with db.cursor() as q:
                q.execute("SELECT used FROM ai_validation_budgets WHERE id=%s", (acceptance.RUN,))
                return q.fetchone()["used"]

    before_calls = used()
    assert before_calls <= 39
    state = read("local-fixtures.json")
    mid = state["manuscripts"]["normal"]
    scene = state["scenes"]["normal"]
    mpath = "/v1/manuscripts/" + mid
    reports_path = "/v2/manuscripts/" + mid + "/quality-runs/language?sceneId=" + scene
    check_path = "/v2/manuscripts/" + mid + "/scenes/" + scene + "/quality-runs/language/operations"
    original = api(mpath)["sections"][scene]
    evidence = {"callsBefore": before_calls, "checks": []}

    def record(label, result):
        evidence["checks"].append({"name": label, "result": result})
        save("workflow-edges.json", evidence)

    def expected(m):
        return {"expectedBranchId": m["currentBranchId"], "expectedVersion": m["version"]}

    def edit(content):
        return api(mpath + "/sections/" + scene, {"content": content, **expected(api(mpath))}, "PUT")

    def wait(op):
        for _ in range(100):
            current = api("/v1/ai-operations/" + op)
            if current["status"] not in ("QUEUED", "RUNNING", "STREAMING"):
                return current
            time.sleep(0.2)
        raise TimeoutError("Inspect existing operation; do not repeat this workflow")

    edit("<p>" + "这是一段用于输入预算边界验收的文字。" * 1000 + "</p>")
    op = api(check_path, expected(api(mpath)), expected=202)
    completed = wait(op["operationId"])
    report = api(reports_path)[0]
    assert completed["status"] == "SUCCEEDED"
    assert report["status"] == "INCOMPLETE" and all(c["state"] == "SKIPPED" for c in report["coverage"])
    assert used() == before_calls
    record("oversized indivisible paragraph explicitly uncovered, no model call", report)

    edit(original + "<p>她把纸条收进抽屉，关好窗户。</p>")
    op = api(check_path, expected(api(mpath)), expected=202)
    cancelled = api("/v1/ai-operations/" + op["operationId"] + "/cancel", {})
    completed = wait(op["operationId"])
    report = api(reports_path)[0]
    assert completed["status"] == "CANCELLED"
    assert report["status"] in ("FAILED", "INCOMPLETE")
    record("cancelled check cannot display no-clear-issues", {"cancel": cancelled, "operation": completed, "report": report})
    edit(original)

    # This exact call is already fenced by a persisted reconciliation reservation.
    # Replaying it verifies the fence, never releases/changes its reservation or key.
    key = "language-20261007:G1-fast-new"
    with acceptance.root_connection("ainovel") as db:
        with db.cursor() as q:
            q.execute("SELECT status FROM ai_credit_reservations WHERE idempotency_key=%s", (key,))
            reservations = q.fetchall()
    assert len(reservations) == 1 and reservations[0]["status"] == "RECONCILIATION_REQUIRED"
    count = used()
    request = next(p["request"] for p in read("frozen-prompts-v2.json")["items"] if p["id"] == "G1-fast-new")
    response = api("/v1/ai/chat", request, key=key, expected=400)
    assert response["message"] == "AI_RESULT_RECONCILIATION_REQUIRED" and used() == count
    record("unknown result replay requests reconciliation without a new inference", response)
    evidence["callsAfter"] = used()
    assert evidence["callsAfter"] <= before_calls + 1
    save("workflow-edges.json", evidence)
    return {"workflow": "edges", "passedChecks": len(evidence["checks"]), "newModelCalls": evidence["callsAfter"] - before_calls}
