import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { assistantTextDiff } from "@/lib/assistant-editor";

type Field = { key: string; label: string; before: string; after: string };

function InlineFieldDiff({ before, after }: Pick<Field, "before" | "after">) {
  const changes = useMemo(() => assistantTextDiff(before, after), [before, after]);
  return <div className="whitespace-pre-wrap break-words leading-7 [overflow-wrap:anywhere]">{changes.map((part, index) =>
    part.kind === "removed" ? <del key={index} className="bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200">{part.text}</del>
      : part.kind === "added" ? <ins key={index} className="bg-green-100 text-green-900 no-underline dark:bg-green-950 dark:text-green-200">{part.text}</ins>
        : <span key={index}>{part.text}</span>,
  )}</div>;
}

export function WorldProposalDiff({ fields, beforeAbsent }: { fields: Field[]; beforeAbsent: boolean }) {
  const { t } = useTranslation();
  const [view, setView] = useState<"comparison" | "inline">("comparison");
  return <section aria-label={t("world3.diff")} className="min-w-0 space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h4 className="text-sm font-medium">{t("world3.diff")}</h4>
      <div role="group" aria-label={t("world3.readingView")} className="flex flex-wrap gap-2">
        <Button size="sm" variant={view === "comparison" ? "secondary" : "outline"} aria-pressed={view === "comparison"} onClick={() => setView("comparison")}>{t("world3.sideBySide")}</Button>
        <Button size="sm" variant={view === "inline" ? "secondary" : "outline"} aria-pressed={view === "inline"} onClick={() => setView("inline")}>{t("world3.inlineDiff")}</Button>
      </div>
    </div>
    <p className="text-xs leading-6 text-muted-foreground">{t("world3.baselineHelp")}</p>
    {fields.map(field => <section key={field.key} aria-label={`${field.label} · ${t("world3.diff")}`} className="min-w-0 space-y-2 text-sm">
      <h5 className="text-xs font-medium text-muted-foreground">{field.label}</h5>
      {view === "comparison" ? <div className="grid min-w-0 gap-3 md:grid-cols-2">
        {[{ label: t("world3.proposalBefore"), text: field.before, empty: beforeAbsent ? t("world2.absent") : t("worlds.emptyField") },
          { label: t("world3.currentCandidate"), text: field.after, empty: t("worlds.emptyField") }].map(column =>
          <section key={column.label} aria-label={`${field.label} · ${column.label}`} className="min-w-0 rounded-md border p-3">
            <h6 className="mb-2 text-xs font-medium text-muted-foreground">{column.label}</h6>
            <p className="whitespace-pre-wrap break-words leading-7 [overflow-wrap:anywhere]">{column.text || column.empty}</p>
          </section>)}
      </div> : <InlineFieldDiff before={field.before} after={field.after} />}
    </section>)}
  </section>;
}
