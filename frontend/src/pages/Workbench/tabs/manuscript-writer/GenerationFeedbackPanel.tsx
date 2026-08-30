import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { TabsContent } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { api } from "@/lib/api-client";
import type { GenerationRunSummary } from "@/types";
import { formatDateTime } from "./shared";
import { localizedErrorMessage } from "@/lib/error-messages";

const FEEDBACK_TAGS = [
  "PLOT_CAUSALITY",
  "CHARACTER_MOTIVATION",
  "CONTINUITY_SETTING",
  "VOICE_DIALOGUE",
  "PACING",
  "STYLE_SPECIFICITY",
  "AI_CLICHE",
  "OTHER",
] as const;

type GenerationFeedbackPanelProps = {
  active: boolean;
  latestRunId?: string;
  manuscriptId: string;
  sceneId: string;
};

function retentionPercent(rate: number | null): number {
  if (rate == null) return 0;
  const normalized = rate <= 1 ? rate * 100 : rate;
  return Math.max(0, Math.min(100, Math.round(normalized)));
}

function limitCodePoints(value: string, limit: number): string {
  return Array.from(value).slice(0, limit).join("");
}

export function GenerationFeedbackPanel({
  active,
  latestRunId,
  manuscriptId,
  sceneId,
}: GenerationFeedbackPanelProps) {
  const { t } = useTranslation();
  const requestSequence = useRef(0);
  const [run, setRun] = useState<GenerationRunSummary | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [note, setNote] = useState("");
  const [preferenceConfirmed, setPreferenceConfirmed] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");

  const applyRun = useCallback((nextRun: GenerationRunSummary | null) => {
    setRun(nextRun);
    setTags(nextRun?.feedbackTags || []);
    setNote(nextRun?.feedbackNote || "");
    setPreferenceConfirmed(Boolean(nextRun?.preferenceConfirmed));
  }, []);

  const loadLatestRun = useCallback(async () => {
    const requestId = ++requestSequence.current;
    if (!active || !manuscriptId || !sceneId) {
      applyRun(null);
      setError("");
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError("");
    try {
      const runs = await api.manuscripts.listGenerationRuns(manuscriptId, sceneId, 1);
      if (requestSequence.current === requestId) applyRun(runs[0] || null);
    } catch (loadError: unknown) {
      if (requestSequence.current === requestId) {
        applyRun(null);
        setError(localizedErrorMessage(loadError, "generationFeedback.loadFailed"));
      }
    } finally {
      if (requestSequence.current === requestId) setIsLoading(false);
    }
  }, [active, applyRun, manuscriptId, sceneId]);

  useEffect(() => {
    void loadLatestRun();
    return () => {
      requestSequence.current += 1;
    };
  }, [latestRunId, loadLatestRun]);

  const availableTags = useMemo(
    () => Array.from(new Set<string>([...FEEDBACK_TAGS, ...tags])),
    [tags],
  );
  const isDirty = Boolean(run) && (
    note !== (run?.feedbackNote || "")
    || preferenceConfirmed !== Boolean(run?.preferenceConfirmed)
    || tags.join("\u0000") !== (run?.feedbackTags || []).join("\u0000")
  );
  const confirmationMissingEvidence = preferenceConfirmed && tags.length === 0 && note.trim().length === 0;

  const toggleTag = (tag: string) => {
    setTags((current) => {
      if (current.includes(tag)) return current.filter((item) => item !== tag);
      return current.length < 8 ? [...current, tag] : current;
    });
  };

  const saveFeedback = async () => {
    if (!run || !manuscriptId || !sceneId) return;
    setIsSaving(true);
    setError("");
    try {
      const updated = await api.manuscripts.updateGenerationRunFeedback(manuscriptId, sceneId, run.id, {
        tags,
        note: note.trim(),
        preferenceConfirmed,
      });
      applyRun(updated);
    } catch (saveError: unknown) {
      setError(localizedErrorMessage(saveError, "generationFeedback.saveFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <TabsContent value="feedback" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2 data-[state=active]:flex data-[state=active]:flex-col">
      <div className="mb-2 flex shrink-0 items-center justify-between gap-2">
        <div>
          <div className="text-sm font-medium">{t("generationFeedback.title")}</div>
          <div className="text-xs text-muted-foreground">{t("generationFeedback.description")}</div>
        </div>
        <Button size="sm" variant="outline" onClick={() => void loadLatestRun()} disabled={isLoading}>
          {isLoading ? t("generationFeedback.loading") : t("common.refresh")}
        </Button>
      </div>

      <ScrollArea className="min-h-0 flex-1 rounded-md border">
        <div className="space-y-3 p-3 text-xs">
          {error && <div role="alert" className="rounded border border-destructive/40 bg-destructive/5 p-2 text-destructive">{error}</div>}
          {!isLoading && !run && !error && (
            <div className="py-8 text-center text-muted-foreground">{t("generationFeedback.noRun")}</div>
          )}
          {run && (
            <>
              <div className="rounded border p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">{t("generationFeedback.latestRun")}</span>
                  <Badge variant="outline">{run.status}</Badge>
                </div>
                <div className="mt-2 grid grid-cols-1 gap-1 text-muted-foreground sm:grid-cols-2">
                  <div className="break-all">{t("generationFeedback.runId", { value: run.id })}</div>
                  <div className="break-all">{t("generationFeedback.versionId", { value: run.generationVersionId })}</div>
                  <div>{t("generationFeedback.createdAt", { value: formatDateTime(run.createdAt) })}</div>
                  <div>{t("generationFeedback.mode", { value: run.mode || "-" })}</div>
                  <div>{t("generationFeedback.source", { value: run.createdBy || "-" })}</div>
                  <div>{t("generationFeedback.model", { value: run.modelKey || "-" })}</div>
                  <div>{t("generationFeedback.promptVersion", { value: run.promptVersion || "-" })}</div>
                  <div>{t("generationFeedback.attempts", { count: run.attemptCount })}</div>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                <div className="rounded border p-2">
                  <div className="text-muted-foreground">{t("generationFeedback.retentionRate")}</div>
                  <div className="mt-1 text-lg font-semibold">
                    {run.recalculationPending ? t("generationFeedback.recalculating") : `${retentionPercent(run.retentionRate)}%`}
                  </div>
                </div>
                <div className="rounded border p-2">
                  <div className="text-muted-foreground">{t("generationFeedback.addedCharacters")}</div>
                  <div className="mt-1 text-lg font-semibold text-emerald-600">{run.addedCharacters == null ? "-" : `+${run.addedCharacters}`}</div>
                </div>
                <div className="rounded border p-2">
                  <div className="text-muted-foreground">{t("generationFeedback.deletedCharacters")}</div>
                  <div className="mt-1 text-lg font-semibold text-rose-600">{run.deletedCharacters == null ? "-" : `-${run.deletedCharacters}`}</div>
                </div>
              </div>

              <div className="rounded border p-3">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">{t("generationFeedback.contextSources")}</span>
                  <span className="text-muted-foreground">
                    {t("generationFeedback.contextBudget", {
                      used: run.contextManifest?.tokenUsed ?? 0,
                      budget: run.contextManifest?.tokenBudget ?? 0,
                    })}
                  </span>
                </div>
                {(run.contextManifest?.sources || []).map((source) => (
                  <div key={`${source.sourceType}-${source.sourceId}`} className="mb-2 rounded bg-muted/40 p-2 last:mb-0">
                    <div className="flex flex-wrap items-center gap-1">
                      <span>{source.label || source.sourceId}</span>
                      <Badge variant="outline">{source.sourceType}</Badge>
                      {source.truncated && <Badge variant="secondary">{t("contextPanel.truncated")}</Badge>}
                    </div>
                    <div className="mt-1 text-muted-foreground">{source.reason || t("common.none")}</div>
                    <div className="mt-1 text-muted-foreground">{t("generationFeedback.sourceTokens", { count: source.estimatedTokens })}</div>
                  </div>
                ))}
                {!(run.contextManifest?.sources || []).length && <div className="text-muted-foreground">{t("common.none")}</div>}
              </div>

              <div className="space-y-2">
                <Label>{t("generationFeedback.tags")}</Label>
                <div className="flex flex-wrap gap-1.5">
                  {availableTags.map((tag) => {
                    const selected = tags.includes(tag);
                    const translated = t(`generationFeedback.tag.${tag}`, { defaultValue: tag });
                    return (
                      <Button
                        key={tag}
                        type="button"
                        size="sm"
                        variant={selected ? "default" : "outline"}
                        className="h-7"
                        aria-pressed={selected}
                        disabled={!selected && tags.length >= 8}
                        onClick={() => toggleTag(tag)}
                      >
                        {translated}
                      </Button>
                    );
                  })}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="generation-feedback-note">{t("generationFeedback.note")}</Label>
                <Textarea
                  id="generation-feedback-note"
                  value={note}
                  className="min-h-24 resize-y"
                  placeholder={t("generationFeedback.notePlaceholder")}
                  onChange={(event) => setNote(limitCodePoints(event.target.value, 500))}
                />
              </div>

              <div className="flex items-start gap-2 rounded border p-3">
                <Checkbox
                  id="generation-preference-confirmed"
                  checked={preferenceConfirmed}
                  disabled={!preferenceConfirmed && tags.length === 0 && note.trim().length === 0}
                  onCheckedChange={(checked) => setPreferenceConfirmed(checked === true)}
                />
                <div className="space-y-1">
                  <Label htmlFor="generation-preference-confirmed">{t("generationFeedback.preferenceConfirmed")}</Label>
                  <p className="text-muted-foreground">{t("generationFeedback.preferenceConfirmedHint")}</p>
                </div>
              </div>

              <div className="flex justify-end">
                <Button onClick={() => void saveFeedback()} disabled={!isDirty || isSaving || confirmationMissingEvidence}>
                  {isSaving ? t("generationFeedback.saving") : t("generationFeedback.save")}
                </Button>
              </div>
            </>
          )}
        </div>
      </ScrollArea>
    </TabsContent>
  );
}
