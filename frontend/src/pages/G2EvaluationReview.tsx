import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Loader2, RefreshCcw } from "lucide-react";
import { api } from "@/lib/api-client";
import type { G2EvaluationReviewSample } from "@/types";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage } from "@/lib/error-messages";

const G2EvaluationReview = () => {
  const { id = "" } = useParams();
  const { toast } = useToast();
  const { t } = useTranslation();
  const [sample, setSample] = useState<G2EvaluationReviewSample | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isVoting, setIsVoting] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setError("");
    try {
      setSample(await api.g2Evaluations.nextReviewSample(id));
    } catch (err: unknown) {
      setError(localizedErrorMessage(err, "g2.loadSampleFailed"));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  const vote = async (choice: "LEFT" | "RIGHT" | "NEUTRAL") => {
    if (!sample || !id) return;
    setIsVoting(true);
    try {
      await api.g2Evaluations.vote(id, sample.sampleId, choice);
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: t("g2.voteFailed"), description: localizedErrorMessage(err, "g2.voteFailedDesc") });
    } finally {
      setIsVoting(false);
    }
  };

  return (
    <main className="min-h-screen bg-background px-4 py-8 sm:px-6 lg:px-10">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-col gap-3 border-b pb-5 sm:flex-row sm:items-end sm:justify-between">
          <div><h1 className="text-2xl font-semibold">{t("g2.title")}</h1><p className="mt-1 text-sm text-muted-foreground">{t("g2.subtitle")}</p></div>
          <Button size="icon" variant="outline" title={t("g2.refresh")} onClick={() => void load()} disabled={isLoading}><RefreshCcw className="h-4 w-4" /></Button>
        </div>
        {isLoading ? <div className="flex min-h-72 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin" /></div> : null}
        {!isLoading && error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">{error}</div> : null}
        {!isLoading && !error && !sample ? <div className="rounded-md border border-dashed p-8 text-center text-sm text-muted-foreground">{t("g2.empty")}</div> : null}
        {!isLoading && sample ? (
          <>
            <div className="grid gap-5 lg:grid-cols-2">
              <article className="min-h-[420px] whitespace-pre-wrap rounded-md border p-5 text-sm leading-7"><h2 className="mb-4 border-b pb-3 font-medium">{t("g2.textA")}</h2>{sample.leftText}</article>
              <article className="min-h-[420px] whitespace-pre-wrap rounded-md border p-5 text-sm leading-7"><h2 className="mb-4 border-b pb-3 font-medium">{t("g2.textB")}</h2>{sample.rightText}</article>
            </div>
            <div className="flex flex-col gap-3 border-t pt-5 sm:flex-row sm:justify-center">
              <Button onClick={() => void vote("LEFT")} disabled={isVoting}>{t("g2.chooseA")}</Button>
              <Button onClick={() => void vote("RIGHT")} disabled={isVoting}>{t("g2.chooseB")}</Button>
              <Button variant="outline" onClick={() => void vote("NEUTRAL")} disabled={isVoting}>{t("g2.cannotTell")}</Button>
            </div>
          </>
        ) : null}
      </div>
    </main>
  );
};

export default G2EvaluationReview;
