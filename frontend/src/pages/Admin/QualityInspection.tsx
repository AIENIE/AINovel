import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { Badge } from "@/components/ui/badge";
import { ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPager, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

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
  const [total, setTotal] = useState(0);
  const [pageCount, setPageCount] = useState(1);
  const [retry, setRetry] = useState(0);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<"all" | "open" | "high">("all");
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError("");
    void api.admin.listQualityRuns(page, search, filter).then((result) => {
      if (cancelled) return;
      setRuns(result.items as unknown as QualityRun[]);
      setTotal(result.totalElements);
      setPageCount(Math.max(1, result.totalPages));
    }).catch((err: unknown) => {
      if (!cancelled) setError(getErrorMessage(err, "质量巡检记录加载失败"));
    }).finally(() => { if (!cancelled) setIsLoading(false); });
    return () => { cancelled = true; };
  }, [page, search, filter, retry]);

  return (
    <div className="space-y-6">
      <AdminPageHeader title="质量巡检" description="聚合反套路与剧情质量运行记录，优先处理高风险场景。" />

      <AdminPanel
        title="巡检记录"
        description="按风险与处理状态筛选，快速定位需要人工复核的场景。"
        actions={<ShieldAlert className="h-5 w-5 text-rose-400" />}
      >
        <div className="space-y-4">
          <AdminSearchToolbar value={search} onChange={(value) => { setSearch(value); setPage(0); }} placeholder="搜索章节、场景、摘要或 ID">
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
                onClick={() => { setFilter(item.key as typeof filter); setPage(0); }}
              >
                {item.label}
              </Button>
            ))}
          </AdminSearchToolbar>

          {error ? <AdminErrorState message={error} onRetry={() => setRetry((value) => value + 1)} /> : null}
          {isLoading ? <AdminLoadingState rows={5} /> : null}
          {!isLoading && !error && runs.length === 0 ? (
            <AdminEmptyState title={search || filter !== "all" ? "没有匹配的质量记录" : "暂无质量记录"} />
          ) : (
            (!isLoading && !error ? runs : []).map((run) => (
              <div key={`${run.kind}-${run.id}`} className="rounded-md border border-zinc-800 p-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="outline" className="border-zinc-700 text-zinc-400">{run.kind}</Badge>
                      <Badge variant="outline" className={severityClass(run.maxSeverity ?? "UNKNOWN")}>{run.maxSeverity || "UNKNOWN"}</Badge>
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
          {!isLoading && !error && runs.length > 0 ? (
            <AdminPager page={page} pageCount={pageCount} total={total} onPageChange={setPage} />
          ) : null}
        </div>
      </AdminPanel>
    </div>
  );
};

export default QualityInspection;
