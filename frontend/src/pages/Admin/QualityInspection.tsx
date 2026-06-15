import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ShieldAlert } from "lucide-react";

const severityClass = (severity: string) => {
  if (severity === "BLOCKING" || severity === "HIGH") return "border-rose-900 text-rose-400";
  if (severity === "MEDIUM") return "border-amber-900 text-amber-400";
  return "border-zinc-700 text-zinc-400";
};

const QualityInspection = () => {
  const [runs, setRuns] = useState<any[]>([]);

  useEffect(() => {
    api.admin.listQualityRuns().then(setRuns);
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">质量巡检</h1>
        <p className="text-sm text-zinc-500 mt-1">聚合反套路与剧情质量运行记录，优先处理高风险场景。</p>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldAlert className="h-5 w-5 text-rose-400" />
            巡检记录
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {runs.length === 0 ? (
            <div className="text-zinc-500">暂无质量记录</div>
          ) : (
            runs.map((run) => (
              <div key={`${run.kind}-${run.id}`} className="rounded-md border border-zinc-800 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
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
        </CardContent>
      </Card>
    </div>
  );
};

export default QualityInspection;
