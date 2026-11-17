import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { roleName, type ModelResponsibility } from "@/lib/ai-model-config";
import { ModelPrice } from "@/components/ai/AiQuotePrompt";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { localizedErrorMessage } from "@/lib/error-messages";
import { AiPendingPayments } from "@/components/ai/AiPendingPayments";

export default function RolePreferences() {
  const { t, i18n } = useTranslation();
  const [roles, setRoles] = useState<ModelResponsibility[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState("");
  const load = async () => {
    setLoading(true); setError("");
    try { setRoles(await api.modelConfig.responsibilities()); } catch (failure) { setError(localizedErrorMessage(failure)); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const save = async (key: string, value: string | null) => {
    setSaving(key); setError("");
    try { const updated = await api.modelConfig.preference(key, value); setRoles((items) => items.map((item) => item.definition.key === key ? updated : item)); }
    catch (failure) { setError(localizedErrorMessage(failure)); } finally { setSaving(""); }
  };
  return <Card>
    <CardHeader><CardTitle>{t("aiModels.preferencesTitle")}</CardTitle><CardDescription>{t("aiModels.preferencesDescription")}</CardDescription></CardHeader>
    <CardContent className="space-y-4">
      <AiPendingPayments />
      {loading ? <p className="text-sm text-muted-foreground">{t("common.loading")}</p> : null}
      {error ? <div role="alert" className="flex flex-wrap items-center gap-2 text-sm text-destructive">{error}<Button size="sm" variant="outline" onClick={() => void load()}>{t("aiModels.reload")}</Button></div> : null}
      <div className="max-h-[65vh] space-y-3 overflow-y-auto pr-1">
        {roles.map((role) => {
          const key = role.definition.key;
          const selectedId = role.preferredOptionId ?? role.defaultOptionId;
          const selected = role.options.find((option) => option.optionId === selectedId);
          return <section key={key} className="space-y-2 rounded-lg border p-3">
            <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-medium">{roleName(role, i18n.language)}</h3><Badge variant="outline">{t(role.billingEnabled ? "aiModels.paid" : "aiModels.free")}</Badge></div>
            {role.options.length === 0 ? <p className="text-sm text-muted-foreground">{t("aiModels.unconfigured")}</p> : role.billingEnabled ? <div className="flex flex-wrap items-center gap-2"><select aria-label={roleName(role, i18n.language)} className="max-w-full h-10 min-w-0 flex-1 rounded-md border bg-background px-3 text-sm" disabled={saving === key} value={role.preferenceInvalid ? "" : selectedId ?? ""} onChange={(e) => void save(key, e.target.value)}>
              <option value="" disabled>{t("aiModels.chooseAgain")}</option>{role.options.map((option) => <option key={option.optionId} value={option.optionId} disabled={role.unavailableOptions.includes(option.optionId)}>{option.label}{option.optionId === role.defaultOptionId ? ` · ${t("aiModels.default")}` : ""}</option>)}
            </select>{role.preferredOptionId ? <Button variant="ghost" size="sm" disabled={saving === key} onClick={() => void save(key, null)}>{t("aiModels.reset")}</Button> : null}</div>
              : <p className="text-sm text-muted-foreground">{role.options[0]?.label} · {t("aiModels.managed")}</p>}
            {selected ? <p className="break-all text-xs text-muted-foreground">{selected.modelKey}</p> : null}
            {role.preferenceInvalid || (selectedId !== null && role.unavailableOptions.includes(selectedId)) ? <p className="text-xs text-destructive">{t(role.billingEnabled ? "aiModels.unavailable" : "aiModels.managedUnavailable")}</p> : selected ? <p className="text-xs leading-relaxed text-muted-foreground"><ModelPrice option={selected} /> · {t(`aiModels.thinking.${selected.thinkingMode}`)}{selected.thinkingEffort ? ` · ${selected.thinkingEffort}` : ""}</p> : null}
          </section>;
        })}
      </div>
    </CardContent>
  </Card>;
}
