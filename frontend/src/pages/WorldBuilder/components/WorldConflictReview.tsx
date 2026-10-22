import { useTranslation } from "react-i18next";
import type { WorldDetail, WorldModuleDefinition } from "@/types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

export function WorldConflictReview({ local, server, definitions, open, onOpenChange, onResolve }: {
  local: WorldDetail; server: WorldDetail; definitions: WorldModuleDefinition[];
  open: boolean; onOpenChange: (open: boolean) => void; onResolve: (keepLocal: boolean) => void;
}) {
  const { t } = useTranslation();
  const rows = ["name", "tagline", "creativeIntent", "themes", "notes"].map(key => ({
    key, label: t(`worldMeta.${key}`),
    local: key === "themes" ? local.themes.join(", ") : String(local[key as keyof WorldDetail] ?? ""),
    server: key === "themes" ? server.themes.join(", ") : String(server[key as keyof WorldDetail] ?? ""),
  }));
  const localModules = Object.fromEntries(local.modules.map(module => [module.key, module.fields]));
  const serverModules = Object.fromEntries(server.modules.map(module => [module.key, module.fields]));
  for (const moduleKey of new Set([...Object.keys(localModules), ...Object.keys(serverModules)])) {
    const definition = definitions.find(module => module.key === moduleKey);
    for (const fieldKey of new Set([...Object.keys(localModules[moduleKey] || {}), ...Object.keys(serverModules[moduleKey] || {})])) {
      rows.push({ key: `${moduleKey}.${fieldKey}`, label: `${definition?.label || moduleKey} / ${definition?.fields.find(field => field.key === fieldKey)?.label || fieldKey}`,
        local: localModules[moduleKey]?.[fieldKey] || "", server: serverModules[moduleKey]?.[fieldKey] || "" });
    }
  }
  const differences = rows.filter(row => row.local !== row.server);
  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="max-w-4xl max-h-[90dvh] overflow-y-auto">
      <DialogHeader><DialogTitle>{t("worlds.compareServer")}</DialogTitle><DialogDescription>{t("worlds.mergeHelp")}</DialogDescription></DialogHeader>
      <div className="space-y-4">{differences.length ? differences.map(row => <section key={row.key} className="space-y-2">
        <h3 className="font-medium">{row.label}</h3><div className="grid gap-3 md:grid-cols-2">
          {[{ label: t("worlds.localDraft"), value: row.local }, { label: t("worlds.serverDraft"), value: row.server }].map(item => <div key={item.label} className="min-w-0 rounded border p-3"><h4 className="mb-2 text-xs text-muted-foreground">{item.label}</h4><p className="whitespace-pre-wrap break-words text-sm">{item.value || t("worlds.emptyField")}</p></div>)}
        </div>
      </section>) : <p>{t("worlds.sameContent")}</p>}</div>
      <DialogFooter className="gap-2"><Button variant="outline" onClick={() => onResolve(false)}>{t("worlds.useServer")}</Button><Button onClick={() => onResolve(true)}>{t("worlds.keepLocalChanges")}</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
