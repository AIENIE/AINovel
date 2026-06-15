import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Badge } from "@/components/ui/badge";
import { ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPager, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage, matchesAdminSearch, pageCountFor, paginateItems } from "./admin-list-utils";

const severityClass = (severity: string) => {
  if (severity === "BLOCKING" || severity === "HIGH") return "border-rose-900 text-rose-400";
  if (severity === "MEDIUM") return "border-amber-900 text-amber-400";
  return "border-zinc-700 text-zinc-400";
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

const QualityInspection = () => {
  const [runs, setRuns] = useState<QualityRun[]>([]);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<"all" | "open" | "high">("all");
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      setRuns(await api.admin.listQualityRuns());
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

  const filteredRuns = runs
    .filter((run) => matchesAdminSearch(run, search))
    .filter((run) => {
      if (filter === "open") return !run.resolved;
      if (filter === "high") return Number(run.overallRiskScore ?? 0) >= 70 || ["BLOCKING", "HIGH"].includes(run.maxSeverity);
      return true;
    });
  const pageCount = pageCountFor(filteredRuns.length, 10);
  const visibleRuns = paginateItems(filteredRuns, page, 10);

  return (
    <div className="space-y-6">
      <AdminPageHeader title="质量巡检" description="聚合反套路与剧情质量运行记录，优先处理高风险场景。" />

      <AdminPanel
        title="巡检记录"
        description="按风险与处理状态筛选，快速定位需要人工复核的场景。"
        actions={<ShieldAlert className="h-5 w-5 text-rose-400" />}
      >
        <div className="space-y-4">
          <AdminSearchToolbar value={search} onChange={setSearch} placeholder="搜索章节、场景、摘要或 ID">
            {[
              { key: "all", label: "全部" },
              { key: "open", label: "待处理" },
              { key: "high", label: "高风险" },
            ].map((item) => (
              <Button
                key={item.key}
                size="sm"
                variant="outline"
                className={filter === item.key ? "border-rose-800 bg-rose-950/40 text-rose-200" : "border-zinc-800 bg-zinc-950 text-zinc-400"}
                onClick={() => setFilter(item.key as typeof filter)}
              >
                {item.label}
              </Button>
            ))}
          </AdminSearchToolbar>

          {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
          {isLoading ? <AdminLoadingState rows={5} /> : null}
          {!isLoading && !error && filteredRuns.length === 0 ? (
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
                    <div className="font-medium mt-2">{run.sceneTitle || run.chapterTitle || run.summary || run.id}</div>
                    <div className="text-xs text-zinc-500 mt-1">稿件 {run.manuscriptId} · 场景 {run.sceneId}</div>
                  </div>
                  <Badge variant="outline" className={run.resolved ? "border-emerald-900 text-emerald-400" : "border-amber-900 text-amber-400"}>
                    {run.resolved ? "已处理" : "待处理"}
                  </Badge>
                </div>
                {run.createdAt && <div className="text-xs text-zinc-600 mt-3">{new Date(run.createdAt).toLocaleString()}</div>}
              </div>
            ))
          )}
          {!isLoading && !error && filteredRuns.length > 0 ? (
            <AdminPager page={Math.min(page, pageCount - 1)} pageCount={pageCount} total={filteredRuns.length} onPageChange={setPage} />
          ) : null}
        </div>
      </AdminPanel>
    </div>
  );
};

export default QualityInspection;
