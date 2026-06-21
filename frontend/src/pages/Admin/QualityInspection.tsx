import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { AdminSlopReviewSample } from "@/types";
import { Badge } from "@/components/ui/badge";
import { Beaker, Check, FilePlus2, RefreshCcw, ShieldAlert, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { useToast } from "@/components/ui/use-toast";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPager, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage, matchesAdminSearch, pageCountFor, paginateItems } from "./admin-list-utils";
import { ReviewSampleFilter, filterReviewSamples, reviewSampleSummary } from "./quality-review-utils";

const severityClass = (severity: string) => {
  if (severity === "BLOCKING" || severity === "HIGH") return "border-rose-900 text-rose-400";
  if (severity === "MEDIUM") return "border-amber-900 text-amber-400";
  return "border-zinc-700 text-zinc-400";
};

const statusClass = (status: string) => {
  if (status === "APPROVED") return "border-emerald-900 text-emerald-400";
  if (status === "REJECTED") return "border-rose-900 text-rose-400";
  if (status === "NEEDS_DISCUSSION") return "border-sky-900 text-sky-400";
  return "border-amber-900 text-amber-400";
};

type QualityRun = {
  kind: string;
  id: string;
  manuscriptId?: string;
  sceneId?: string;
  chapterTitle?: string;
  sceneTitle?: string;
  summary?: string;
  maxSeverity?: string;
  overallRiskScore?: number;
  resolved?: boolean;
  createdAt?: string | null;
};

const emptyForm = {
  sampleId: "",
  text: "",
  genre: "",
  tone: "",
  characterContext: "",
  styleContext: "",
  expectedEvidenceLevel: "E1",
  expectedRequiresAiReview: false,
  reviewerNote: "",
};

const QualityInspection = () => {
  const { toast } = useToast();
  const [runs, setRuns] = useState<QualityRun[]>([]);
  const [samples, setSamples] = useState<AdminSlopReviewSample[]>([]);
  const [search, setSearch] = useState("");
  const [sampleSearch, setSampleSearch] = useState("");
  const [filter, setFilter] = useState<"all" | "open" | "high">("all");
  const [sampleFilter, setSampleFilter] = useState<ReviewSampleFilter>("all");
  const [page, setPage] = useState(0);
  const [samplePage, setSamplePage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState(emptyForm);
  const [savingId, setSavingId] = useState("");
  const [isCreating, setIsCreating] = useState(false);

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      const [qualityRuns, reviewSamples] = await Promise.all([
        api.admin.listQualityRuns(),
        api.admin.listQualityReviewSamples(),
      ]);
      setRuns(qualityRuns);
      setSamples(reviewSamples);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "质量巡检记录加载失败"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    setPage(0);
  }, [search, filter]);

  useEffect(() => {
    setSamplePage(0);
  }, [sampleSearch, sampleFilter]);

  const createSample = async () => {
    if (!form.text.trim()) {
      toast({ variant: "destructive", title: "样本文本不能为空" });
      return;
    }
    setIsCreating(true);
    try {
      await api.admin.createQualityReviewSample(form);
      toast({ title: "样本已创建" });
      setForm(emptyForm);
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "创建失败", description: getErrorMessage(err, "请求失败") });
    } finally {
      setIsCreating(false);
    }
  };

  const createFromRun = async (run: QualityRun) => {
    setSavingId(`run-${run.id}`);
    try {
      await api.admin.createQualityReviewSampleFromRun(run.id);
      toast({ title: "已沉淀为审核样本" });
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "沉淀失败", description: getErrorMessage(err, "仅 slop 记录支持沉淀样本") });
    } finally {
      setSavingId("");
    }
  };

  const updateSample = async (sample: AdminSlopReviewSample, status: string) => {
    setSavingId(sample.id);
    try {
      await api.admin.updateQualityReviewSample(sample.id, {
        status,
        expectedEvidenceLevel: sample.expectedEvidenceLevel,
        expectedRequiresAiReview: sample.expectedRequiresAiReview,
        reviewerNote: sample.reviewerNote || "",
      });
      toast({ title: "审核状态已更新" });
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "更新失败", description: getErrorMessage(err, "请求失败") });
    } finally {
      setSavingId("");
    }
  };

  const filteredRuns = runs
    .filter((run) => matchesAdminSearch(run, search))
    .filter((run) => {
      if (filter === "open") return !run.resolved;
      if (filter === "high") return Number(run.overallRiskScore ?? 0) >= 70 || ["BLOCKING", "HIGH"].includes(run.maxSeverity);
      return true;
    });
  const pageCount = pageCountFor(filteredRuns.length, 10);
  const visibleRuns = paginateItems(filteredRuns, page, 10);
  const filteredSamples = filterReviewSamples(samples, sampleSearch, sampleFilter);
  const samplePageCount = pageCountFor(filteredSamples.length, 8);
  const visibleSamples = paginateItems(filteredSamples, samplePage, 8);
  const sampleSummary = reviewSampleSummary(samples);

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="质量巡检"
        description="聚合质量运行记录，并沉淀可长期审核的 Slop 校准样本。"
        actions={
          <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800" onClick={() => void load()}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            刷新
          </Button>
        }
      />

      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}

      <AdminPanel title="样本审核" description="长期沉淀人工确认样本，用于后续阈值校准；本页不会自动修改诊断阈值。" actions={<Beaker className="h-5 w-5 text-sky-400" />}>
        <div className="space-y-5">
          <div className="grid gap-3 sm:grid-cols-4">
            <Metric label="样本总数" value={sampleSummary.total} />
            <Metric label="待审核" value={sampleSummary.pending} />
            <Metric label="预期不匹配" value={sampleSummary.mismatch} />
            <Metric label="高风险观测" value={sampleSummary.highRisk} />
          </div>

          <div className="grid gap-3 lg:grid-cols-[1fr_220px_180px]">
            <Textarea
              value={form.text}
              onChange={(event) => setForm((current) => ({ ...current, text: event.target.value }))}
              placeholder="输入需要人工标注的样本文本"
              className="min-h-24 border-zinc-800 bg-zinc-950 text-zinc-100 placeholder:text-zinc-600"
            />
            <div className="space-y-2">
              <Input value={form.sampleId} onChange={(event) => setForm((current) => ({ ...current, sampleId: event.target.value }))} placeholder="样本编号" className="border-zinc-800 bg-zinc-950" />
              <Input value={form.genre} onChange={(event) => setForm((current) => ({ ...current, genre: event.target.value }))} placeholder="题材" className="border-zinc-800 bg-zinc-950" />
              <Input value={form.tone} onChange={(event) => setForm((current) => ({ ...current, tone: event.target.value }))} placeholder="语气" className="border-zinc-800 bg-zinc-950" />
            </div>
            <div className="space-y-3">
              <div className="grid grid-cols-4 gap-1">
                {["E1", "E2", "E3", "E4"].map((level) => (
                  <Button key={level} size="sm" variant="outline" className={form.expectedEvidenceLevel === level ? "border-sky-800 bg-sky-950/40 text-sky-200" : "border-zinc-800 bg-zinc-950 text-zinc-400"} onClick={() => setForm((current) => ({ ...current, expectedEvidenceLevel: level }))}>
                    {level}
                  </Button>
                ))}
              </div>
              <label className="flex items-center gap-2 text-sm text-zinc-400">
                <Checkbox checked={form.expectedRequiresAiReview} onCheckedChange={(checked) => setForm((current) => ({ ...current, expectedRequiresAiReview: checked === true }))} />
                期望触发 AI review
              </label>
              <Button className="w-full bg-sky-600 text-white hover:bg-sky-500" disabled={isCreating} onClick={() => void createSample()}>
                <FilePlus2 className="mr-2 h-4 w-4" />
                创建样本
              </Button>
            </div>
          </div>

          <AdminSearchToolbar value={sampleSearch} onChange={setSampleSearch} placeholder="搜索样本编号、文本、证据等级或备注">
            {[
              { key: "all", label: "全部" },
              { key: "pending", label: "待审核" },
              { key: "mismatch", label: "不匹配" },
              { key: "high", label: "高风险" },
            ].map((item) => (
              <Button key={item.key} size="sm" variant="outline" className={sampleFilter === item.key ? "border-sky-800 bg-sky-950/40 text-sky-200" : "border-zinc-800 bg-zinc-950 text-zinc-400"} onClick={() => setSampleFilter(item.key as ReviewSampleFilter)}>
                {item.label}
              </Button>
            ))}
          </AdminSearchToolbar>

          {isLoading ? <AdminLoadingState rows={3} /> : null}
          {!isLoading && filteredSamples.length === 0 ? (
            <AdminEmptyState title={samples.length === 0 ? "暂无审核样本" : "没有匹配的审核样本"} />
          ) : (
            visibleSamples.map((sample) => (
              <div key={sample.id} className="rounded-md border border-zinc-800 p-4">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="outline" className="border-zinc-700 text-zinc-400">{sample.sourceType}</Badge>
                      <Badge variant="outline" className={statusClass(sample.status)}>{sample.status}</Badge>
                      <Badge variant="outline" className={sample.matchesExpected ? "border-emerald-900 text-emerald-400" : "border-amber-900 text-amber-400"}>
                        {sample.matchesExpected ? "匹配" : "需复核"}
                      </Badge>
                      <span className="text-sm text-zinc-500">期望 {sample.expectedEvidenceLevel} / 观测 {sample.observedEvidenceLevel} · 风险 {sample.observedRiskScore}</span>
                    </div>
                    <div className="mt-2 text-sm font-medium text-zinc-200">{sample.sampleId || sample.id}</div>
                    <p className="mt-1 line-clamp-3 text-sm text-zinc-500">{sample.textPreview || sample.text}</p>
                    <div className="mt-2 text-xs text-zinc-600">{sample.genre || "未填题材"} · {sample.tone || "未填语气"} · {sample.createdAt ? new Date(sample.createdAt).toLocaleString() : ""}</div>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    <Button size="icon" variant="outline" className="border-emerald-900 text-emerald-400" disabled={savingId === sample.id} onClick={() => void updateSample(sample, "APPROVED")}>
                      <Check className="h-4 w-4" />
                    </Button>
                    <Button size="icon" variant="outline" className="border-sky-900 text-sky-400" disabled={savingId === sample.id} onClick={() => void updateSample(sample, "NEEDS_DISCUSSION")}>
                      <Beaker className="h-4 w-4" />
                    </Button>
                    <Button size="icon" variant="outline" className="border-rose-900 text-rose-400" disabled={savingId === sample.id} onClick={() => void updateSample(sample, "REJECTED")}>
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
          {!isLoading && filteredSamples.length > 0 ? (
            <AdminPager page={Math.min(samplePage, samplePageCount - 1)} pageCount={samplePageCount} total={filteredSamples.length} onPageChange={setSamplePage} />
          ) : null}
        </div>
      </AdminPanel>

      <AdminPanel title="巡检记录" description="按风险与处理状态筛选；Slop 记录可沉淀为长期审核样本。" actions={<ShieldAlert className="h-5 w-5 text-rose-400" />}>
        <div className="space-y-4">
          <AdminSearchToolbar value={search} onChange={setSearch} placeholder="搜索章节、场景、摘要或 ID">
            {[
              { key: "all", label: "全部" },
              { key: "open", label: "待处理" },
              { key: "high", label: "高风险" },
            ].map((item) => (
              <Button key={item.key} size="sm" variant="outline" className={filter === item.key ? "border-rose-800 bg-rose-950/40 text-rose-200" : "border-zinc-800 bg-zinc-950 text-zinc-400"} onClick={() => setFilter(item.key as typeof filter)}>
                {item.label}
              </Button>
            ))}
          </AdminSearchToolbar>

          {isLoading ? <AdminLoadingState rows={5} /> : null}
          {!isLoading && filteredRuns.length === 0 ? (
            <AdminEmptyState title={runs.length === 0 ? "暂无质量记录" : "没有匹配的质量记录"} />
          ) : (
            visibleRuns.map((run) => (
              <div key={`${run.kind}-${run.id}`} className="rounded-md border border-zinc-800 p-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="outline" className="border-zinc-700 text-zinc-400">{run.kind}</Badge>
                      <Badge variant="outline" className={severityClass(run.maxSeverity)}>{run.maxSeverity || "UNKNOWN"}</Badge>
                      <span className="text-sm text-zinc-500">风险分 {run.overallRiskScore}</span>
                    </div>
                    <div className="mt-2 font-medium">{run.sceneTitle || run.chapterTitle || run.summary || run.id}</div>
                    <div className="mt-1 text-xs text-zinc-500">稿件 {run.manuscriptId} · 场景 {run.sceneId}</div>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    {run.kind === "slop" ? (
                      <Button size="sm" variant="outline" className="border-sky-900 text-sky-400" disabled={savingId === `run-${run.id}`} onClick={() => void createFromRun(run)}>
                        <FilePlus2 className="mr-2 h-4 w-4" />
                        沉淀样本
                      </Button>
                    ) : null}
                    <Badge variant="outline" className={run.resolved ? "border-emerald-900 text-emerald-400" : "border-amber-900 text-amber-400"}>
                      {run.resolved ? "已处理" : "待处理"}
                    </Badge>
                  </div>
                </div>
                {run.createdAt && <div className="mt-3 text-xs text-zinc-600">{new Date(run.createdAt).toLocaleString()}</div>}
              </div>
            ))
          )}
          {!isLoading && filteredRuns.length > 0 ? (
            <AdminPager page={Math.min(page, pageCount - 1)} pageCount={pageCount} total={filteredRuns.length} onPageChange={setPage} />
          ) : null}
        </div>
      </AdminPanel>
    </div>
  );
};

const Metric = ({ label, value }: { label: string; value: number }) => (
  <div className="rounded-md border border-zinc-800 bg-zinc-950/60 p-3">
    <div className="text-xs text-zinc-500">{label}</div>
    <div className="mt-1 text-xl font-semibold text-zinc-100">{value}</div>
  </div>
);

export default QualityInspection;
