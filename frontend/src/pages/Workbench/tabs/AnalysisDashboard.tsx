import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/mock-api";
import { Manuscript, Outline, SlopDriftRun, Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Progress } from "@/components/ui/progress";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer, Line, LineChart, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";

const issueStatusOptions = ["open", "acknowledged", "resolved", "false_positive"];
const driftMetricLabels: Record<string, string> = {
  template_density: "模板密度",
  causal_coherence: "因果连贯",
  role_stability: "角色稳定",
  foreshadow_memory: "伏笔记忆",
  breath_score: "呼吸节奏",
};
const driftMetricColors = ["#7c3aed", "#0f766e", "#b45309", "#dc2626", "#2563eb"];

const driftMetricLabel = (key: string) => driftMetricLabels[key] || key;

const AnalysisDashboard = () => {
  const { toast } = useToast();
  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState("");
  const [outlines, setOutlines] = useState<Outline[]>([]);
  const [selectedOutlineId, setSelectedOutlineId] = useState("");
  const [manuscripts, setManuscripts] = useState<Manuscript[]>([]);
  const [selectedManuscriptId, setSelectedManuscriptId] = useState("");
  const [jobs, setJobs] = useState<any[]>([]);
  const [reports, setReports] = useState<any[]>([]);
  const [issues, setIssues] = useState<any[]>([]);
  const [driftRuns, setDriftRuns] = useState<SlopDriftRun[]>([]);
  const [selectedDriftRunId, setSelectedDriftRunId] = useState("");
  const [selectedReportId, setSelectedReportId] = useState("");
  const [selectedChapter, setSelectedChapter] = useState("1");
  const [continuityText, setContinuityText] = useState(
    "凌晨三点，林烬在城门外看见了昨日战死的副将，对方竟毫发无伤地站在雨幕中。"
  );
  const [isBusy, setIsBusy] = useState(false);
  const [isDriftBusy, setIsDriftBusy] = useState(false);

  const loadStories = async () => {
    const list = await api.stories.list();
    setStories(list);
    if (!storyId && list.length > 0) {
      setStoryId(list[0].id);
    }
  };

  const loadAnalysis = async (targetStoryId: string) => {
    if (!targetStoryId) return;
    const [jobList, reportList, issueList] = await Promise.all([
      api.v2.analysis.listJobs(targetStoryId),
      api.v2.analysis.listReports(targetStoryId),
      api.v2.analysis.listIssues(targetStoryId),
    ]);
    setJobs(jobList.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
    setReports(reportList.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
    setIssues(issueList.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
    if (!selectedReportId && reportList[0]) {
      setSelectedReportId(reportList[0].id);
    }
  };

  const loadOutlines = async (targetStoryId: string) => {
    if (!targetStoryId) return;
    const list = await api.outlines.listByStory(targetStoryId);
    setOutlines(list);
    setSelectedOutlineId((current) => (current && list.some((outline) => outline.id === current) ? current : list[0]?.id || ""));
  };

  const loadManuscripts = async (outlineId: string) => {
    if (!outlineId) {
      setManuscripts([]);
      setSelectedManuscriptId("");
      return;
    }
    const list = await api.manuscripts.listByOutline(outlineId);
    setManuscripts(list);
    setSelectedManuscriptId((current) => (current && list.some((manuscript) => manuscript.id === current) ? current : list[0]?.id || ""));
  };

  const loadDriftRuns = async (manuscriptId: string) => {
    if (!manuscriptId) {
      setDriftRuns([]);
      setSelectedDriftRunId("");
      return;
    }
    const list = await api.v2.slopDrift.listRuns(manuscriptId);
    const sorted = list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    setDriftRuns(sorted);
    setSelectedDriftRunId((current) => (current && sorted.some((run) => run.id === current) ? current : sorted[0]?.id || ""));
  };

  useEffect(() => {
    loadStories().catch((error: any) => toast({ variant: "destructive", title: "加载故事失败", description: error.message }));
  }, []);

  useEffect(() => {
    if (!storyId) return;
    loadAnalysis(storyId).catch((error: any) => toast({ variant: "destructive", title: "加载分析数据失败", description: error.message }));
    loadOutlines(storyId).catch((error: any) => toast({ variant: "destructive", title: "加载大纲失败", description: error.message }));
  }, [storyId]);

  useEffect(() => {
    loadManuscripts(selectedOutlineId).catch((error: any) => toast({ variant: "destructive", title: "加载稿件失败", description: error.message }));
  }, [selectedOutlineId]);

  useEffect(() => {
    loadDriftRuns(selectedManuscriptId).catch((error: any) => toast({ variant: "destructive", title: "加载 drift 巡检失败", description: error.message }));
  }, [selectedManuscriptId]);

  const hasRunningJob = useMemo(
    () => jobs.some((job) => ["queued", "running", "processing"].includes(String(job.status || "").toLowerCase())),
    [jobs]
  );

  useEffect(() => {
    if (!storyId || !hasRunningJob) return;
    const timer = window.setInterval(() => {
      loadAnalysis(storyId).catch(() => {});
    }, 3000);
    return () => window.clearInterval(timer);
  }, [storyId, hasRunningJob]);

  const runAction = async (label: string, fn: () => Promise<any>) => {
    if (!storyId) {
      toast({ variant: "destructive", title: "请先选择故事" });
      return;
    }
    if (hasRunningJob) {
      toast({ variant: "destructive", title: "当前已有分析任务运行中", description: "请等待当前任务完成后再触发新任务。" });
      return;
    }
    setIsBusy(true);
    try {
      await fn();
      await loadAnalysis(storyId);
      toast({ title: `${label}已触发` });
    } catch (error: any) {
      toast({ variant: "destructive", title: `${label}失败`, description: error.message });
    } finally {
      setIsBusy(false);
    }
  };

  const runChapterBetaReader = async () =>
    runAction("章节分析", () =>
      api.v2.analysis.triggerBetaReader(storyId, {
        scope: "chapter",
        scopeReference: `chapter-${selectedChapter}`,
        focus: "chapter_quality",
      })
    );

  const runFullBetaReader = async () => {
    const estimatedTokens = 180000;
    const ok = window.confirm(`全稿分析预计消耗约 ${estimatedTokens.toLocaleString()} tokens，是否继续？`);
    if (!ok) return;
    await runAction("全稿分析", () =>
      api.v2.analysis.triggerBetaReader(storyId, {
        scope: "full_manuscript",
        focus: "overall",
        estimatedTokenCost: estimatedTokens,
      })
    );
  };

  const selectedManuscript = useMemo(
    () => manuscripts.find((item) => item.id === selectedManuscriptId) || null,
    [manuscripts, selectedManuscriptId]
  );

  const runSlopDrift = async () => {
    if (!selectedManuscriptId || !selectedManuscript) {
      toast({ variant: "destructive", title: "请先选择稿件" });
      return;
    }
    const plainLength = Object.values(selectedManuscript.sections || {})
      .join("\n")
      .replace(/<[^>]+>/g, "")
      .length;
    const estimatedTokens = Math.max(6000, Math.ceil(plainLength * 1.6));
    const ok = window.confirm(`长篇 drift 巡检预计消耗约 ${estimatedTokens.toLocaleString()} tokens，是否继续？`);
    if (!ok) return;
    setIsDriftBusy(true);
    try {
      const run = await api.v2.slopDrift.analyze(selectedManuscriptId);
      await loadDriftRuns(selectedManuscriptId);
      setSelectedDriftRunId(run.id);
      toast({ title: "长篇 drift 巡检已完成" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "长篇 drift 巡检失败", description: error.message });
    } finally {
      setIsDriftBusy(false);
    }
  };

  const runContinuityCheck = async () =>
    runAction("连续性检查", () =>
      api.v2.analysis.triggerContinuity(storyId, {
        scope: "chapter",
        scopeReference: `chapter-${selectedChapter}`,
        text: continuityText,
      })
    );

  const selectedReport = useMemo(() => reports.find((item) => String(item.id) === String(selectedReportId)) || null, [reports, selectedReportId]);
  const selectedDriftRun = useMemo(
    () => driftRuns.find((item) => String(item.id) === String(selectedDriftRunId)) || null,
    [driftRuns, selectedDriftRunId]
  );

  const driftCurveRows = useMemo(() => {
    if (!selectedDriftRun) return [];
    const rows: Record<string, any> = {};
    Object.entries(selectedDriftRun.metricCurves || {}).forEach(([metric, points]) => {
      if (!Array.isArray(points)) return;
      points.forEach((point: any) => {
        const windowName = String(point.window || point.label || "window");
        rows[windowName] = rows[windowName] || { window: windowName };
        rows[windowName][metric] = Number(point.score || point.value || 0);
      });
    });
    return Object.values(rows);
  }, [selectedDriftRun]);

  const driftMetricKeys = useMemo(
    () => Object.keys(selectedDriftRun?.metricCurves || {}).filter((key) => Array.isArray(selectedDriftRun?.metricCurves?.[key])),
    [selectedDriftRun]
  );

  const radarData = useMemo(() => {
    if (!selectedReport) return [];
    return [
      { metric: "节奏", score: Number(selectedReport.scorePacing ?? 0) },
      { metric: "角色", score: Number(selectedReport.scoreCharacters ?? 0) },
      { metric: "对话", score: Number(selectedReport.scoreDialogue ?? 0) },
      { metric: "一致性", score: Number(selectedReport.scoreConsistency ?? 0) },
      { metric: "吸引力", score: Number(selectedReport.scoreEngagement ?? 0) },
    ];
  }, [selectedReport]);

  const tensionLineData = useMemo(() => {
    const base = Number(selectedReport?.scorePacing ?? 75);
    return [
      { point: "开篇", value: Math.max(10, base - 12) },
      { point: "铺垫", value: Math.max(10, base - 5) },
      { point: "冲突", value: Math.min(100, base + 6) },
      { point: "高潮", value: Math.min(100, base + 14) },
      { point: "收束", value: Math.max(10, base - 2) },
    ];
  }, [selectedReport]);

  const updateIssueStatus = async (issueId: string, status: string) => {
    if (!storyId) return;
    try {
      await api.v2.analysis.updateIssue(storyId, issueId, { status });
      await loadAnalysis(storyId);
      toast({ title: "问题状态已更新" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "更新失败", description: error.message });
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="pt-6 flex flex-wrap items-end gap-3">
          <div className="w-[300px]">
            <Label>故事</Label>
            <Select value={storyId} onValueChange={setStoryId}>
              <SelectTrigger>
                <SelectValue placeholder="选择故事" />
              </SelectTrigger>
              <SelectContent>
                {stories.map((story) => (
                  <SelectItem key={story.id} value={story.id}>
                    {story.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-[240px]">
            <Label>大纲</Label>
            <Select value={selectedOutlineId} onValueChange={setSelectedOutlineId}>
              <SelectTrigger>
                <SelectValue placeholder="选择大纲" />
              </SelectTrigger>
              <SelectContent>
                {outlines.map((outline) => (
                  <SelectItem key={outline.id} value={outline.id}>
                    {outline.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-[240px]">
            <Label>稿件</Label>
            <Select value={selectedManuscriptId} onValueChange={setSelectedManuscriptId}>
              <SelectTrigger>
                <SelectValue placeholder="选择稿件" />
              </SelectTrigger>
              <SelectContent>
                {manuscripts.map((manuscript) => (
                  <SelectItem key={manuscript.id} value={manuscript.id}>
                    {manuscript.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-[150px]">
            <Label>章节</Label>
            <Select value={selectedChapter} onValueChange={setSelectedChapter}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Array.from({ length: 20 }).map((_, index) => (
                  <SelectItem key={index + 1} value={String(index + 1)}>
                    第 {index + 1} 章
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button onClick={runChapterBetaReader} disabled={isBusy || !storyId}>
            分析本章
          </Button>
          <Button variant="secondary" onClick={runFullBetaReader} disabled={isBusy || !storyId}>
            分析全稿
          </Button>
          <Button variant="secondary" onClick={runSlopDrift} disabled={isDriftBusy || !selectedManuscriptId}>
            长篇 drift 巡检
          </Button>
          <Button variant="outline" onClick={runContinuityCheck} disabled={isBusy || !storyId}>
            连续性检查
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              loadAnalysis(storyId);
              loadDriftRuns(selectedManuscriptId);
            }}
            disabled={!storyId}
          >
            刷新
          </Button>
          {hasRunningJob ? <Badge>任务执行中</Badge> : <Badge variant="outline">空闲</Badge>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>连续性检查输入</CardTitle>
          <CardDescription>触发连续性检查时会带入这段文本，便于检测角色/时间线矛盾。</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea className="min-h-[100px]" value={continuityText} onChange={(event) => setContinuityText(event.target.value)} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>长篇 drift 巡检</CardTitle>
          <CardDescription>按稿件窗口比较模板密度、角色稳定、事件推进和伏笔记忆。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-[360px]">
              <Label>巡检记录</Label>
              <Select value={selectedDriftRunId} onValueChange={setSelectedDriftRunId}>
                <SelectTrigger>
                  <SelectValue placeholder="选择巡检记录" />
                </SelectTrigger>
                <SelectContent>
                  {driftRuns.map((run) => (
                    <SelectItem key={run.id} value={run.id}>
                      {`${run.status} · ${run.riskLabel || "unavailable"} · ${run.createdAt ? new Date(run.createdAt).toLocaleString() : "刚刚"}`}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {selectedDriftRun ? (
              <div className="flex flex-wrap gap-2">
                <Badge>{selectedDriftRun.status}</Badge>
                <Badge variant="outline">风险 {selectedDriftRun.overallRiskScore}</Badge>
                <Badge variant="outline">窗口 {selectedDriftRun.windowCount}</Badge>
                <Badge variant="outline">字符 {selectedDriftRun.totalCharacters.toLocaleString()}</Badge>
              </div>
            ) : null}
          </div>

          {selectedDriftRun ? (
            <div className="space-y-4">
              <div className="rounded border p-3 text-sm space-y-2">
                <p className="font-medium">{selectedDriftRun.summary || "暂无摘要"}</p>
                <p className="text-muted-foreground">{selectedDriftRun.safeClaim || "暂无安全结论"}</p>
              </div>

              {driftCurveRows.length > 0 ? (
                <div className="h-[300px] rounded border p-2">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={driftCurveRows}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="window" />
                      <YAxis domain={[0, 100]} />
                      <Tooltip />
                      {driftMetricKeys.map((metric, index) => (
                        <Line
                          key={metric}
                          type="monotone"
                          dataKey={metric}
                          name={driftMetricLabel(metric)}
                          stroke={driftMetricColors[index % driftMetricColors.length]}
                          strokeWidth={2}
                        />
                      ))}
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              ) : null}

              <div className="grid gap-4 xl:grid-cols-3">
                <div className="rounded border p-3 space-y-2">
                  <div className="text-sm font-medium">断层点</div>
                  {selectedDriftRun.driftPoints.map((point, index) => (
                    <div key={`${point.from_window || index}-${point.to_window || index}`} className="text-sm border-t pt-2 first:border-t-0 first:pt-0">
                      <div className="font-medium">{`${point.from_window || "from"} -> ${point.to_window || "to"}`}</div>
                      <p className="text-muted-foreground">{point.interpretation || point.safe_claim || "暂无说明"}</p>
                    </div>
                  ))}
                  {!selectedDriftRun.driftPoints.length && <p className="text-sm text-muted-foreground">暂无断层点</p>}
                </div>

                <div className="rounded border p-3 space-y-2">
                  <div className="text-sm font-medium">证据</div>
                  {selectedDriftRun.evidenceItems.slice(0, 5).map((item, index) => (
                    <div key={`${item.window || "window"}-${index}`} className="text-sm border-t pt-2 first:border-t-0 first:pt-0">
                      <div className="flex flex-wrap gap-2">
                        <Badge variant="outline">{item.window || "window"}</Badge>
                        <Badge variant="outline">{item.module || "module"}</Badge>
                        <Badge>{item.evidence_level || item.evidenceLevel || "E1"}</Badge>
                      </div>
                      <p className="mt-1">{item.quote || item.risk_explanation || "暂无证据文本"}</p>
                    </div>
                  ))}
                  {!selectedDriftRun.evidenceItems.length && <p className="text-sm text-muted-foreground">暂无证据</p>}
                </div>

                <div className="rounded border p-3 space-y-2">
                  <div className="text-sm font-medium">修复任务</div>
                  {selectedDriftRun.rewriteTasks.map((task, index) => (
                    <div key={`${task.task_id || task.taskId || index}`} className="text-sm border-t pt-2 first:border-t-0 first:pt-0">
                      <div className="font-medium">{task.task_id || task.taskId || `D${index + 1}`}</div>
                      <p className="text-muted-foreground">{task.problem || task.repair_goal || task.repairGoal || "暂无任务说明"}</p>
                    </div>
                  ))}
                  {!selectedDriftRun.rewriteTasks.length && <p className="text-sm text-muted-foreground">暂无修复任务</p>}
                </div>
              </div>

              {selectedDriftRun.alternativeExplanations.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {selectedDriftRun.alternativeExplanations.map((item) => (
                    <Badge key={item} variant="outline">{item}</Badge>
                  ))}
                </div>
              ) : null}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">暂无长篇 drift 巡检记录</p>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>任务列表</CardTitle>
            <CardDescription>异步任务进度与执行状态。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {jobs.map((job) => (
              <div key={job.id} className="rounded border p-3 space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span>{job.jobType}</span>
                  <Badge variant="outline">{job.status}</Badge>
                </div>
                <Progress value={Number(job.progress ?? 0)} />
                <div className="text-xs text-muted-foreground">{job.progressMessage || "处理中..."}</div>
              </div>
            ))}
            {!jobs.length && <p className="text-sm text-muted-foreground">暂无分析任务</p>}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>问题追踪</CardTitle>
            <CardDescription>可将问题标记为 acknowledged / resolved / false_positive。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {issues.map((issue) => (
              <div key={issue.id} className="rounded border p-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="text-sm font-medium">{issue.issueType || "continuity_issue"}</div>
                  <Badge>{issue.severity || "info"}</Badge>
                </div>
                <p className="text-xs text-muted-foreground mt-1">{issue.description}</p>
                <div className="mt-2">
                  <Select value={issue.status || "open"} onValueChange={(value) => updateIssueStatus(issue.id, value)}>
                    <SelectTrigger className="h-8">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {issueStatusOptions.map((item) => (
                        <SelectItem key={item} value={item}>
                          {item}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            ))}
            {!issues.length && <p className="text-sm text-muted-foreground">暂无连续性问题</p>}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>分析报告</CardTitle>
          <CardDescription>五维评分雷达图 + 张力趋势曲线。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="w-[320px]">
            <Label>选择报告</Label>
            <Select value={selectedReportId} onValueChange={setSelectedReportId}>
              <SelectTrigger>
                <SelectValue placeholder="选择报告" />
              </SelectTrigger>
              <SelectContent>
                {reports.map((report) => (
                  <SelectItem key={report.id} value={report.id}>
                    {`${report.scope || "scope"} · ${new Date(report.createdAt).toLocaleString()}`}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {selectedReport ? (
            <div className="grid gap-4 xl:grid-cols-2">
              <div className="h-[280px] rounded border p-2">
                <ResponsiveContainer width="100%" height="100%">
                  <RadarChart data={radarData}>
                    <PolarGrid />
                    <PolarAngleAxis dataKey="metric" />
                    <Radar name="score" dataKey="score" stroke="#4f7a63" fill="#6fa283" fillOpacity={0.4} />
                  </RadarChart>
                </ResponsiveContainer>
              </div>
              <div className="h-[280px] rounded border p-2">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={tensionLineData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="point" />
                    <YAxis domain={[0, 100]} />
                    <Tooltip />
                    <Line type="monotone" dataKey="value" stroke="#8b6f4e" strokeWidth={2} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <div className="xl:col-span-2 rounded border p-3 text-sm space-y-2">
                <div className="flex flex-wrap gap-2">
                  <Badge>总分 {selectedReport.scoreOverall ?? 0}</Badge>
                  <Badge variant="outline">Token 消耗 {selectedReport.tokenCost ?? 0}</Badge>
                </div>
                <p className="text-muted-foreground">{selectedReport.summary || "暂无摘要"}</p>
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">请选择报告查看可视化结果</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default AnalysisDashboard;
