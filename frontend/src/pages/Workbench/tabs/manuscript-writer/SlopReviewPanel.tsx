import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { localizedErrorMessage } from "@/lib/error-messages";
import { Button } from "@/components/ui/button";
import type { Manuscript, SlopQualityRun } from "@/types";
import type { LanguagePatch } from "@/lib/api/domains/language-quality";
import { LanguagePatchReview } from "./LanguageQualityPanel";

type Props = { manuscript?: Manuscript | null; sceneId: string; content: string; dirty: boolean; active: boolean; busy: boolean;
  run: SlopQualityRun | null; onApplied: (manuscript: Manuscript, scene: string) => void };
const judgments = { CONFIRMED: "语义确认", DISMISSED: "已排除", UNCERTAIN: "证据不足" };
const kinds = { LANGUAGE: "语言问题", OUTPUT_ARTIFACT: "输出残留", STYLE: "文风建议", OBSERVATION: "观察" };
export function SlopReviewPanel({ manuscript, sceneId, content, dirty, active, busy, run: initial, onApplied }: Props) {
  const client = useQueryClient(); const [working, setWorking] = useState(false); const [error, setError] = useState("");
  const id = manuscript?.id || ""; const branch = manuscript?.currentBranchId || null; const version = manuscript?.version ?? 0;
  const identity = `${id}:${branch}:${sceneId}:${version}`; const latest = useRef({ identity, content, dirty }); latest.current = { identity, content, dirty };
  const mounted = useRef(true); useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const actionKeys = useRef(new Map<string, string>());
  const query = useQuery({ queryKey: ["slop-review", id, initial?.id], enabled: active && !!id && !!initial,
    queryFn: async () => (await api.v2.quality.listRuns(id, sceneId)).find(r => r.id === initial?.id) || initial, retry: false });
  const run = query.data || initial; const review = run?.review;
  const settings = useQuery({ queryKey: ["language-settings", id], queryFn: () => api.language.settings(id), enabled: active && !!id, retry: false });
  const candidates = useQuery({ queryKey: ["slop-candidates", id, review?.languageReportId, version], enabled: active && !!review?.languageReportId,
    queryFn: () => api.language.report(id, review!.languageReportId!), retry: false,
    refetchInterval: q => q.state.data?.patches.some(p => p.status === "GENERATING") ? 2000 : false });
  const report = candidates.data;
  const sourceMatches = !!review?.source && review.source.branchId === branch && review.source.bodyVersion === version;
  const canContinue = !dirty && !!settings.data?.available && (report ? report.canContinueBatch : sourceMatches);
  const execute = async (action: () => Promise<void>) => {
    setWorking(true); setError("");
    try { await action(); } catch (e) { if (mounted.current && latest.current.identity === identity) setError(localizedErrorMessage(e)); }
    finally { await Promise.all([client.invalidateQueries({ queryKey: ["slop-review", id] }), client.invalidateQueries({ queryKey: ["slop-candidates", id] })]); if (mounted.current) setWorking(false); }
  };
  const decide = (patch: LanguagePatch, action: "accept" | "reject" | "undo") => execute(async () => {
    if (!manuscript || !report || dirty) return;
    const intent = `${patch.id}:${action}:${identity}`; const key = actionKeys.current.get(intent) || crypto.randomUUID(); actionKeys.current.set(intent, key);
    const result = await api.language.decide(id, report.id, patch.id, action, { expectedBranchId: branch, expectedVersion: version }, key);
    if (action === "reject") return;
    if (!mounted.current || latest.current.identity !== identity || latest.current.content !== content || latest.current.dirty) {
      setError("服务器已保存处置；本地正文已变化，请在版本面板核对，未覆盖本地草稿。"); return;
    }
    onApplied({ ...manuscript, version: result.bodyVersion, currentBranchId: result.branchId, sections: { ...manuscript.sections, [sceneId]: result.content } }, sceneId);
  });
  if (!run) return null;
  if (!review) return <p className="rounded border p-3 text-xs">历史 Slop 报告缺少可靠来源快照。证据可读；如需生成候选，请基于当前正文重新诊断。</p>;
  return <section aria-label="Slop 证据与逐项修订" className="min-w-0 space-y-3 rounded border p-3 text-xs break-words">
    <h3 className="font-semibold">Slop 证据与逐项修订</h3>
    <p role="status">{review.reason}</p>
    <p>{review.coverage === "COMPLETE" ? "完整正文已检查" : "正文未完整检查，不能视为通过"} · {review.promptVersion}</p>
    {review.source && <p className="text-muted-foreground">正文版本 {review.source.bodyVersion} · {review.source.projectionVersion}</p>}
    {(!sourceMatches || dirty) && <p className="text-amber-700">来源正文或分支已变化。{report?.canContinueBatch && !dirty ? "同批次未受影响的问题仍可逐项处理。" : "旧报告不能生成或应用候选，请重新诊断。"}</p>}
    {!settings.data?.available && <p>本环境语言候选功能未启用，报告仍可阅读。</p>}
    {(error || query.error || candidates.error) && <p role="alert" className="text-destructive">{error || localizedErrorMessage(query.error || candidates.error)}</p>}
    <details><summary>本地命中线索（{review.localSignals.length} 条，线索分 {review.localRisk}）</summary><p>这些命中没有直接计入确认问题风险。</p>
      {review.localSignals.map((signal, i) => <p key={i} className="mt-2 whitespace-pre-wrap">{signal.quote || signal.evidence} · {signal.patternId}</p>)}</details>
    {review.findings.map(finding => {
      const patch = report?.patches.find(p => p.issueId === finding.id);
      const currentIssue = report?.issues.find(i => i.id === finding.id);
      const eligible = finding.judgment === "CONFIRMED" && finding.kind === "LANGUAGE" && !!finding.category && finding.location.replaceable;
      return <article key={finding.id} className="space-y-2 rounded border p-2">
        <p className="font-medium">{judgments[finding.judgment]} · {kinds[finding.kind]}</p>
        <blockquote className="whitespace-pre-wrap border-l-2 pl-2">{finding.quote}</blockquote>
        <p>{finding.explanation}</p><p className="text-muted-foreground">{finding.advice}</p>
        <p>{finding.location.start === null ? "引文缺失或有多处匹配，不能定位或替换。" : !finding.location.replaceable ? "引文已核对，富文本无法安全映射；仅供手动参考。" : `${finding.location.trust === "RELOCATED" ? "按唯一引文重定位" : "坐标与引文一致"}：${finding.location.start}–${finding.location.end}（UTF-16）`}</p>
        {eligible && !patch && <Button size="sm" variant="outline" disabled={working || busy || !canContinue || (report && currentIssue?.availability !== "AVAILABLE")} onClick={() => void execute(async () => { await runTrackedAiOperation(api.language.slopSuggest(id, run.id, finding.id)); })}>生成局部候选</Button>}
        {patch && <LanguagePatchReview patch={patch} disabled={working || busy} dirty={dirty} onDecide={action => void decide(patch, action)} />}
      </article>;
    })}
    {!review.findings.length && <p>没有可用语义问题；本地线索不会自动变成修改建议。</p>}
    <Button size="sm" variant="ghost" disabled={working} onClick={() => void execute(async () => {})}>刷新报告与候选</Button>
  </section>;
}
