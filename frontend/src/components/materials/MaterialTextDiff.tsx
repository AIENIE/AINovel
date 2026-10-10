import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { materialTextDiff } from "@/lib/material-text-diff";
export function MaterialTextDiff({ before, after }: { before: string; after: string }) {
  const { t } = useTranslation();
  const changes = useMemo(() => materialTextDiff(before,after),[before,after]);
  return <div className="space-y-2">
    <p className="text-xs text-muted-foreground">{t("evidence.diffHelp")}</p>
    {changes.map((change,index) => <div key={index} className={`min-w-0 rounded border p-2 text-xs ${change.kind === "removed" ? "border-red-300 bg-red-50 text-red-950 dark:bg-red-950 dark:text-red-100" : change.kind === "added" ? "border-green-300 bg-green-50 text-green-950 dark:bg-green-950 dark:text-green-100" : "text-muted-foreground"}`}>
      <p>{t(`evidence.diff.${change.kind}`)} · {t("evidence.position", { start: change.start, end: change.end })}</p>
      <pre className="max-h-40 overflow-auto whitespace-pre-wrap break-words">{Array.from(change.text).slice(0,2000).join("")}</pre>
      {change.end-change.start > 2000 && <p>{t("evidence.diffTruncated")}</p>}
    </div>)}
  </div>;
}
