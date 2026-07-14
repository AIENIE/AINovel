import { useEffect, useState } from "react";
import { FlaskConical, Loader2, Play, RefreshCcw, SquareCheckBig } from "lucide-react";
import { api } from "@/lib/api-client";
import type { G2EvaluationExperiment, G2EvaluationStatus } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

const transition = (status: G2EvaluationStatus): { next: G2EvaluationStatus; label: string } | null => {
  if (status === "DRAFT") return { next: "COLLECTING", label: "开放投稿" };
  if (status === "COLLECTING") return { next: "REVIEWING", label: "开始评审" };
  if (status === "REVIEWING") return { next: "CLOSED", label: "结束盲测" };
  return null;
};

const statusClass: Record<G2EvaluationStatus, string> = {
  DRAFT: "border-zinc-700 text-zinc-400",
  COLLECTING: "border-sky-900 text-sky-300",
  REVIEWING: "border-amber-900 text-amber-300",
  CLOSED: "border-emerald-900 text-emerald-300",
};

const G2EvaluationCampaigns = () => {
  const { toast } = useToast();
  const [items, setItems] = useState<G2EvaluationExperiment[]>([]);
  const [title, setTitle] = useState("");
  const [reviewers, setReviewers] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [transitioningId, setTransitioningId] = useState("");
  const [error, setError] = useState("");

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      setItems(await api.admin.listG2Evaluations());
    } catch (err: unknown) {
      setError(getErrorMessage(err, "盲测活动加载失败"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const create = async () => {
    const reviewerUsernames = reviewers.split(/[\n,\s]+/).map((value) => value.trim()).filter(Boolean);
    if (!title.trim() || reviewerUsernames.length === 0) {
      toast({ variant: "destructive", title: "请填写活动名称和 SSO 评审用户名" });
      return;
    }
    setIsCreating(true);
    try {
      await api.admin.createG2Evaluation({ title: title.trim(), reviewerUsernames });
      setTitle("");
      setReviewers("");
      toast({ title: "盲测活动已创建" });
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "创建失败", description: getErrorMessage(err, "请求失败") });
    } finally {
      setIsCreating(false);
    }
  };

  const advance = async (item: G2EvaluationExperiment) => {
    const next = transition(item.status);
    if (!next) return;
    setTransitioningId(item.id);
    try {
      await api.admin.transitionG2Evaluation(item.id, next.next);
      toast({ title: `已${next.label}` });
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: `${next.label}失败`, description: getErrorMessage(err, "请求失败") });
    } finally {
      setTransitioningId("");
    }
  };

  return (
    <div className="max-w-6xl space-y-6">
      <AdminPageHeader
        title="G2 盲测"
        description="维护带约束起草的快速/精雕匿名对照评估。"
        actions={<Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800" onClick={() => void load()}><RefreshCcw className="mr-2 h-4 w-4" />刷新</Button>}
      />

      <AdminPanel title="新建活动" description="填写已绑定统一登录的评审用户名。" actions={<FlaskConical className="h-5 w-5 text-sky-400" />}>
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto] md:items-end">
          <div className="space-y-2"><Label htmlFor="g2-title">活动名称</Label><Input id="g2-title" value={title} onChange={(event) => setTitle(event.target.value)} className="border-zinc-800 bg-zinc-950 text-zinc-100" /></div>
          <div className="space-y-2"><Label htmlFor="g2-reviewers">评审用户名</Label><Textarea id="g2-reviewers" value={reviewers} onChange={(event) => setReviewers(event.target.value)} className="min-h-10 border-zinc-800 bg-zinc-950 text-zinc-100" /></div>
          <Button onClick={() => void create()} disabled={isCreating}>{isCreating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FlaskConical className="mr-2 h-4 w-4" />}创建</Button>
        </div>
      </AdminPanel>

      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
      {isLoading ? <AdminLoadingState rows={4} /> : null}
      {!isLoading && !error && items.length === 0 ? <AdminEmptyState title="暂无盲测活动" /> : null}

      {!isLoading && !error && items.length > 0 ? (
        <AdminPanel title="活动状态" description="中性票计入有效样本数，但不增加精雕胜场。" actions={<SquareCheckBig className="h-5 w-5 text-emerald-400" />}>
          <div className="divide-y divide-zinc-800">
            {items.map((item) => {
              const next = transition(item.status);
              return (
                <div key={item.id} className="flex flex-col gap-4 py-4 first:pt-0 last:pb-0 lg:flex-row lg:items-center lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2"><span className="font-medium text-zinc-100">{item.title}</span><Badge variant="outline" className={statusClass[item.status]}>{item.status}</Badge>{item.gatePassed ? <Badge variant="outline" className="border-emerald-900 text-emerald-300">门槛已达成</Badge> : null}</div>
                    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-zinc-500">
                      <span>样本 {item.readySamplePairs}/{item.minimumSamplePairs}</span><span>有效票 {item.validVotes}/{item.minimumVotes}</span><span>评审 {item.reviewersWithVotes}/{item.minimumReviewers}</span><span>精雕胜率 {item.craftedWinRate.toFixed(1)}%/{item.craftedWinRateTarget}%</span><span>受邀 {item.invitedReviewers}</span>
                    </div>
                  </div>
                  {next ? <Button size="sm" variant="outline" className="border-zinc-700 bg-zinc-950 text-zinc-200 hover:bg-zinc-800" disabled={transitioningId === item.id} onClick={() => void advance(item)}>{transitioningId === item.id ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Play className="mr-2 h-4 w-4" />}{next.label}</Button> : null}
                </div>
              );
            })}
          </div>
        </AdminPanel>
      ) : null}
    </div>
  );
};

export default G2EvaluationCampaigns;
