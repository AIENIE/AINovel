import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { featureName, type AiFeature } from "@/lib/ai-model-config";
import { ModelPrice } from "@/components/ai/AiQuotePrompt";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { localizedErrorMessage } from "@/lib/error-messages";
import { AiPendingPayments } from "@/components/ai/AiPendingPayments";

export default function AiFeaturePreferences() {
  const { t, i18n } = useTranslation();
  const [features, setFeatures] = useState<AiFeature[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [saving, setSaving] = useState("");
  const load = async () => {
    setLoading(true); setError("");
    try { setFeatures(await api.modelConfig.features()); } catch (failure) { setError(localizedErrorMessage(failure)); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const save = async (key: string, value: string | null) => {
    setSaving(key); setError("");
    try { const updated = await api.modelConfig.preference(key, value); setFeatures((items) => items.map((item) => item.definition.key === key ? updated : item)); }
    catch (failure) { setError(localizedErrorMessage(failure)); } finally { setSaving(""); }
  };
  return <Card>
    <CardHeader><CardTitle>{t("aiModels.preferencesTitle")}</CardTitle><CardDescription>{t("aiModels.preferencesDescription")}</CardDescription></CardHeader>
    <CardContent className="space-y-4">
      <AiPendingPayments />
      <Input aria-label={t("aiModels.search")} placeholder={t("aiModels.search")} value={search} onChange={(e) => setSearch(e.target.value)} />
      {loading ? <p className="text-sm text-muted-foreground">{t("common.loading")}</p> : null}
      {error ? <div role="alert" className="flex flex-wrap items-center gap-2 text-sm text-destructive">{error}<Button size="sm" variant="outline" onClick={() => void load()}>{t("aiModels.reload")}</Button></div> : null}
      <div className="max-h-[65vh] space-y-3 overflow-y-auto pr-1">
        {features.filter((feature) => !feature.definition.retired && featureName(feature, i18n.language).toLowerCase().includes(search.toLowerCase())).map((feature) => {
          const key = feature.definition.key;
          const selectedId = feature.preferredOptionId ?? feature.defaultOptionId;
          const selected = feature.options.find((option) => option.optionId === selectedId);
          return <section key={key} className="space-y-2 rounded-lg border p-3">
            <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-medium">{featureName(feature, i18n.language)}</h3><Badge variant="outline">{t(feature.billingEnabled ? "aiModels.paid" : "aiModels.free")}</Badge></div>
            {feature.billingEnabled ? <div className="flex flex-wrap items-center gap-2"><select aria-label={featureName(feature, i18n.language)} className="h-10 min-w-0 flex-1 rounded-md border bg-background px-3 text-sm" disabled={saving === key} value={feature.preferenceInvalid ? "" : selectedId} onChange={(e) => void save(key, e.target.value)}>
              <option value="" disabled>{t("aiModels.chooseAgain")}</option>{feature.options.map((option) => <option key={option.optionId} value={option.optionId} disabled={feature.unavailableOptions.includes(option.optionId)}>{option.label}{option.optionId === feature.defaultOptionId ? ` · ${t("aiModels.default")}` : ""}</option>)}
            </select>{feature.preferredOptionId ? <Button variant="ghost" size="sm" disabled={saving === key} onClick={() => void save(key, null)}>{t("aiModels.reset")}</Button> : null}</div>
              : <p className="text-sm text-muted-foreground">{feature.options[0]?.label} · {t("aiModels.managed")}</p>}
            {selected ? <p className="break-all text-xs text-muted-foreground">{selected.modelKey}</p> : null}
            {feature.preferenceInvalid || feature.unavailableOptions.includes(selectedId) ? <p className="text-xs text-destructive">{t(feature.billingEnabled ? "aiModels.unavailable" : "aiModels.managedUnavailable")}</p> : selected ? <p className="text-xs leading-relaxed text-muted-foreground"><ModelPrice option={selected} /> · {t(`aiModels.thinking.${selected.thinkingMode}`)}{selected.thinkingEffort ? ` · ${selected.thinkingEffort}` : ""}</p> : null}
          </section>;
        })}
      </div>
    </CardContent>
  </Card>;
}
