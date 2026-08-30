import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, type NetworkObject } from "@/lib/api-client";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Textarea } from "@/components/ui/textarea";
import { localizedErrorMessage } from "@/lib/error-messages";

const TASKS = [
  { key: "draft_generation", labelKey: "models.taskDraft" },
  { key: "entity_extraction", labelKey: "models.taskEntity" },
  { key: "style_analysis", labelKey: "models.taskStyle" },
  { key: "beta_reader", labelKey: "models.taskBetaReader", unavailable: true },
  { key: "continuity_check", labelKey: "models.taskContinuity", unavailable: true },
  { key: "refine", labelKey: "models.taskRefine" },
] as const;

const formatMoney = (value: number) => {
  if (!Number.isFinite(value)) return "0.000000";
  return value.toFixed(6);
};

const toNumber = (value: NetworkObject) => {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const ModelPreferences = () => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [models, setModels] = useState<NetworkObject[]>([]);
  const [prefs, setPrefs] = useState<NetworkObject[]>([]);
  const [usageSummary, setUsageSummary] = useState<NetworkObject | null>(null);
  const [usageDetails, setUsageDetails] = useState<NetworkObject[]>([]);
  const [stories, setStories] = useState<Story[]>([]);

  const [taskType, setTaskType] = useState("draft_generation");
  const [modelId, setModelId] = useState("");

  const [storyId, setStoryId] = useState("");
  const [compareTaskType, setCompareTaskType] = useState("draft_generation");
  const [comparePrompt, setComparePrompt] = useState(t("models.comparePromptDefault"));
  const [compareModelAId, setCompareModelAId] = useState("");
  const [compareModelBId, setCompareModelBId] = useState("");
  const [compareResult, setCompareResult] = useState<NetworkObject | null>(null);
  const [adoptedCandidate, setAdoptedCandidate] = useState<{ slot: string; text: string; modelKey: string } | null>(null);

  const [estimatedInputTokens, setEstimatedInputTokens] = useState(800);
  const [estimatedOutputTokens, setEstimatedOutputTokens] = useState(1200);

  const loadData = async () => {
    const [modelList, prefList, summary, details, storyList] = await Promise.all([
      api.v2.models.list(),
      api.v2.models.listPreferences(),
      api.v2.models.usageSummary(),
      api.v2.models.usageDetails(),
      api.stories.list(),
    ]);
    setModels(modelList);
    setPrefs(prefList);
    setUsageSummary(summary);
    setUsageDetails(details);
    setStories(storyList);

    if (!storyId && storyList.length > 0) setStoryId(storyList[0].id);
    if (!modelId && modelList.length > 0) setModelId(String(modelList[0].id));
    if (!compareModelAId && modelList.length > 0) setCompareModelAId(String(modelList[0].id));
    if (!compareModelBId && modelList.length > 1) setCompareModelBId(String(modelList[1].id));
  };

  useEffect(() => {
    loadData().catch((error: NetworkObject) => toast({ variant: "destructive", title: t("models.loadFailed"), description: localizedErrorMessage(error, "models.loadFailed") }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const currentPref = useMemo(() => prefs.find((pref) => pref.taskType === taskType), [prefs, taskType]);
  const taskUnavailable = Boolean(TASKS.find((task) => task.key === taskType && "unavailable" in task));
  const compareTaskUnavailable = Boolean(TASKS.find((task) => task.key === compareTaskType && "unavailable" in task));

  const selectedModel = useMemo(() => {
    const prefModelId = currentPref?.preferredModelId ? String(currentPref.preferredModelId) : "";
    if (prefModelId) return models.find((model) => String(model.id) === prefModelId) || null;
    if (modelId) return models.find((model) => String(model.id) === String(modelId)) || null;
    return null;
  }, [currentPref, modelId, models]);

  const estimatedCost = useMemo(() => {
    if (!selectedModel) return 0;
    const inCost = (toNumber(selectedModel.costPer1kInput) * estimatedInputTokens) / 1000;
    const outCost = (toNumber(selectedModel.costPer1kOutput) * estimatedOutputTokens) / 1000;
    return inCost + outCost;
  }, [selectedModel, estimatedInputTokens, estimatedOutputTokens]);

  const usageByModel = useMemo(() => {
    const summary = new Map<string, { key: string; label: string; calls: number; input: number; output: number; cost: number }>();
    for (const item of usageDetails) {
      const modelId = String(item.modelId || "NetworkObject");
      const model = models.find((entry) => String(entry.id) === modelId);
      const current = summary.get(modelId) || {
        key: modelId,
        label: model?.displayName || model?.modelKey || modelId.slice(0, 8),
        calls: 0,
        input: 0,
        output: 0,
        cost: 0,
      };
      current.calls += 1;
      current.input += toNumber(item.inputTokens);
      current.output += toNumber(item.outputTokens);
      current.cost += toNumber(item.costEstimate);
      summary.set(modelId, current);
    }
    return Array.from(summary.values()).sort((a, b) => b.calls - a.calls);
  }, [models, usageDetails]);

  const usageByTask = useMemo(() => {
    const summary = new Map<string, { key: string; calls: number; input: number; output: number; cost: number }>();
    for (const item of usageDetails) {
      const task = String(item.taskType || "NetworkObject");
      const current = summary.get(task) || { key: task, calls: 0, input: 0, output: 0, cost: 0 };
      current.calls += 1;
      current.input += toNumber(item.inputTokens);
      current.output += toNumber(item.outputTokens);
      current.cost += toNumber(item.costEstimate);
      summary.set(task, current);
    }
    return Array.from(summary.values()).sort((a, b) => b.calls - a.calls);
  }, [usageDetails]);

  const usageByDate = useMemo(() => {
    const summary = new Map<string, { date: string; calls: number; tokens: number; cost: number }>();
    for (const item of usageDetails) {
      const date = String(item.createdAt || "").slice(0, 10) || "NetworkObject";
      const current = summary.get(date) || { date, calls: 0, tokens: 0, cost: 0 };
      current.calls += 1;
      current.tokens += toNumber(item.inputTokens) + toNumber(item.outputTokens);
      current.cost += toNumber(item.costEstimate);
      summary.set(date, current);
    }
    return Array.from(summary.values())
      .sort((a, b) => (a.date < b.date ? 1 : -1))
      .slice(0, 7);
  }, [usageDetails]);

  const savePreference = async () => {
    try {
      await api.v2.models.setPreference(taskType, modelId || null);
      await loadData();
      toast({ title: t("models.updated") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
    }
  };

  const resetPreference = async () => {
    try {
      await api.v2.models.resetPreference(taskType);
      await loadData();
      toast({ title: t("models.resetDefault") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("models.resetFailed"), description: localizedErrorMessage(error, "models.resetFailed") });
    }
  };

  const compareModels = async () => {
    if (!storyId) {
      toast({ variant: "destructive", title: t("models.selectStoryFirst") });
      return;
    }
    try {
      const result = await api.v2.models.compare(storyId, {
        taskType: compareTaskType,
        prompt: comparePrompt,
        modelAId: compareModelAId || undefined,
        modelBId: compareModelBId || undefined,
      });
      setCompareResult(result);
      setAdoptedCandidate(null);
      await loadData();
      toast({ title: t("models.compareDone") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("models.compareFailed"), description: localizedErrorMessage(error, "models.compareFailed") });
    }
  };

  const pickCandidate = (candidate: NetworkObject) => {
    const slot = String(candidate?.slot || "");
    const text = String(candidate?.text || "");
    const modelKey = String(candidate?.modelKey || candidate?.modelId || "-");
    setAdoptedCandidate({ slot, text, modelKey });
    toast({ title: t("models.adoptedResult", { slot }) });
  };

  const copyAdopted = async () => {
    if (!adoptedCandidate?.text) return;
    try {
      await navigator.clipboard.writeText(adoptedCandidate.text);
      toast({ title: t("models.copiedResult") });
    } catch {
      toast({ variant: "destructive", title: t("models.copyFailed") });
    }
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("models.title")}</CardTitle>
            <CardDescription>{t("models.desc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-2">
              <Label>{t("models.taskType")}</Label>
              <Select value={taskType} onValueChange={setTaskType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TASKS.map((task) => (
                    <SelectItem key={task.key} value={task.key} disabled={"unavailable" in task}>
                      {t(task.labelKey)}{"unavailable" in task ? ` · ${t("common.notImplemented")}` : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>{t("models.preferredModel")}</Label>
              <Select value={modelId} onValueChange={setModelId}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {models.map((model) => (
                    <SelectItem key={model.id} value={String(model.id)}>
                      {model.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-wrap gap-2 text-xs">
              {currentPref?.preferredModelId ? <Badge>{t("models.overridden")}</Badge> : <Badge variant="outline">{t("models.systemDefault")}</Badge>}
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="space-y-1">
                <Label>{t("models.estInputTokens")}</Label>
                <Input type="number" value={estimatedInputTokens} onChange={(event) => setEstimatedInputTokens(Math.max(0, Number(event.target.value || 0)))} />
              </div>
              <div className="space-y-1">
                <Label>{t("models.estOutputTokens")}</Label>
                <Input type="number" value={estimatedOutputTokens} onChange={(event) => setEstimatedOutputTokens(Math.max(0, Number(event.target.value || 0)))} />
              </div>
            </div>

            <div className="rounded border p-2 text-xs">
              <div>{t("models.modelLabel")}：{selectedModel?.displayName || t("models.noneSelected")}</div>
              <div>{t("models.estCost")}：{formatMoney(estimatedCost)}</div>
            </div>

            <div className="flex gap-2">
              <Button onClick={savePreference} disabled={taskUnavailable}>{t("models.savePreference")}</Button>
              <Button variant="outline" onClick={resetPreference} disabled={taskUnavailable}>
                {t("models.restoreDefault")}
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("models.usageTitle")}</CardTitle>
            <CardDescription>{t("models.usageDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.totalCalls")}</div>
                <div className="font-semibold">{usageSummary?.totalCalls ?? 0}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.totalCost")}</div>
                <div className="font-semibold">{String(usageSummary?.totalCost ?? 0)}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.inputTokens")}</div>
                <div className="font-semibold">{usageSummary?.totalInputTokens ?? 0}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.outputTokens")}</div>
                <div className="font-semibold">{usageSummary?.totalOutputTokens ?? 0}</div>
              </div>
            </div>
            <div className="space-y-2">
              {usageDetails.slice(0, 8).map((item) => (
                <div key={item.id} className="rounded border p-2 text-xs space-y-1">
                  <div className="flex items-center justify-between">
                    <span>{item.taskType}</span>
                    <Badge variant="outline">{item.modelId ? String(item.modelId).slice(0, 8) : "-"}</Badge>
                  </div>
                  <div className="text-muted-foreground">
                    in:{item.inputTokens} / out:{item.outputTokens} / cost:{String(item.costEstimate || 0)}
                  </div>
                </div>
              ))}
              {!usageDetails.length && <p className="text-sm text-muted-foreground">{t("models.noUsage")}</p>}
            </div>
            {!!usageDetails.length && (
              <div className="grid gap-2 xl:grid-cols-3">
                <div className="rounded border p-2 text-xs space-y-1">
                  <div className="font-medium">{t("models.byModel")}</div>
                  {usageByModel.map((item) => (
                    <div key={item.key} className="flex items-center justify-between gap-2">
                      <span className="truncate">{item.label}</span>
                      <span>{t("models.callCount", { count: item.calls })}</span>
                    </div>
                  ))}
                </div>
                <div className="rounded border p-2 text-xs space-y-1">
                  <div className="font-medium">{t("models.byTask")}</div>
                  {usageByTask.map((item) => (
                    <div key={item.key} className="flex items-center justify-between gap-2">
                      <span>{item.key}</span>
                      <span>{t("models.callCount", { count: item.calls })}</span>
                    </div>
                  ))}
                </div>
                <div className="rounded border p-2 text-xs space-y-1">
                  <div className="font-medium">{t("models.byDate")}</div>
                  {usageByDate.map((item) => (
                    <div key={item.date} className="flex items-center justify-between gap-2">
                      <span>{item.date}</span>
                      <span>{t("models.callCount", { count: item.calls })}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("models.compareTitle")}</CardTitle>
          <CardDescription>{t("models.compareDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div className="space-y-2">
              <Label>{t("models.story")}</Label>
              <Select value={storyId} onValueChange={setStoryId}>
                <SelectTrigger>
                  <SelectValue placeholder={t("models.selectStory")} />
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
            <div className="space-y-2">
              <Label>{t("models.taskType")}</Label>
              <Select value={compareTaskType} onValueChange={setCompareTaskType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TASKS.map((task) => (
                    <SelectItem key={task.key} value={task.key} disabled={"unavailable" in task}>
                      {t(task.labelKey)}{"unavailable" in task ? ` · ${t("common.notImplemented")}` : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t("models.modelA")}</Label>
              <Select value={compareModelAId} onValueChange={setCompareModelAId}>
                <SelectTrigger>
                  <SelectValue placeholder={t("models.autoRoute")} />
                </SelectTrigger>
                <SelectContent>
                  {models.map((model) => (
                    <SelectItem key={`a-${model.id}`} value={String(model.id)}>
                      {model.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t("models.modelB")}</Label>
              <Select value={compareModelBId} onValueChange={setCompareModelBId}>
                <SelectTrigger>
                  <SelectValue placeholder={t("models.autoFallback")} />
                </SelectTrigger>
                <SelectContent>
                  {models.map((model) => (
                    <SelectItem key={`b-${model.id}`} value={String(model.id)}>
                      {model.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-2">
            <Label>Prompt</Label>
            <Textarea className="min-h-[120px]" value={comparePrompt} onChange={(event) => setComparePrompt(event.target.value)} />
          </div>

          <div className="rounded border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            {t("models.compareNotice")}
          </div>
          <Button onClick={compareModels} disabled={compareTaskUnavailable}>{t("models.runCompare")}</Button>

          {!!compareResult?.candidates?.length && (
            <div className="grid gap-3 md:grid-cols-2">
              {compareResult.candidates.map((item: NetworkObject) => (
                <div
                  key={item.slot}
                  className={`rounded border p-3 space-y-2 ${adoptedCandidate?.slot === String(item.slot) ? "border-primary bg-primary/5" : ""}`}
                >
                  <div className="flex items-center justify-between">
                    <Badge>{item.slot}</Badge>
                    <Badge variant="outline">score {item.score ?? "-"}</Badge>
                  </div>
                  <div className="text-xs text-muted-foreground">{t("models.modelLabel")}：{String(item.modelKey || item.modelId || "-")}</div>
                  <p className="text-sm whitespace-pre-wrap">{item.text || t("models.noResult")}</p>
                  <Button
                    size="sm"
                    variant={adoptedCandidate?.slot === String(item.slot) ? "default" : "secondary"}
                    onClick={() => pickCandidate(item)}
                  >
                    {adoptedCandidate?.slot === String(item.slot) ? t("models.adopted") : t("models.adoptThis")}
                  </Button>
                </div>
              ))}
            </div>
          )}

          {!!adoptedCandidate && (
            <div className="rounded border p-3 space-y-2">
              <div className="flex items-center justify-between">
                <div className="text-sm font-medium">{t("models.adoptedResult", { slot: adoptedCandidate.slot })} · {adoptedCandidate.modelKey}</div>
                <Button size="sm" variant="outline" onClick={copyAdopted}>
                  {t("models.copyResult")}
                </Button>
              </div>
              <p className="text-sm whitespace-pre-wrap text-muted-foreground">{adoptedCandidate.text}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default ModelPreferences;
