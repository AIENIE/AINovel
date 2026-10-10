import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { localizedErrorMessage } from "@/lib/error-messages";
import { materialTextDiff } from "@/lib/material-text-diff";
import type { LanguageIssue, LanguagePatch, LanguageReport } from "@/lib/api/domains/language-quality";
import type { Manuscript } from "@/types";

const languageStatusLabel: Record<LanguageReport["status"], string> = {
  UNCHECKED: "未检查", CHECKING: "检查中", ISSUES: "发现问题或建议", NO_CLEAR_ISSUES: "未发现明确问题",
  INCOMPLETE: "检查不完整", FAILED: "检查失败", LOCAL_ONLY: "仅本地规则", STALE: "报告已过期",
};
const kinds = { LANGUAGE: "明确语言问题", STYLE: "文风建议", OBSERVATION: "证据不足的观察" };
const categories = { OMISSION: "过度省略", COMPRESSION: "表达压缩", CHOPPY: "刻意断句", AWKWARD: "生硬搭配／抽象化", PARAGRAPH: "整段生硬" };
const verdicts = { PASS: "模型复核通过，仍需作者判断", UNCERTAIN: "不确定，请核对", FAIL: "未通过" };
const patchStates: Record<string, string> = { GENERATING: "正在生成或复核", READY: "待作者决定", ACCEPTED: "已采纳", REJECTED: "已拒绝", UNDONE: "已撤销", FAILED: "建议未完成", NEEDS_CONTEXT: "缺少必要信息，未生成修改建议" };
class LanguageReviewNotice extends Error {}
type Props = {
  manuscript?: Manuscript | null; sceneId: string; content: string; dirty: boolean; active: boolean; busy: boolean;
  save: (sceneId: string, html: string, silent?: boolean) => Promise<void>;
  onApplied: (manuscript: Manuscript, sceneId: string) => void;
  onSourceResolved?: (manuscript: Manuscript) => void;
};
export function LanguageQualityPanel({ manuscript, sceneId, content, dirty, active, busy, save, onApplied, onSourceResolved }: Props) {
  const client = useQueryClient(); const [working, setWorking] = useState(false); const [error, setError] = useState("");
  const id = manuscript?.id || ""; const branch = manuscript?.currentBranchId || null; const version = manuscript?.version ?? 0;
  const identity = `${id}:${branch}:${sceneId}:${version}`;
  const scope = `${id}:${branch}:${sceneId}`;
  const latest = useRef({ identity, scope, content, dirty }); latest.current = { identity, scope, content, dirty };
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const actionKeys = useRef(new Map<string, string>());
  const settings = useQuery({ queryKey: ["language-settings", id], queryFn: () => api.language.settings(id), enabled: active && !!id, retry: false });
  const reports = useQuery({
    queryKey: ["language-reports", id, branch, sceneId, version],
    queryFn: ({ signal }) => api.language.reports(id, sceneId, signal), enabled: active && !!id && !!sceneId, retry: false, staleTime: 0,
    refetchInterval: query => query.state.data?.some(r => r.status === "CHECKING" || r.patches.some(p => p.status === "GENERATING")) ? 2000 : false,
  });
  const report = reports.data?.[0];
  const versionMismatch = report && (report.source.branchId !== branch || report.source.bodyVersion !== version);
  const stale = dirty || (versionMismatch && !report?.canContinueBatch) || report?.status === "STALE";
  const canContinue = !dirty && !!report?.canContinueBatch && report.source.branchId === branch;
  const status = stale ? "STALE" : report?.status ?? "UNCHECKED";
  const disabled = working || busy;
  const refresh = () => client.invalidateQueries({ queryKey: ["language-reports", id] });
  const execute = async (action: () => Promise<void>) => {
    setWorking(true); setError("");
    try { await action(); } catch (e) { if (mounted.current && latest.current.identity === identity) setError(e instanceof LanguageReviewNotice ? e.message : localizedErrorMessage(e)); }
    finally { await refresh(); if (mounted.current) setWorking(false); }
  };
  const check = () => execute(async () => {
    if (dirty) await save(sceneId, content, true);
    const current = await api.manuscripts.get(id);
    if (!mounted.current || latest.current.scope !== scope || (current.currentBranchId || null) !== branch || latest.current.content !== content) throw new LanguageReviewNotice("正文或分支已变化，请保存后重新检查。");
    const accepted = await api.language.check(id, sceneId, { expectedBranchId: current.currentBranchId || null, expectedVersion: current.version });
    const source = await api.manuscripts.get(id);
    if (mounted.current && latest.current.scope === scope && latest.current.content === content && !latest.current.dirty) onSourceResolved?.(source);
    await runTrackedAiOperation(Promise.resolve(accepted));
  });
  const suggest = (issue: LanguageIssue) => execute(async () => {
    if (!report || !canContinue) return;
    await runTrackedAiOperation(api.language.suggest(id, report.id, issue.id));
  });
  const decide = (patch: LanguagePatch, action: "accept" | "reject" | "undo") => execute(async () => {
    if (!manuscript || !report || dirty) return;
    const intent = `${patch.id}:${action}:${identity}`;
    const key = actionKeys.current.get(intent) || crypto.randomUUID(); actionKeys.current.set(intent, key);
    const result = await api.language.decide(id, report.id, patch.id, action, { expectedBranchId: branch, expectedVersion: version }, key);
    if (action === "reject") return;
    if (!mounted.current || latest.current.identity !== identity || latest.current.content !== content || latest.current.dirty) {
      throw new LanguageReviewNotice("服务器已保存本次处置；期间本地正文发生变化，未覆盖本地草稿。请在版本面板核对差异。");
    }
    onApplied({ ...manuscript, version: result.bodyVersion, currentBranchId: result.branchId, sections: { ...manuscript.sections, [sceneId]: result.content } }, sceneId);
  });
  return <section aria-label="语言检查" className="min-w-0 space-y-3 rounded-md border border-primary/30 p-3 text-xs break-words">
    <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-semibold">语言检查</h3><span role="status">{languageStatusLabel[status]}</span></div>
    {settings.data && <div className="space-y-2">
      <label className="flex items-start gap-2"><input type="checkbox" checked={settings.data.generationStandard} disabled={disabled || !settings.data.available} onChange={event => void execute(async () => {
        await api.language.saveSettings(id, { generationStandard: event.target.checked, checkAfterGeneration: settings.data!.checkAfterGeneration }); await client.invalidateQueries({ queryKey: ["language-settings", id] });
      })} />生成正文时使用自然度规范</label>
      <label className="flex items-start gap-2"><input type="checkbox" checked={settings.data.checkAfterGeneration} disabled={disabled || !settings.data.available} onChange={event => void execute(async () => {
        await api.language.saveSettings(id, { generationStandard: settings.data!.generationStandard, checkAfterGeneration: event.target.checked }); await client.invalidateQueries({ queryKey: ["language-settings", id] });
      })} />生成后检查语言（后台检查，会使用模型额度）</label>
      {!settings.data.available && <p className="text-muted-foreground">本环境尚未启用语言检查，历史结果仍可查看。</p>}
    </div>}
    <div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" disabled={disabled || !settings.data?.available || !sceneId} onClick={() => void check()}>{dirty ? "保存并检查语言" : "检查语言"}</Button><Button size="sm" variant="ghost" disabled={working} onClick={() => void refresh()}>刷新结果</Button></div>
    {(error || reports.error || settings.error) && <p role="alert" className="text-destructive">{error || localizedErrorMessage(reports.error || settings.error)}</p>}
    {dirty && <p className="text-amber-700">正文有未保存修改，旧报告已失效，采纳与撤销暂不可用。</p>}
    {report && <>
      <p>{report.summary}</p>
      <p className="text-muted-foreground">{report.source.standardVersion} · 正文版本 {report.source.bodyVersion} · {new Date(report.createdAt).toLocaleString()}</p>
      {stale && <p className="text-amber-700">这是历史检查结论，不能证明当前正文通过。{canContinue ? "同批次未受影响的建议仍可逐项处理。" : "如需检查当前正文，请主动发起检查。"}</p>}
      <details><summary className="cursor-pointer">检查覆盖（{report.coverage.filter(c => c.state === "COMPLETE").length}/{report.coverage.length} 块完整）</summary><ul className="mt-2 space-y-1">{report.coverage.map(c => <li key={c.index}>{c.start}–{c.end}：{({ COMPLETE: "完成", PARTIAL: "部分结果", FAILED: "失败", SKIPPED: "未检查", PENDING: "等待检查" } as Record<string, string>)[c.state] || c.state} {c.reason}</li>)}</ul></details>
      {(["LANGUAGE", "STYLE", "OBSERVATION"] as const).map(kind => <div key={kind} className="space-y-2">
        <h4 className="font-medium">{kinds[kind]}（{report.issues.filter(i => i.kind === kind).length}）</h4>
        {report.issues.filter(i => i.kind === kind).map(issue => {
          const patch = report.patches.find(p => p.issueId === issue.id);
          return <article key={issue.id} className="space-y-2 rounded border p-2">
            <p className="font-medium">{categories[issue.category]}</p>
            <blockquote className="whitespace-pre-wrap border-l-2 pl-2">{issue.quote}</blockquote>
            {issue.location !== "EXACT" && <p className="text-amber-700">引文位置未验证，不能据此高亮或一键替换。</p>}
            <p>{issue.impact}</p><p className="text-muted-foreground">修改方向：{issue.direction}</p>
            <details><summary className="cursor-pointer">完整原文段落</summary><p className="mt-2 whitespace-pre-wrap leading-relaxed">{issue.context}</p></details>
            {!patch && <Button size="sm" variant="outline" disabled={disabled || !canContinue || issue.availability !== "AVAILABLE" || issue.location !== "EXACT"} onClick={() => void suggest(issue)}>生成修改建议</Button>}
            {patch && <div className="space-y-2 border-t pt-2">
              <p>{patchStates[patch.status] || patch.status}</p>
              {patch.reason && <p>{patch.reason}</p>}
              {patch.status === "NEEDS_CONTEXT" && <p className="text-muted-foreground">未生成候选，因此没有继续调用模型复核。请补充或编辑正文后主动重新检查。</p>}
              {patch.replacement && <>
                <p className="font-medium">完整差异（删除／新增）</p><div className="whitespace-pre-wrap leading-relaxed">{materialTextDiff(patch.original, patch.replacement).map((part, i) => part.kind === "removed" ? <del className="bg-red-50 text-red-800" key={i}>{part.text}</del> : part.kind === "added" ? <ins className="bg-green-50 text-green-800" key={i}>{part.text}</ins> : <span key={i}>{part.text}</span>)}</div>
                <details><summary className="cursor-pointer">修改前后完整段落与相邻上下文</summary><p className="mt-2 font-medium">修改前</p><p className="whitespace-pre-wrap leading-relaxed">{patch.beforeContext}</p><p className="mt-2 font-medium">修改后</p><p className="whitespace-pre-wrap leading-relaxed">{patch.afterContext}</p></details>
              </>}
              {patch.review && <div className="space-y-1"><p>语言效果：{verdicts[patch.review.language]}。{patch.review.languageReason}</p><p>原意保留：{verdicts[patch.review.meaning]}。{patch.review.meaningReason}</p><ul>{patch.review.changes.map((change, i) => <li key={i}>{change}</li>)}</ul></div>}
              {!(["APPLICABLE", "UNCERTAIN", "PENDING", "NEEDS_CONTEXT"] as string[]).includes(patch.applicability) && <p className="text-amber-700">{patch.applicability === "MANUAL_ONLY" ? "富文本无法可靠映射，请参照差异手动编辑。" : patch.applicability === "STALE" ? "正文或相邻上下文已变化，此建议已失效。" : "候选或复核不满足一键采纳条件，请保留原文并核对差异。"}</p>}
              <div className="flex flex-wrap gap-2">
                {patch.status === "READY" && <Button size="sm" disabled={disabled || dirty || !["APPLICABLE", "UNCERTAIN"].includes(patch.applicability)} onClick={() => void decide(patch, "accept")}>{patch.applicability === "UNCERTAIN" ? "已核对，仍采纳" : "采纳此项"}</Button>}
                {["READY", "FAILED", "NEEDS_CONTEXT"].includes(patch.status) && <Button size="sm" variant="outline" disabled={disabled || dirty} onClick={() => void decide(patch, "reject")}>拒绝此项</Button>}
                {patch.status === "ACCEPTED" && <Button size="sm" variant="outline" disabled={disabled || dirty || patch.applicability === "STALE"} onClick={() => void decide(patch, "undo")}>撤销此项</Button>}
              </div>
              {patch.appliedSnapshotId && <p className="text-muted-foreground">采纳已保存为独立版本{patch.undoneSnapshotId ? "；撤销也已保存新版本" : ""}。</p>}
            </div>}
          </article>;
        })}
      </div>)}
    </>}
    {!report && !reports.isPending && !reports.error && <p className="text-muted-foreground">尚无语言报告。本地规则零命中也不代表语言自然；检查不会自动生成修改建议。</p>}
  </section>;
}
