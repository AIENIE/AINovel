import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/mock-api";
import { Story } from "@/types";
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

const AnalysisDashboard = () => {
  const { toast } = useToast();
  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState("");
  const [jobs, setJobs] = useState<any[]>([]);
  const [reports, setReports] = useState<any[]>([]);
  const [issues, setIssues] = useState<any[]>([]);
  const [selectedReportId, setSelectedReportId] = useState("");
  const [selectedChapter, setSelectedChapter] = useState("1");
  const [continuityText, setContinuityText] = useState(
    "凌晨三点，林烬在城门外看见了昨日战死的副将，对方竟毫发无伤地站在雨幕中。"
  );
  const [isBusy, setIsBusy] = useState(false);

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

  useEffect(() => {
    loadStories().catch((error: any) => toast({ variant: "destructive", title: "加载故事失败", description: error.message }));
  }, []);

  useEffect(() => {
    if (!storyId) return;
    loadAnalysis(storyId).catch((error: any) => toast({ variant: "destructive", title: "加载分析数据失败", description: error.message }));
  }, [storyId]);

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

  const runContinuityCheck = async () =>
    runAction("连续性检查", () =>
      api.v2.analysis.triggerContinuity(storyId, {
        scope: "chapter",
        scopeReference: `chapter-${selectedChapter}`,
        text: continuityText,
      })
    );

  const selectedReport = useMemo(() => reports.find((item) => String(item.id) === String(selectedReportId)) || null, [reports, selectedReportId]);

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
          <Button variant="outline" onClick={runContinuityCheck} disabled={isBusy || !storyId}>
            连续性检查
          </Button>
          <Button variant="ghost" onClick={() => loadAnalysis(storyId)} disabled={!storyId}>
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
