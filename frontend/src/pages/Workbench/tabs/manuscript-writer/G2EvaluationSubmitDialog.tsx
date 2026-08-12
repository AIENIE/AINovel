import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FlaskConical, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { api } from "@/lib/api-client";
import type { G2EvaluationExperiment } from "@/types";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage } from "@/lib/error-messages";

export function G2EvaluationSubmitDialog({ manuscriptId, sceneId }: { manuscriptId: string; sceneId: string }) {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [campaigns, setCampaigns] = useState<G2EvaluationExperiment[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setIsLoading(true);
    api.g2Evaluations.listOpen()
      .then((items) => {
        if (cancelled) return;
        setCampaigns(items);
        setSelectedId(items[0]?.id || "");
      })
      .catch((error: Error) => {
        if (!cancelled) toast({ variant: "destructive", title: t("g2.loadCampaignsFailed"), description: localizedErrorMessage(error, "g2.loadCampaignsFailed") });
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => { cancelled = true; };
  }, [open, toast, t]);

  const submit = async () => {
    if (!selectedId) return;
    setIsSubmitting(true);
    try {
      await api.g2Evaluations.submitSample(selectedId, manuscriptId, sceneId);
      toast({ title: t("g2.submitted"), description: t("g2.submittedDesc") });
      setOpen(false);
    } catch (error: any) {
      toast({ variant: "destructive", title: t("g2.submitFailed"), description: localizedErrorMessage(error, "g2.submitFailed") });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" disabled={!manuscriptId || !sceneId} title={t("g2.submitTitle")}>
          <FlaskConical className="mr-1.5 h-3.5 w-3.5" />
          {t("g2.participate")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("g2.submitSceneTitle")}</DialogTitle>
          <DialogDescription>{t("g2.submitSceneDesc")}</DialogDescription>
        </DialogHeader>
        {isLoading ? (
          <div className="flex min-h-24 items-center justify-center"><Loader2 className="h-5 w-5 animate-spin" /></div>
        ) : campaigns.length === 0 ? (
          <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">{t("g2.noOpenCampaigns")}</div>
        ) : (
          <select
            className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
            value={selectedId}
            onChange={(event) => setSelectedId(event.target.value)}
          >
            {campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.title}</option>)}
          </select>
        )}
        <DialogFooter>
          <Button onClick={() => void submit()} disabled={!selectedId || isSubmitting}>
            {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {t("common.submit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
