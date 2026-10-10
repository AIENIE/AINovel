import { useTranslation } from "react-i18next";
import { evidenceReportSchema, materialAnalysisSchema, type EvidenceTask } from "@/lib/api/domains/material-evidence";
import { Button } from "@/components/ui/button";

type Props = { task: EvidenceTask; confirm?: (index: number) => void; pending?: boolean };
export function EvidenceReportView({ task, confirm, pending }: Props) {
  const { t } = useTranslation();
  const parsed = task.kind === "EXTRACT" ? materialAnalysisSchema.safeParse(task.result) : evidenceReportSchema.safeParse(task.result);
  if (!parsed.success) return <p role="alert" className="text-sm text-destructive">{t("evidence.invalidReport")}</p>;
  const result = parsed.data;
  const analysis = "analysis" in result ? result.analysis : null;
  const findings = "report" in result ? result.report.findings : null;
  const coverage = analysis?.coverage ?? ("report" in result ? result.report.coverage : "");
  const quotes = (items: Array<{ sourceId: string; quote: string; start: number; end: number }>) => items.map((item, index) => {
    const source = result.opened.find(opened => opened.id === item.sourceId);
    return <figure key={`${item.sourceId}:${index}`} className="space-y-1 border-l-2 pl-3">
      <blockquote className="whitespace-pre-wrap break-words text-sm">{item.quote}</blockquote>
      <figcaption className="break-words text-xs text-muted-foreground">
        {t(source?.kind === "BODY" ? "evidence.bodySource" : "evidence.referenceSource")}
        {source?.title && ` · ${source.title}`}
        {" · "}{t("evidence.position", { start: (source?.start ?? 0) + item.start, end: (source?.start ?? 0) + item.end })}
      </figcaption>
      {source && <details><summary className="cursor-pointer text-xs">{t("evidence.openOriginal")}</summary><p className="mt-2 whitespace-pre-wrap break-words text-sm">{source.text}</p></details>}
    </figure>;
  });
  return <div className="mt-3 min-w-0 space-y-4">
    <p className="whitespace-pre-wrap break-words text-sm">{coverage}</p>
    {findings?.map((finding, index) => <article key={index} className="space-y-2 border-t pt-3">
      <p className="text-xs font-medium">{t(`evidence.kind.${finding.kind}`)} · {t(`evidence.decision.${finding.decision}`)}</p>
      <p className="whitespace-pre-wrap break-words text-sm font-medium">{finding.claim}</p>
      {finding.condition && <p className="whitespace-pre-wrap break-words text-sm">{t("evidence.condition")}: {finding.condition}</p>}
      {quotes(finding.evidence)}
    </article>)}
    {analysis?.candidates.map((candidate, index) => <article key={index} className="space-y-2 border-t pt-3">
      <p className="text-sm font-medium">{t(`evidence.annotation.${candidate.type}`)} · {candidate.name}</p>
      {!!candidate.aliases.length && <p className="break-words text-sm">{t("evidence.aliases")}: {candidate.aliases.join("、")}</p>}
      {candidate.statement && <p className="whitespace-pre-wrap break-words text-sm">{candidate.statement}</p>}
      {candidate.condition && <p className="whitespace-pre-wrap break-words text-sm">{t("evidence.condition")}: {candidate.condition}</p>}
      {quotes(candidate.evidence)}
      {confirm && <Button size="sm" variant="outline" disabled={pending || task.stale || task.status !== "COMPLETED"} onClick={() => confirm(index)}>{t("evidence.confirmCandidate")}</Button>}
    </article>)}
    {!!result.exclusions.length && <ul className="list-disc space-y-1 pl-4 text-xs text-muted-foreground">{result.exclusions.map((text, index) => <li key={index} className="break-words">{text}</li>)}</ul>}
  </div>;
}
