import { Loader2 } from "lucide-react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { TabsContent } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import type { PlotQualityRun, PlotQualityTrend, SlopQualityRun } from "@/types";
import {
  plotDimensionLabel,
  plotStatusClass,
  plotStatusText,
  qualityStatusClass,
  qualityStatusText,
  slopModuleLabel,
  slopRewriteTaskTitle,
} from "./shared";

type PlotSidebarPanelProps = {
  isPlotBusy: boolean;
  isPlotRevisionBusy: boolean;
  isSlopBusy: boolean;
  onApplyPlotRevision: () => Promise<void> | void;
  onCopySlopRewriteTask: (task: any, index: number) => Promise<void> | void;
  onGeneratePlotRevisionCandidate: () => Promise<void> | void;
  onRefreshPlotQuality: () => Promise<unknown> | void;
  onRunPlotDiagnosis: () => Promise<void> | void;
  onRunSlopDiagnosis: () => Promise<void> | void;
  plotDimensionEntries: Array<[string, number]>;
  plotTrend: PlotQualityTrend | null;
  plotTrendChartData: Array<{ label: string; riskScore: number }>;
  selectedManuscriptId: string;
  selectedPlotRun: PlotQualityRun | null;
  selectedQualityRun: SlopQualityRun | null;
  selectedSceneId: string;
  selectedSceneTitle: string;
};

export function PlotSidebarPanel({
  isPlotBusy,
  isPlotRevisionBusy,
  isSlopBusy,
  onApplyPlotRevision,
  onCopySlopRewriteTask,
  onGeneratePlotRevisionCandidate,
  onRefreshPlotQuality,
  onRunPlotDiagnosis,
  onRunSlopDiagnosis,
  plotDimensionEntries,
  plotTrend,
  plotTrendChartData,
  selectedManuscriptId,
  selectedPlotRun,
  selectedQualityRun,
  selectedSceneId,
  selectedSceneTitle,
}: PlotSidebarPanelProps) {
  return (
    <TabsContent value="plot" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <div className="flex flex-wrap gap-2 mb-2">
        <Button size="sm" variant="outline" onClick={() => void onRunSlopDiagnosis()} disabled={isSlopBusy || !selectedSceneId || !selectedManuscriptId}>
          {isSlopBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
          文本 Slop 诊断
        </Button>
        <Button size="sm" variant="outline" onClick={() => void onRunPlotDiagnosis()} disabled={isPlotBusy || !selectedSceneId || !selectedManuscriptId}>
          {isPlotBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
          重新诊断
        </Button>
        <Button size="sm" variant="secondary" onClick={() => void onGeneratePlotRevisionCandidate()} disabled={isPlotRevisionBusy || !selectedPlotRun}>
          {isPlotRevisionBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
          生成候选
        </Button>
        <Button size="sm" onClick={() => void onApplyPlotRevision()} disabled={isPlotRevisionBusy || !selectedPlotRun?.revisionCandidateText || selectedPlotRun?.revisionApplied}>
          采纳候选
        </Button>
      </div>

      <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 text-xs">
        <div className="space-y-3">
          <div className="rounded border p-2 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="font-medium">文本 Slop 风险</div>
              <Badge variant="outline" className={cn("shrink-0", qualityStatusClass(selectedQualityRun))}>
                {qualityStatusText(selectedQualityRun)}
              </Badge>
            </div>
            <div className="grid grid-cols-3 gap-2">
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">风险</div>
                <div className="text-lg font-semibold">{selectedQualityRun?.overallRiskScore ?? "-"}</div>
              </div>
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">证据</div>
                <div className="text-lg font-semibold">{selectedQualityRun?.evidenceLevel || "-"}</div>
              </div>
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">问题</div>
                <div className="text-lg font-semibold">{selectedQualityRun?.issues?.length ?? 0}</div>
              </div>
            </div>
            {!!selectedQualityRun?.safeClaim && <div className="text-muted-foreground leading-relaxed">{selectedQualityRun.safeClaim}</div>}
            {!selectedQualityRun && <div className="text-muted-foreground">点击“文本 Slop 诊断”后，会生成证据表、替代解释和改写任务；不会自动覆盖正文。</div>}
          </div>

          {!!selectedQualityRun && (
            <div className="rounded border p-2 space-y-2">
              <div className="font-medium">文本证据</div>
              {(selectedQualityRun.issues || []).map((issue) => (
                <div key={issue.id} className="rounded border p-2 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="outline">{slopModuleLabel(issue.module)}</Badge>
                    <Badge variant="outline">{issue.evidenceLevel || issue.severity}</Badge>
                    <span className="text-muted-foreground">风险 {issue.riskScore}</span>
                    {issue.charStart !== undefined && issue.charEnd !== undefined && (
                      <span className="text-muted-foreground">位置 {issue.charStart}-{issue.charEnd}</span>
                    )}
                  </div>
                  {!!(issue.quote || issue.evidence) && <div>{issue.quote || issue.evidence}</div>}
                  {!!(issue.repairHint || issue.minimalFix) && <div className="text-muted-foreground">{issue.repairHint || issue.minimalFix}</div>}
                </div>
              ))}
              {!selectedQualityRun.issues.length && <div className="text-muted-foreground">暂无文本风险证据。</div>}
            </div>
          )}

          {!!selectedQualityRun?.alternativeExplanations?.length && (
            <div className="rounded border p-2 space-y-1">
              <div className="font-medium">替代解释</div>
              {selectedQualityRun.alternativeExplanations.map((item, index) => (
                <div key={`${item}-${index}`} className="text-muted-foreground">
                  {index + 1}. {item}
                </div>
              ))}
            </div>
          )}

          {!!selectedQualityRun?.rewriteTasks?.length && (
            <div className="rounded border p-2 space-y-2">
              <div className="font-medium">改写任务</div>
              {selectedQualityRun.rewriteTasks.map((task, index) => (
                <div key={`${slopRewriteTaskTitle(task, index)}-${index}`} className="rounded border p-2 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <Badge variant="outline">{slopRewriteTaskTitle(task, index)}</Badge>
                    <Button size="sm" variant="ghost" className="h-6 px-2" onClick={() => void onCopySlopRewriteTask(task, index)}>
                      复制
                    </Button>
                  </div>
                  {!!task.problem && <div>{task.problem}</div>}
                  {!!(task.repair_goal || task.repairGoal) && <div className="text-muted-foreground">{task.repair_goal || task.repairGoal}</div>}
                </div>
              ))}
            </div>
          )}

          <div className="rounded border p-2 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="font-medium">{selectedPlotRun?.sceneTitle || selectedSceneTitle || "当前场景"}</div>
              <Badge variant="outline" className={cn("shrink-0", plotStatusClass(selectedPlotRun))}>
                {plotStatusText(selectedPlotRun)}
              </Badge>
            </div>
            <div className="grid grid-cols-3 gap-2">
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">风险</div>
                <div className="text-lg font-semibold">{selectedPlotRun?.overallRiskScore ?? "-"}</div>
              </div>
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">等级</div>
                <div className="text-lg font-semibold">{selectedPlotRun?.maxSeverity || "-"}</div>
              </div>
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">问题</div>
                <div className="text-lg font-semibold">{selectedPlotRun?.issues?.length ?? 0}</div>
              </div>
            </div>
            {!!selectedPlotRun?.summary && <div className="text-muted-foreground leading-relaxed">{selectedPlotRun.summary}</div>}
            {!selectedPlotRun && <div className="text-muted-foreground">当前场景还没有剧情诊断记录。</div>}
          </div>

          <div className="rounded border p-2 space-y-2">
            <div className="font-medium">问题清单</div>
            {(selectedPlotRun?.issues || []).map((issue) => (
              <div key={issue.id} className="rounded border p-2 space-y-1">
                <div className="flex items-center gap-2">
                  <Badge variant="outline">{plotDimensionLabel(issue.dimension)}</Badge>
                  <Badge variant="outline">{issue.severity}</Badge>
                  <span className="text-muted-foreground">风险 {issue.riskScore}</span>
                </div>
                {!!issue.evidence && <div>{issue.evidence}</div>}
                {!!issue.minimalFix && <div className="text-muted-foreground">{issue.minimalFix}</div>}
              </div>
            ))}
            {selectedPlotRun && !selectedPlotRun.issues.length && <div className="text-muted-foreground">暂无剧情问题。</div>}
          </div>

          <div className="grid grid-cols-1 gap-2">
            <div className="rounded border p-2 space-y-1">
              <div className="font-medium">重写计划</div>
              {(selectedPlotRun?.rewritePlan || []).map((item, index) => (
                <div key={`${item}-${index}`} className="text-muted-foreground">
                  {index + 1}. {item}
                </div>
              ))}
              {selectedPlotRun && !selectedPlotRun.rewritePlan.length && <div className="text-muted-foreground">暂无</div>}
            </div>
            <div className="rounded border p-2 space-y-1">
              <div className="font-medium">微调动作</div>
              {(selectedPlotRun?.surgicalFixes || []).map((item, index) => (
                <div key={`${item}-${index}`} className="text-muted-foreground">
                  {index + 1}. {item}
                </div>
              ))}
              {selectedPlotRun && !selectedPlotRun.surgicalFixes.length && <div className="text-muted-foreground">暂无</div>}
            </div>
          </div>

          {!!selectedPlotRun?.revisionCandidateText && (
            <div className="rounded border p-2 space-y-2">
              <div className="flex items-center justify-between">
                <div className="font-medium">候选修订</div>
                {selectedPlotRun.revisionApplied && <Badge variant="outline">已采纳</Badge>}
              </div>
              <div className="max-h-48 overflow-auto whitespace-pre-wrap rounded bg-muted/50 p-2 leading-relaxed">{selectedPlotRun.revisionCandidateText}</div>
            </div>
          )}

          <div className="rounded border p-2 space-y-2">
            <div className="flex items-center justify-between">
              <div className="font-medium">全稿趋势</div>
              <Button size="sm" variant="ghost" className="h-6 px-2" onClick={() => void onRefreshPlotQuality()}>
                刷新
              </Button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">平均风险</div>
                <div className="text-lg font-semibold">{plotTrend ? Math.round(plotTrend.averageRisk) : "-"}</div>
              </div>
              <div className="rounded bg-muted/60 p-2">
                <div className="text-muted-foreground">高风险场景</div>
                <div className="text-lg font-semibold">{plotTrend?.highRiskScenes ?? "-"}</div>
              </div>
            </div>
            <div className="h-[180px]">
              {plotTrendChartData.length ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={plotTrendChartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="label" tick={{ fontSize: 10 }} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Line type="monotone" dataKey="riskScore" stroke="#dc2626" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full rounded bg-muted/50 flex items-center justify-center text-muted-foreground">暂无趋势数据</div>
              )}
            </div>
            <div className="space-y-1">
              {plotDimensionEntries.map(([dimension, count]) => (
                <div key={dimension} className="flex items-center justify-between rounded border px-2 py-1">
                  <span>{plotDimensionLabel(dimension)}</span>
                  <span className="text-muted-foreground">{count}</span>
                </div>
              ))}
              {!plotDimensionEntries.length && <div className="text-muted-foreground">暂无维度统计</div>}
            </div>
          </div>
        </div>
      </ScrollArea>
    </TabsContent>
  );
}
