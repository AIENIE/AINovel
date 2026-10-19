import { featureName } from "@/lib/ai-model-config";
import AiFeaturePreferences from "./AiFeaturePreferences";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, type NetworkObject } from "@/lib/api-client";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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

const toNumber = (value: NetworkObject) => {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const ModelPreferences = () => {
  const { toast } = useToast();
  const { t, i18n } = useTranslation();
  const [models, setModels] = useState<NetworkObject[]>([]);
  const [usageSummary, setUsageSummary] = useState<NetworkObject | null>(null);
  const [usageDetails, setUsageDetails] = useState<NetworkObject[]>([]);
  const [stories, setStories] = useState<Story[]>([]);


  const [storyId, setStoryId] = useState("");
  const [compareTaskType, setCompareTaskType] = useState("draft_generation");
  const [comparePrompt, setComparePrompt] = useState(t("models.comparePromptDefault"));
  const [compareModelAId, setCompareModelAId] = useState("");
  const [compareModelBId, setCompareModelBId] = useState("");
  const [compareResult, setCompareResult] = useState<NetworkObject | null>(null);
  const [adoptedCandidate, setAdoptedCandidate] = useState<{ slot: string; text: string; modelKey: string } | null>(null);


  const loadData = async () => {
    const [modelList, summary, details, storyList] = await Promise.all([
      api.v2.models.list(),
      api.v2.models.usageSummary(),
      api.v2.models.usageDetails(),
      api.stories.list(),
    ]);
    setModels(modelList);
    setUsageSummary(summary);
    setUsageDetails(details);
    setStories(storyList);

    if (!storyId && storyList.length > 0) setStoryId(storyList[0].id);
    if (!compareModelAId && modelList.length > 0) setCompareModelAId(String(modelList[0].id));
    if (!compareModelBId && modelList.length > 1) setCompareModelBId(String(modelList[1].id));
  };

  useEffect(() => {
    loadData().catch((error: NetworkObject) => toast({ variant: "destructive", title: t("models.loadFailed"), description: localizedErrorMessage(error, "models.loadFailed") }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const compareTaskUnavailable = Boolean(TASKS.find((task) => task.key === compareTaskType && "unavailable" in task));

  const usageByModel = useMemo(() => {
    const summary = new Map<string, { key: string; label: string; calls: number; input: number; output: number; cost: number }>();
    for (const item of usageDetails) {
      const modelId = String(item.optionId || item.modelId || "legacy");
      const model = models.find((entry) => String(entry.id) === modelId);
      const current = summary.get(modelId) || {
        key: modelId,
        label: item.modelLabel || model?.displayName || model?.modelKey || t("aiModels.historical"),
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
  }, [models, usageDetails, t]);

  const usageByTask = useMemo(() => {
    const summary = new Map<string, { key: string; calls: number; input: number; output: number; cost: number }>();
    for (const item of usageDetails) {
      const task = item.definition ? featureName({ definition: item.definition }, i18n.language) : String(item.taskType || t("aiModels.historical"));
      const current = summary.get(task) || { key: task, calls: 0, input: 0, output: 0, cost: 0 };
      current.calls += 1;
      current.input += toNumber(item.inputTokens);
      current.output += toNumber(item.outputTokens);
      current.cost += toNumber(item.costEstimate);
      summary.set(task, current);
    }
    return Array.from(summary.values()).sort((a, b) => b.calls - a.calls);
  }, [usageDetails, i18n.language, t]);

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
        <AiFeaturePreferences />

        <Card>
          <CardHeader>
            <CardTitle>{t("models.usageTitle")}</CardTitle>
            <CardDescription>{t("aiModels.usageDescription")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.totalCalls")}</div>
                <div className="font-semibold">{usageSummary?.totalCalls ?? 0}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("aiModels.actualCharges")}</div>
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
                    <span>{item.definition ? featureName({ definition: item.definition }, i18n.language) : item.taskType}</span>
                    <Badge variant="outline">{item.modelLabel || t("aiModels.historical")}</Badge>
                  </div>
                  <div className="text-muted-foreground">
                    {t("aiModels.usageLine", { input: item.inputTokens, output: item.outputTokens, cost: item.costEstimate || 0 })}{item.billingStatus === "RESULT_READY" ? ` · ${t("aiModels.pendingBilling")}` : ""}
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
          <CardDescription>{t("models.compareDesc")} · {t("aiModels.localComparison")}</CardDescription>
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
