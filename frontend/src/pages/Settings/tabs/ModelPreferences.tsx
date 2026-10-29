import { featureName } from "@/lib/ai-model-config";
import AiFeaturePreferences from "./AiFeaturePreferences";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, type NetworkObject } from "@/lib/api-client";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { localizedErrorMessage } from "@/lib/error-messages";

const toNumber = (value: unknown) => {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new Error("MODEL_USAGE_INVALID");
  return value;
};

const ModelPreferences = () => {
  const { t, i18n } = useTranslation();
  const [models, setModels] = useState<NetworkObject[]>([]);
  const [usageSummary, setUsageSummary] = useState<NetworkObject | null>(null);
  const [usageDetails, setUsageDetails] = useState<NetworkObject[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    const [modelList, summary, details] = await Promise.all([
      api.v2.models.list(),
      api.v2.models.usageSummary(),
      api.v2.models.usageDetails(),
    ]);
    if (!Array.isArray(modelList) || !Array.isArray(details) || !summary || typeof summary !== "object") throw new Error("MODEL_USAGE_INVALID");
    for (const key of ["totalCalls", "totalCost", "totalInputTokens", "totalOutputTokens"]) toNumber(summary[key]);
    for (const item of details) for (const key of ["inputTokens", "outputTokens", "costEstimate"]) toNumber(item[key]);
    setModels(modelList);
    setUsageSummary(summary);
    setUsageDetails(details);

    setLoadError(null);
  }, []);

  const refresh = useCallback(() => loadData().catch((error: unknown) => setLoadError(localizedErrorMessage(error, "models.loadFailed"))), [loadData]);
  useEffect(() => { void refresh(); }, [refresh]);

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

  return (
    <div className="space-y-4">
      {loadError && <div role="alert" className="text-sm text-destructive">{loadError}<Button variant="link" onClick={() => void refresh()}>{t("common.retry")}</Button></div>}
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
                <div className="font-semibold">{usageSummary?.totalCalls ?? "—"}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("aiModels.actualCharges")}</div>
                <div className="font-semibold">{String(usageSummary?.totalCost ?? "—")}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.inputTokens")}</div>
                <div className="font-semibold">{usageSummary?.totalInputTokens ?? "—"}</div>
              </div>
              <div className="rounded border p-2">
                <div className="text-muted-foreground">{t("models.outputTokens")}</div>
                <div className="font-semibold">{usageSummary?.totalOutputTokens ?? "—"}</div>
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
              {usageSummary && !usageDetails.length && <p className="text-sm text-muted-foreground">{t("models.noUsage")}</p>}
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

    </div>
  );
};

export default ModelPreferences;
