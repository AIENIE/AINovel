import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Loader2, RefreshCcw } from "lucide-react";
import { api } from "@/lib/api-client";
import type { G2EvaluationReviewSample } from "@/types";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/use-toast";

const G2EvaluationReview = () => {
  const { id = "" } = useParams();
  const { toast } = useToast();
  const [sample, setSample] = useState<G2EvaluationReviewSample | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isVoting, setIsVoting] = useState(false);
  const [error, setError] = useState("");

  const load = async () => {
    if (!id) return;
    setIsLoading(true);
    setError("");
    try {
      setSample(await api.g2Evaluations.nextReviewSample(id));
    } catch (err: any) {
      setError(err?.message || "评审样本加载失败");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { void load(); }, [id]);

  const vote = async (choice: "LEFT" | "RIGHT" | "NEUTRAL") => {
    if (!sample || !id) return;
    setIsVoting(true);
    try {
      await api.g2Evaluations.vote(id, sample.sampleId, choice);
      await load();
    } catch (err: any) {
      toast({ variant: "destructive", title: "投票失败", description: err?.message || "请稍后重试" });
    } finally {
      setIsVoting(false);
    }
  };

  return (
    <main className="min-h-screen bg-background px-4 py-8 sm:px-6 lg:px-10">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-col gap-3 border-b pb-5 sm:flex-row sm:items-end sm:justify-between">
          <div><h1 className="text-2xl font-semibold">匿名文本评审</h1><p className="mt-1 text-sm text-muted-foreground">请选择更愿意继续阅读的文本。</p></div>
          <Button size="icon" variant="outline" title="刷新样本" onClick={() => void load()} disabled={isLoading}><RefreshCcw className="h-4 w-4" /></Button>
        </div>
        {isLoading ? <div className="flex min-h-72 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin" /></div> : null}
        {!isLoading && error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">{error}</div> : null}
        {!isLoading && !error && !sample ? <div className="rounded-md border border-dashed p-8 text-center text-sm text-muted-foreground">当前没有待评审的匿名样本。</div> : null}
        {!isLoading && sample ? (
          <>
            <div className="grid gap-5 lg:grid-cols-2">
              <article className="min-h-[420px] whitespace-pre-wrap rounded-md border p-5 text-sm leading-7"><h2 className="mb-4 border-b pb-3 font-medium">文本 A</h2>{sample.leftText}</article>
              <article className="min-h-[420px] whitespace-pre-wrap rounded-md border p-5 text-sm leading-7"><h2 className="mb-4 border-b pb-3 font-medium">文本 B</h2>{sample.rightText}</article>
            </div>
            <div className="flex flex-col gap-3 border-t pt-5 sm:flex-row sm:justify-center">
              <Button onClick={() => void vote("LEFT")} disabled={isVoting}>选择文本 A</Button>
              <Button onClick={() => void vote("RIGHT")} disabled={isVoting}>选择文本 B</Button>
              <Button variant="outline" onClick={() => void vote("NEUTRAL")} disabled={isVoting}>无法区分</Button>
            </div>
          </>
        ) : null}
      </div>
    </main>
  );
};

export default G2EvaluationReview;
