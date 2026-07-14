import { useEffect, useState } from "react";
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

export function G2EvaluationSubmitDialog({ manuscriptId, sceneId }: { manuscriptId: string; sceneId: string }) {
  const { toast } = useToast();
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
        if (!cancelled) toast({ variant: "destructive", title: "盲测活动加载失败", description: error.message });
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => { cancelled = true; };
  }, [open, toast]);

  const submit = async () => {
    if (!selectedId) return;
    setIsSubmitting(true);
    try {
      await api.g2Evaluations.submitSample(selectedId, manuscriptId, sceneId);
      toast({ title: "已提交盲测", description: "将生成独立的快速与精雕副本，不会改动当前稿件。" });
      setOpen(false);
    } catch (error: any) {
      toast({ variant: "destructive", title: "盲测投稿失败", description: error?.message || "请稍后重试" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" disabled={!manuscriptId || !sceneId} title="提交当前场景参与 G2 盲测">
          <FlaskConical className="mr-1.5 h-3.5 w-3.5" />
          参与盲测
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>提交当前场景</DialogTitle>
          <DialogDescription>评估文本独立生成，当前正文和版本不会被覆盖。</DialogDescription>
        </DialogHeader>
        {isLoading ? (
          <div className="flex min-h-24 items-center justify-center"><Loader2 className="h-5 w-5 animate-spin" /></div>
        ) : campaigns.length === 0 ? (
          <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">当前没有开放投稿的盲测活动。</div>
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
            提交
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
