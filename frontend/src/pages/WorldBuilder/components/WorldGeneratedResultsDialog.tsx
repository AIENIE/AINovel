import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Copy } from "lucide-react";
import { api, isApiError } from "@/lib/api-client";
import { localizedErrorMessage } from "@/lib/error-messages";
import type { WorldModuleDefinition } from "@/types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";

type GeneratedResults = Awaited<ReturnType<typeof api.worlds.generatedResults>>;

interface WorldGeneratedResultsDialogProps {
  worldId: string;
  operationId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  definitions?: WorldModuleDefinition[];
}

export function WorldGeneratedResultsDialog({ worldId, operationId, open, onOpenChange, definitions = [] }: WorldGeneratedResultsDialogProps) {
  const { t } = useTranslation();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<GeneratedResults | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [copyStatus, setCopyStatus] = useState<{ field: string; failed: boolean } | null>(null);
  const copyAttempt = useRef(0);
  const currentResult = useRef<GeneratedResults | null>(null);
  const visibleResult = open && result?.worldId === worldId && result.operationId === operationId ? result : null;
  currentResult.current = visibleResult;

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    const pendingCopies = copyAttempt;
    setLoading(true);
    setResult(null);
    setError(null);
    setCopyStatus(null);
    api.worlds.generatedResults(worldId, operationId).then(data => {
      if (cancelled) return;
      if (data.worldId !== worldId || data.operationId !== operationId) throw new Error("WORLD_RESULT_RESPONSE_MISMATCH");
      setResult(data);
    }).catch(failure => {
      if (!cancelled) setError(failure);
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; pendingCopies.current++; };
  }, [worldId, operationId, open, attempt]);

  const copyField = async (field: string, text: string) => {
    const source = currentResult.current;
    const sequence = ++copyAttempt.current;
    setCopyStatus(null);
    try {
      await navigator.clipboard.writeText(text);
      if (currentResult.current === source && sequence === copyAttempt.current) setCopyStatus({ field, failed: false });
    } catch {
      if (currentResult.current === source && sequence === copyAttempt.current) setCopyStatus({ field, failed: true });
    }
  };
  const modules = Object.entries(visibleResult?.modules ?? {}).filter(([, fields]) => Object.keys(fields).length > 0);

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="max-h-[90dvh] max-w-3xl overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{t("worldGeneratedResults.title")}</DialogTitle>
        <DialogDescription>{t("worldGeneratedResults.description")}</DialogDescription>
      </DialogHeader>
      {loading && <p role="status" className="text-sm text-muted-foreground">{t("common.loading")}</p>}
      {!loading && error !== null && <div className="space-y-3">
        <p role="alert" className="text-sm text-destructive">{isApiError(error) && error.status === 409 && error.message === "WORLD_TASK_BASELINE_REQUIRED"
          ? t("worlds.taskBaselineMissing")
          : localizedErrorMessage(error, "worldGeneratedResults.loadFailed")}</p>
        <Button variant="outline" onClick={() => setAttempt(value => value + 1)}>{t("worldGeneratedResults.retry")}</Button>
      </div>}
      {!loading && error === null && modules.length === 0 && <p className="text-sm text-muted-foreground">{t("worldGeneratedResults.empty")}</p>}
      {modules.map(([moduleKey, fields]) => {
        const definition = definitions.find(item => item.key === moduleKey);
        const applied = visibleResult?.appliedModules.includes(moduleKey);
        return <section key={moduleKey} className="min-w-0 space-y-3 border-t pt-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="font-medium">{definition?.label ?? moduleKey}</h3>
            <span className="text-xs text-muted-foreground">{t(applied ? "worldGeneratedResults.applied" : "worldGeneratedResults.unapplied")}</span>
          </div>
          {Object.entries(fields).map(([fieldKey, text]) => {
            const field = JSON.stringify([moduleKey, fieldKey]);
            const label = definition?.fields.find(item => item.key === fieldKey)?.label ?? fieldKey;
            return <div key={fieldKey} className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h4 className="text-sm font-medium">{label}</h4>
                <Button variant="ghost" size="sm" aria-label={t("worldGeneratedResults.copyField", { field: label })} onClick={() => void copyField(field, text)}>
                  <Copy className="mr-1 h-3 w-3" />{t("worldGeneratedResults.copy")}
                </Button>
              </div>
              <p className="whitespace-pre-wrap break-words rounded border bg-muted/30 p-3 text-sm">{text}</p>
              {copyStatus?.field === field && <p role={copyStatus.failed ? "alert" : "status"} className={copyStatus.failed ? "text-xs text-destructive" : "text-xs text-muted-foreground"}>
                {t(copyStatus.failed ? "worldGeneratedResults.copyFailed" : "worldGeneratedResults.copied")}
              </p>}
            </div>;
          })}
        </section>;
      })}
    </DialogContent>
  </Dialog>;
}
