import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/mock-api";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Textarea } from "@/components/ui/textarea";

const TASKS = [
  { key: "draft_generation", label: "正文生成" },
  { key: "entity_extraction", label: "实体提取" },
  { key: "style_analysis", label: "风格分析" },
  { key: "beta_reader", label: "Beta Reader" },
  { key: "continuity_check", label: "连续性检查" },
  { key: "refine", label: "润色重写" },
] as const;

const formatMoney = (value: number) => {
  if (!Number.isFinite(value)) return "0.000000";
  return value.toFixed(6);
};

const toNumber = (value: any) => {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const ModelPreferences = () => {
  const { toast } = useToast();
  const [models, setModels] = useState<any[]>([]);
  const [prefs, setPrefs] = useState<any[]>([]);
  const [routing, setRouting] = useState<any[]>([]);
  const [usageSummary, setUsageSummary] = useState<any>(null);
  const [usageDetails, setUsageDetails] = useState<any[]>([]);
  const [stories, setStories] = useState<Story[]>([]);

  const [taskType, setTaskType] = useState("draft_generation");
  const [modelId, setModelId] = useState("");

  const [storyId, setStoryId] = useState("");
  const [compareTaskType, setCompareTaskType] = useState("draft_generation");
  const [comparePrompt, setComparePrompt] = useState("请围绕孤城守望这个主题，写一个120字的开场段落。");
  const [compareModelAId, setCompareModelAId] = useState("");
  const [compareModelBId, setCompareModelBId] = useState("");
  const [compareResult, setCompareResult] = useState<any>(null);
  const [adoptedCandidate, setAdoptedCandidate] = useState<{ slot: string; text: string; modelKey: string } | null>(null);

  const [estimatedInputTokens, setEstimatedInputTokens] = useState(800);
  const [estimatedOutputTokens, setEstimatedOutputTokens] = useState(1200);

  const loadData = async () => {
    const [modelList, prefList, summary, details, storyList, routingList] = await Promise.all([
      api.v2.models.list(),
      api.v2.models.listPreferences(),
      api.v2.models.usageSummary(),
      api.v2.models.usageDetails(),
      api.stories.list(),
      api.v2.models.listRouting().catch(() => []),
    ]);
    setModels(modelList);
    setPrefs(prefList);
    setUsageSummary(summary);
    setUsageDetails(details);
    setStories(storyList);
    setRouting(Array.isArray(routingList) ? routingList : []);

    if (!storyId && storyList.length > 0) setStoryId(storyList[0].id);
    if (!modelId && modelList.length > 0) setModelId(String(modelList[0].id));
    if (!compareModelAId && modelList.length > 0) setCompareModelAId(String(modelList[0].id));
    if (!compareModelBId && modelList.length > 1) setCompareModelBId(String(modelList[1].id));
  };

  useEffect(() => {
    loadData().catch((error: any) => toast({ variant: "destructive", title: "加载模型配置失败", description: error.message }));
  }, []);

  const currentPref = useMemo(() => prefs.find((pref) => pref.taskType === taskType), [prefs, taskType]);
  const currentRouting = useMemo(() => routing.find((item) => item.taskType === taskType), [routing, taskType]);

  const selectedModel = useMemo(() => {
    const prefModelId = currentPref?.preferredModelId ? String(currentPref.preferredModelId) : "";
    if (prefModelId) return models.find((model) => String(model.id) === prefModelId) || null;
    if (modelId) return models.find((model) => String(model.id) === String(modelId)) || null;
    return null;
  }, [currentPref, modelId, models]);

  const recommendedModel = useMemo(() => {
    const id = currentRouting?.recommendedModelId ? String(currentRouting.recommendedModelId) : "";
    return models.find((model) => String(model.id) === id) || null;
  }, [currentRouting, models]);

  const fallbackModel = useMemo(() => {
    const id = currentRouting?.fallbackModelId ? String(currentRouting.fallbackModelId) : "";
    return models.find((model) => String(model.id) === id) || null;
  }, [currentRouting, models]);

  const estimatedCost = useMemo(() => {
    if (!selectedModel) return 0;
    const inCost = (toNumber(selectedModel.costPer1kInput) * estimatedInputTokens) / 1000;
    const outCost = (toNumber(selectedModel.costPer1kOutput) * estimatedOutputTokens) / 1000;
    return inCost + outCost;
  }, [selectedModel, estimatedInputTokens, estimatedOutputTokens]);

  const usageByModel = useMemo(() => {
    const summary = new Map<string, { key: string; label: string; calls: number; input: number; output: number; cost: number }>();
    for (const item of usageDetails) {
      const modelId = String(item.modelId || "unknown");
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
      const task = String(item.taskType || "unknown");
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
      const date = String(item.createdAt || "").slice(0, 10) || "unknown";
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
      toast({ title: "模型偏好已更新" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "更新失败", description: error.message });
    }
  };

  const resetPreference = async () => {
    try {
      await api.v2.models.resetPreference(taskType);
      await loadData();
      toast({ title: "已恢复默认路由" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "重置失败", description: error.message });
    }
  };

  const compareModels = async () => {
    if (!storyId) {
      toast({ variant: "destructive", title: "请先选择故事" });
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
      toast({ title: "A/B 对比完成" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "对比失败", description: error.message });
    }
  };

  const pickCandidate = (candidate: any) => {
    const slot = String(candidate?.slot || "");
    const text = String(candidate?.text || "");
    const modelKey = String(candidate?.modelKey || candidate?.modelId || "-");
    setAdoptedCandidate({ slot, text, modelKey });
    toast({ title: `已采用结果 ${slot}` });
  };

  const copyAdopted = async () => {
    if (!adoptedCandidate?.text) return;
    try {
      await navigator.clipboard.writeText(adoptedCandidate.text);
      toast({ title: "已复制采用结果" });
    } catch {
      toast({ variant: "destructive", title: "复制失败，请手动复制文本" });
    }
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>模型偏好</CardTitle>
            <CardDescription>按任务覆盖系统路由，显示推荐与回退模型。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-2">
              <Label>任务类型</Label>
              <Select value={taskType} onValueChange={setTaskType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TASKS.map((task) => (
                    <SelectItem key={task.key} value={task.key}>
                      {task.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>偏好模型</Label>
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
              {recommendedModel ? <Badge variant="outline">推荐：{recommendedModel.displayName}</Badge> : <Badge variant="outline">推荐：未配置</Badge>}
              {fallbackModel ? <Badge variant="secondary">Fallback：{fallbackModel.displayName}</Badge> : <Badge variant="secondary">Fallback：未配置</Badge>}
              {currentPref?.preferredModelId ? <Badge>当前已覆盖</Badge> : <Badge variant="outline">使用系统默认</Badge>}
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="space-y-1">
                <Label>预估输入 Token</Label>
                <Input type="number" value={estimatedInputTokens} onChange={(event) => setEstimatedInputTokens(Math.max(0, Number(event.target.value || 0)))} />
              </div>
              <div className="space-y-1">
                <Label>预估输出 Token</Label>
                <Input type="number" value={estimatedOutputTokens} onChange={(event) => setEstimatedOutputTokens(Math.max(0, Number(event.target.value || 0)))} />
              </div>
            </div>

            <div className="rounded border p-2 text-xs">
              <div>模型：{selectedModel?.displayName || "未选择"}</div>
              <div>预估成本：{formatMoney(estimatedCost)}</div>
            </div>

            <div className="flex gap-2">
              <Button onClick={savePreference}>保存偏好</Button>
              <Button variant="outline" onClick={resetPreference}>
                恢复默认
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>用量统计</CardTitle>
            <CardDescription>按任务和模型查看调用与成本。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div className="rounded border p-2">
                <div className="text-muted-foreground">总调用</div>
                <div className="font-semibold">{usageSummary?.totalCalls ?? 0}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">总成本</div>
                <div className="font-semibold">{String(usageSummary?.totalCost ?? 0)}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">输入 Token</div>
                <div className="font-semibold">{usageSummary?.totalInputTokens ?? 0}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">输出 Token</div>
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
              {!usageDetails.length && <p className="text-sm text-muted-foreground">暂无调用记录</p>}
            </div>
            {!!usageDetails.length && (
              <div className="grid gap-2 xl:grid-cols-3">
                <div className="rounded border p-2 text-xs space-y-1">
                  <div className="font-medium">按模型</div>
                  {usageByModel.map((item) => (
                    <div key={item.key} className="flex items-center justify-between gap-2">
                      <span className="truncate">{item.label}</span>
                      <span>{`${item.calls} 次`}</span>
                    </div>
                  ))}
                </div>
                <div className="rounded border p-2 text-xs space-y-1">
                  <div className="font-medium">按任务类型</div>
                  {usageByTask.map((item) => (
                    <div key={item.key} className="flex items-center justify-between gap-2">
                      <span>{item.key}</span>
                      <span>{`${item.calls} 次`}</span>
                    </div>
                  ))}
                </div>
                <div className="rounded border p-2 text-xs space-y-1">
                  <div className="font-medium">按时间（近7天）</div>
                  {usageByDate.map((item) => (
                    <div key={item.date} className="flex items-center justify-between gap-2">
                      <span>{item.date}</span>
                      <span>{`${item.calls} 次`}</span>
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
          <CardTitle>模型 A/B 对比</CardTitle>
          <CardDescription>对同一任务并行生成两个结果并选择采用版本。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div className="space-y-2">
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
            <div className="space-y-2">
              <Label>任务类型</Label>
              <Select value={compareTaskType} onValueChange={setCompareTaskType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TASKS.map((task) => (
                    <SelectItem key={task.key} value={task.key}>
                      {task.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>模型 A</Label>
              <Select value={compareModelAId} onValueChange={setCompareModelAId}>
                <SelectTrigger>
                  <SelectValue placeholder="自动路由" />
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
              <Label>模型 B</Label>
              <Select value={compareModelBId} onValueChange={setCompareModelBId}>
                <SelectTrigger>
                  <SelectValue placeholder="自动回退" />
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
            A/B 对比会并行调用两个模型，预计成本约为单次生成的 2 倍。
          </div>
          <Button onClick={compareModels}>执行 A/B 对比</Button>

          {!!compareResult?.candidates?.length && (
            <div className="grid gap-3 md:grid-cols-2">
              {compareResult.candidates.map((item: any) => (
                <div
                  key={item.slot}
                  className={`rounded border p-3 space-y-2 ${adoptedCandidate?.slot === String(item.slot) ? "border-primary bg-primary/5" : ""}`}
                >
                  <div className="flex items-center justify-between">
                    <Badge>{item.slot}</Badge>
                    <Badge variant="outline">score {item.score ?? "-"}</Badge>
                  </div>
                  <div className="text-xs text-muted-foreground">模型：{String(item.modelKey || item.modelId || "-")}</div>
                  <p className="text-sm whitespace-pre-wrap">{item.text || "无结果"}</p>
                  <Button
                    size="sm"
                    variant={adoptedCandidate?.slot === String(item.slot) ? "default" : "secondary"}
                    onClick={() => pickCandidate(item)}
                  >
                    {adoptedCandidate?.slot === String(item.slot) ? "已采用" : "采用此结果"}
                  </Button>
                </div>
              ))}
            </div>
          )}

          {!!adoptedCandidate && (
            <div className="rounded border p-3 space-y-2">
              <div className="flex items-center justify-between">
                <div className="text-sm font-medium">{`已采用结果 ${adoptedCandidate.slot} · ${adoptedCandidate.modelKey}`}</div>
                <Button size="sm" variant="outline" onClick={copyAdopted}>
                  复制结果
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
