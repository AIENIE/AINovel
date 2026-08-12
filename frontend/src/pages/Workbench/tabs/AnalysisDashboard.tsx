import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { Manuscript, Outline, SlopDriftRun, Story, V2AnalysisJob, V2AnalysisReport, V2ContinuityIssue } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Progress } from "@/components/ui/progress";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer, Line, LineChart, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";
import { localizedErrorMessage } from "@/lib/error-messages";

const issueStatusOptions = ["open", "acknowledged", "resolved", "false_positive"];
const driftMetricLabelKeys: Record<string, string> = {
  template_density: "analysis.metricTemplateDensity",
  causal_coherence: "analysis.metricCausalCoherence",
  role_stability: "analysis.metricRoleStability",
  foreshadow_memory: "analysis.metricForeshadowMemory",
  breath_score: "analysis.metricBreathScore",
};
const driftMetricColors = ["#7c3aed", "#0f766e", "#b45309", "#dc2626", "#2563eb"];

const AnalysisDashboard = () => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState("");
  const [outlines, setOutlines] = useState<Outline[]>([]);
  const [selectedOutlineId, setSelectedOutlineId] = useState("");
  const [manuscripts, setManuscripts] = useState<Manuscript[]>([]);
  const [selectedManuscriptId, setSelectedManuscriptId] = useState("");
  const [jobs, setJobs] = useState<V2AnalysisJob[]>([]);
  const [reports, setReports] = useState<V2AnalysisReport[]>([]);
  const [issues, setIssues] = useState<V2ContinuityIssue[]>([]);
  const [driftRuns, setDriftRuns] = useState<SlopDriftRun[]>([]);
  const [selectedDriftRunId, setSelectedDriftRunId] = useState("");
  const [selectedReportId, setSelectedReportId] = useState("");
  const [selectedChapter, setSelectedChapter] = useState("1");
  const [continuityText, setContinuityText] = useState(
    t("analysis.continuitySampleText")
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
    setJobs(jobList.sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime()));
    setReports(reportList.sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime()));
    setIssues(issueList.sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime()));
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
    loadStories().catch((error: any) => toast({ variant: "destructive", title: t("errors.loadStoriesFailed"), description: localizedErrorMessage(error, "errors.loadStoriesFailed") }));
  }, []);

  useEffect(() => {
    if (!storyId) return;
    loadAnalysis(storyId).catch((error: any) => toast({ variant: "destructive", title: t("analysis.loadAnalysisFailed"), description: localizedErrorMessage(error, "analysis.loadAnalysisFailed") }));
    loadOutlines(storyId).catch((error: any) => toast({ variant: "destructive", title: t("errors.loadOutlinesFailed"), description: localizedErrorMessage(error, "errors.loadOutlinesFailed") }));
  }, [storyId]);

  useEffect(() => {
    loadManuscripts(selectedOutlineId).catch((error: any) => toast({ variant: "destructive", title: t("analysis.loadManuscriptsFailed"), description: localizedErrorMessage(error, "analysis.loadManuscriptsFailed") }));
  }, [selectedOutlineId]);

  useEffect(() => {
    loadDriftRuns(selectedManuscriptId).catch((error: any) => toast({ variant: "destructive", title: t("analysis.loadDriftFailed"), description: localizedErrorMessage(error, "analysis.loadDriftFailed") }));
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

  const runAction = async (labelKey: string, fn: () => Promise<any>) => {
    if (!storyId) {
      toast({ variant: "destructive", title: t("analysis.selectStoryFirst") });
      return;
    }
    if (hasRunningJob) {
      toast({ variant: "destructive", title: t("analysis.jobRunning"), description: t("analysis.jobRunningDesc") });
      return;
    }
    setIsBusy(true);
    try {
      await fn();
      await loadAnalysis(storyId);
      toast({ title: t("analysis.triggered", { label: t(labelKey) }) });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("analysis.triggerFailed", { label: t(labelKey) }), description: localizedErrorMessage(error, "errors.operationFailed") });
    } finally {
      setIsBusy(false);
    }
  };

  const runChapterBetaReader = async () =>
    runAction("analysis.chapterAnalysis", () =>
      api.v2.analysis.triggerBetaReader(storyId, {
        scope: "chapter",
        scopeReference: `chapter-${selectedChapter}`,
        focus: "chapter_quality",
      })
    );

  const runFullBetaReader = async () => {
    const estimatedTokens = 180000;
    const ok = window.confirm(t("analysis.fullAnalysisConfirm", { count: estimatedTokens }));
    if (!ok) return;
    await runAction("analysis.fullAnalysis", () =>
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
      toast({ variant: "destructive", title: t("analysis.selectManuscriptFirst") });
      return;
    }
    const plainLength = Object.values(selectedManuscript.sections || {})
      .join("\n")
      .replace(/<[^>]+>/g, "")
      .length;
    const estimatedTokens = Math.max(6000, Math.ceil(plainLength * 1.6));
    const ok = window.confirm(t("analysis.driftConfirm", { count: estimatedTokens }));
    if (!ok) return;
    setIsDriftBusy(true);
    try {
      await runTrackedAiOperation(api.v2.slopDrift.startAnalyze(selectedManuscriptId));
      const run = (await api.v2.slopDrift.listRuns(selectedManuscriptId))[0];
      if (!run) throw new Error(t("analysis.driftNoResult"));
      await loadDriftRuns(selectedManuscriptId);
      setSelectedDriftRunId(run.id);
      toast({ title: t("analysis.driftDone") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("analysis.driftFailed"), description: localizedErrorMessage(error, "analysis.driftFailed") });
    } finally {
      setIsDriftBusy(false);
    }
  };

  const runContinuityCheck = async () =>
    runAction("analysis.continuityCheck", () =>
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
      { metric: t("analysis.metricPacing"), score: Number(selectedReport.scorePacing ?? 0) },
      { metric: t("analysis.metricCharacters"), score: Number(selectedReport.scoreCharacters ?? 0) },
      { metric: t("analysis.metricDialogue"), score: Number(selectedReport.scoreDialogue ?? 0) },
      { metric: t("analysis.metricConsistency"), score: Number(selectedReport.scoreConsistency ?? 0) },
      { metric: t("analysis.metricEngagement"), score: Number(selectedReport.scoreEngagement ?? 0) },
    ];
  }, [selectedReport, t]);

  const tensionLineData = useMemo(() => {
    const base = Number(selectedReport?.scorePacing ?? 75);
    return [
      { point: t("analysis.tensionOpening"), value: Math.max(10, base - 12) },
      { point: t("analysis.tensionSetup"), value: Math.max(10, base - 5) },
      { point: t("analysis.tensionConflict"), value: Math.min(100, base + 6) },
      { point: t("analysis.tensionClimax"), value: Math.min(100, base + 14) },
      { point: t("analysis.tensionResolution"), value: Math.max(10, base - 2) },
    ];
  }, [selectedReport, t]);

  const updateIssueStatus = async (issueId: string, status: string) => {
    if (!storyId) return;
    try {
      await api.v2.analysis.updateIssue(storyId, issueId, { status });
      await loadAnalysis(storyId);
      toast({ title: t("analysis.issueStatusUpdated") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("errors.updateFailed"), description: localizedErrorMessage(error, "errors.updateFailed") });
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="pt-6 flex flex-wrap items-end gap-3">
          <div className="w-[300px]">
            <Label>{t("common.story")}</Label>
            <Select value={storyId} onValueChange={setStoryId}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.selectStory")} />
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
            <Label>{t("analysis.outline")}</Label>
            <Select value={selectedOutlineId} onValueChange={setSelectedOutlineId}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.selectOutline")} />
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
            <Label>{t("analysis.manuscript")}</Label>
            <Select value={selectedManuscriptId} onValueChange={setSelectedManuscriptId}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.selectManuscript")} />
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
            <Label>{t("analysis.chapter")}</Label>
            <Select value={selectedChapter} onValueChange={setSelectedChapter}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Array.from({ length: 20 }).map((_, index) => (
                  <SelectItem key={index + 1} value={String(index + 1)}>
                    {t("common.chapterShort", { count: index + 1 })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button onClick={runChapterBetaReader} disabled={isBusy || !storyId}>
            {t("analysis.analyzeChapter")}
          </Button>
          <Button variant="secondary" onClick={runFullBetaReader} disabled={isBusy || !storyId}>
            {t("analysis.analyzeFull")}
          </Button>
          <Button variant="secondary" onClick={runSlopDrift} disabled={isDriftBusy || !selectedManuscriptId}>
            {t("analysis.driftCheck")}
          </Button>
          <Button variant="outline" onClick={runContinuityCheck} disabled={isBusy || !storyId}>
            {t("analysis.continuityCheck")}
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              loadAnalysis(storyId);
              loadDriftRuns(selectedManuscriptId);
            }}
            disabled={!storyId}
          >
            {t("common.refresh")}
          </Button>
          {hasRunningJob ? <Badge>{t("analysis.jobRunning")}</Badge> : <Badge variant="outline">{t("analysis.idle")}</Badge>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("analysis.continuityInputTitle")}</CardTitle>
          <CardDescription>{t("analysis.continuityInputDesc")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea className="min-h-[100px]" value={continuityText} onChange={(event) => setContinuityText(event.target.value)} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("analysis.driftTitle")}</CardTitle>
          <CardDescription>{t("analysis.driftDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-[360px]">
              <Label>{t("analysis.driftRuns")}</Label>
              <Select value={selectedDriftRunId} onValueChange={setSelectedDriftRunId}>
                <SelectTrigger>
                  <SelectValue placeholder={t("analysis.selectDriftRun")} />
                </SelectTrigger>
                <SelectContent>
                  {driftRuns.map((run) => (
                    <SelectItem key={run.id} value={run.id}>
                      {`${run.status} · ${run.riskLabel || "unavailable"} · ${run.createdAt ? new Date(run.createdAt).toLocaleString() : t("analysis.justNow")}`}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {selectedDriftRun ? (
              <div className="flex flex-wrap gap-2">
                <Badge>{selectedDriftRun.status}</Badge>
                <Badge variant="outline">{t("analysis.riskScore", { score: selectedDriftRun.overallRiskScore })}</Badge>
                <Badge variant="outline">{t("analysis.windows", { count: selectedDriftRun.windowCount })}</Badge>
                <Badge variant="outline">{t("analysis.charCount", { count: Number(selectedDriftRun.totalCharacters) })}</Badge>
              </div>
            ) : null}
          </div>

          {selectedDriftRun ? (
            <div className="space-y-4">
              <div className="rounded border p-3 text-sm space-y-2">
                <p className="font-medium">{selectedDriftRun.summary || t("analysis.noSummary")}</p>
                <p className="text-muted-foreground">{selectedDriftRun.safeClaim || t("analysis.noSafeClaim")}</p>
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
                          name={t(driftMetricLabelKeys[metric] || "analysis.metricUnknown")}
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
                  <div className="text-sm font-medium">{t("analysis.driftPoints")}</div>
                  {selectedDriftRun.driftPoints.map((point, index) => (
                    <div key={`${point.from_window || index}-${point.to_window || index}`} className="text-sm border-t pt-2 first:border-t-0 first:pt-0">
                      <div className="font-medium">{`${point.from_window || "from"} -> ${point.to_window || "to"}`}</div>
                      <p className="text-muted-foreground">{point.interpretation || point.safe_claim || t("analysis.noDescription")}</p>
                    </div>
                  ))}
                  {!selectedDriftRun.driftPoints.length && <p className="text-sm text-muted-foreground">{t("analysis.noDriftPoints")}</p>}
                </div>

                <div className="rounded border p-3 space-y-2">
                  <div className="text-sm font-medium">{t("analysis.evidence")}</div>
                  {selectedDriftRun.evidenceItems.slice(0, 5).map((item, index) => (
                    <div key={`${item.window || "window"}-${index}`} className="text-sm border-t pt-2 first:border-t-0 first:pt-0">
                      <div className="flex flex-wrap gap-2">
                        <Badge variant="outline">{item.window || "window"}</Badge>
                        <Badge variant="outline">{item.module || "module"}</Badge>
                        <Badge>{item.evidence_level || item.evidenceLevel || "E1"}</Badge>
                      </div>
                      <p className="mt-1">{item.quote || item.risk_explanation || t("analysis.noEvidenceText")}</p>
                    </div>
                  ))}
                  {!selectedDriftRun.evidenceItems.length && <p className="text-sm text-muted-foreground">{t("analysis.noEvidence")}</p>}
                </div>

                <div className="rounded border p-3 space-y-2">
                  <div className="text-sm font-medium">{t("analysis.repairTasks")}</div>
                  {selectedDriftRun.rewriteTasks.map((task, index) => (
                    <div key={`${task.task_id || task.taskId || index}`} className="text-sm border-t pt-2 first:border-t-0 first:pt-0">
                      <div className="font-medium">{task.task_id || task.taskId || `D${index + 1}`}</div>
                      <p className="text-muted-foreground">{task.problem || task.repair_goal || task.repairGoal || t("analysis.noTaskDescription")}</p>
                    </div>
                  ))}
                  {!selectedDriftRun.rewriteTasks.length && <p className="text-sm text-muted-foreground">{t("analysis.noRepairTasks")}</p>}
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
            <p className="text-sm text-muted-foreground">{t("analysis.noDriftRuns")}</p>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("analysis.jobList")}</CardTitle>
            <CardDescription>{t("analysis.jobListDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {jobs.map((job) => (
              <div key={job.id} className="rounded border p-3 space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span>{job.jobType}</span>
                  <Badge variant="outline">{job.status}</Badge>
                </div>
                <Progress value={Number(job.progress ?? 0)} />
                <div className="text-xs text-muted-foreground">{job.progressMessage || t("analysis.processing")}</div>
              </div>
            ))}
            {!jobs.length && <p className="text-sm text-muted-foreground">{t("analysis.noJobs")}</p>}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("analysis.issueTracking")}</CardTitle>
            <CardDescription>{t("analysis.issueTrackingDesc")}</CardDescription>
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
            {!issues.length && <p className="text-sm text-muted-foreground">{t("analysis.noIssues")}</p>}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("analysis.reportTitle")}</CardTitle>
          <CardDescription>{t("analysis.reportDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="w-[320px]">
            <Label>{t("analysis.selectReport")}</Label>
            <Select value={selectedReportId} onValueChange={setSelectedReportId}>
              <SelectTrigger>
                <SelectValue placeholder={t("analysis.selectReport")} />
              </SelectTrigger>
              <SelectContent>
                {reports.map((report) => (
                  <SelectItem key={report.id} value={report.id}>
                    {`${report.scope || "scope"} · ${report.createdAt ? new Date(report.createdAt).toLocaleString() : t("analysis.unknownTime")}`}
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
                  <Badge>{t("analysis.totalScore", { score: selectedReport.scoreOverall ?? 0 })}</Badge>
                  <Badge variant="outline">{t("analysis.tokenCost", { cost: selectedReport.tokenCost ?? 0 })}</Badge>
                </div>
                <p className="text-muted-foreground">{selectedReport.summary || t("analysis.noSummary")}</p>
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{t("analysis.selectReportHint")}</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default AnalysisDashboard;
