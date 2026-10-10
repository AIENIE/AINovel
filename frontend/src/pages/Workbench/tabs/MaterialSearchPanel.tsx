import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { EvidenceHit, EvidenceSettings } from "@/lib/api/domains/material-evidence";
import type { Material } from "@/types";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

export default function MaterialSearchPanel({ storyId = "" }: { storyId?: string }) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [mode, setMode] = useState<"fact" | "inspiration">("fact");
  const [scope, setScope] = useState<"bound" | "personal" | "public">("bound");
  const [results, setResults] = useState<EvidenceHit[]>([]);
  const [resultMode, setResultMode] = useState<"fact" | "inspiration" | null>(null);
  const [degradation, setDegradation] = useState<string[]>([]);
  const [settings, setSettings] = useState<EvidenceSettings | null>(null);
  const [materials, setMaterials] = useState<Material[]>([]);
  const [bindings, setBindings] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<EvidenceHit | null>(null);
  const [entities, setEntities] = useState<Awaited<ReturnType<typeof api.evidence.entities>> | null>(null);
  const [entityPage, setEntityPage] = useState(0);
  const [entityId, setEntityId] = useState("");
  const [sourcePage, setSourcePage] = useState(0);
  const request = useRef<AbortController | null>(null);
  const mutation = useRef(false);
  const intent = useRef<{ hash: string; key: string } | null>(null);
  const storyEpoch = useRef(0);
  const sourceObservation = useRef(0);
  useEffect(() => {
    let active = true; storyEpoch.current += 1; sourceObservation.current += 1; request.current?.abort(); setResults([]); setResultMode(null); setDegradation([]); setSelected(null); setSettings(null); setError(""); setBusy(false); setEntities(null); setEntityId(""); setEntityPage(0); setSourcePage(0);
    if (storyId) Promise.all([api.evidence.settings(storyId), api.materials.list()]).then(([config, list]) => {
      if (active) { setSettings(config); setBindings(config.bindings); setMaterials(list); }
    }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : t("errors.loadFailed")); });
    return () => { active = false; storyEpoch.current += 1; sourceObservation.current += 1; request.current?.abort(); };
  }, [storyId, t]);
  const search = async () => {
    if (!storyId || !query.trim()) return;
    resetSearch(); const control = new AbortController(); request.current = control; setBusy(true);
    try {
      const result = await api.evidence.search({ storyId, query, mode, scope, limit: 8 }, control.signal);
      if (!control.signal.aborted) { setResults(result.items); setResultMode(mode); setDegradation(result.degradation); }
    } catch (e: unknown) { if (!control.signal.aborted) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { if (request.current === control) setBusy(false); }
  };
  const resetSearch = () => {
    request.current?.abort(); request.current = null; sourceObservation.current += 1;
    setResults([]); setResultMode(null); setDegradation([]); setSelected(null); setEntityId(""); setBusy(false); setError("");
  };
  const loadEntities = async (page: number) => {
    const epoch = storyEpoch.current; setBusy(true); setError("");
    try { const rows = await api.evidence.entities(page); if (epoch === storyEpoch.current) { setEntities(rows); setEntityPage(page); } }
    catch (e: unknown) { if (epoch === storyEpoch.current) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { if (epoch === storyEpoch.current) setBusy(false); }
  };
  const listEntitySources = async (id: string, page: number) => {
    request.current?.abort(); const control = new AbortController(); request.current = control; setBusy(true); setError("");
    const epoch = storyEpoch.current;
    try { const rows = await api.evidence.entitySources(id, storyId, page); if (epoch === storyEpoch.current && !control.signal.aborted) { setResults(rows); setResultMode("fact"); setEntityId(id); setSourcePage(page); setDegradation([]); setSelected(null); } }
    catch (e: unknown) { if (epoch === storyEpoch.current && !control.signal.aborted) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { if (request.current === control) setBusy(false); }
  };
  const saveBindings = async () => {
    if (!settings || mutation.current) return;
    mutation.current = true; setSaving(true); setError("");
    const epoch = storyEpoch.current;
    const payload = { expectedVersion: settings.version, semanticProfile: settings.semanticProfile, rerank: settings.rerank, hints: settings.hints, checks: settings.checks, bindings };
    const hash = JSON.stringify({ storyId, payload });
    if (intent.current?.hash !== hash) intent.current = { hash, key: crypto.randomUUID() };
    try { const result = await api.evidence.saveSettings(storyId, { ...payload, requestKey: intent.current.key }); if (storyEpoch.current === epoch) { setSettings(result); intent.current = null; } }
    catch (e: unknown) { if (storyEpoch.current === epoch) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { mutation.current = false; setSaving(false); }
  };
  const openSource = async (hit: EvidenceHit) => {
    const epoch = storyEpoch.current;
    const observation = ++sourceObservation.current;
    try {
      const source = await api.evidence.open(hit.chunkId);
      if (epoch === storyEpoch.current && observation === sourceObservation.current) setSelected(source);
    } catch (e: unknown) {
      if (epoch === storyEpoch.current && observation === sourceObservation.current) setError(e instanceof Error ? e.message : t("errors.loadFailed"));
    }
  };
  if (!storyId) return <p className="p-6 text-muted-foreground">{t("evidence.selectStory")}</p>;
  return <div className="mx-auto max-w-4xl space-y-5 pb-8">
    <h2 className="text-xl font-semibold">{t("evidence.searchTitle")}</h2>
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <Label className="space-y-2">{t("evidence.mode")}<select aria-label={t("evidence.mode")} className="block w-full rounded-md border bg-background p-2" value={mode} onChange={e => { resetSearch(); setMode(e.target.value as typeof mode); }}><option value="fact">{t("evidence.fact")}</option><option value="inspiration">{t("evidence.inspiration")}</option></select></Label>
      <Label className="space-y-2">{t("evidence.scope")}<select aria-label={t("evidence.scope")} className="block w-full rounded-md border bg-background p-2" value={scope} onChange={e => { resetSearch(); setScope(e.target.value as typeof scope); }}><option value="bound">{t("evidence.bound")}</option><option value="personal">{t("evidence.personal")}</option><option value="public">{t("evidence.public")}</option></select></Label>
    </div>
    <form className="flex gap-2" onSubmit={e => { e.preventDefault(); void search(); }}><Input aria-label={t("evidence.query")} value={query} maxLength={2000} onChange={e => setQuery(e.target.value)} placeholder={t("materialSearch.searchPlaceholder")} /><Button disabled={busy || !query.trim()}>{busy ? t("common.loading") : t("evidence.search")}</Button></form>
    <details className="rounded-md border p-3"><summary className="cursor-pointer font-medium">{t("evidence.entityDirectory")}</summary><p className="my-2 text-sm text-muted-foreground">{t("evidence.entityDirectoryHelp")}</p>
      {entities === null ? <Button size="sm" variant="outline" disabled={busy} onClick={() => void loadEntities(0)}>{t("evidence.entityDirectory")}</Button> : <>
        <div className="flex flex-wrap gap-2">{entities.map(entity => <Button key={entity.id} size="sm" variant={entity.id === entityId ? "default" : "outline"} disabled={busy} onClick={() => void listEntitySources(entity.id, 0)}>{entity.name}{entity.aliases.filter(alias => alias !== entity.name).length > 0 && ` (${entity.aliases.filter(alias => alias !== entity.name).join(" / ")})`}</Button>)}</div>
        {!entities.length && <p className="text-sm text-muted-foreground">{t("evidence.emptyEntities")}</p>}
        <div className="mt-3 flex gap-2"><Button size="sm" variant="outline" disabled={busy || entityPage === 0} onClick={() => void loadEntities(entityPage - 1)}>{t("evidence.previousPage")}</Button><span className="self-center text-sm">{t("evidence.page", { number: entityPage + 1 })}</span><Button size="sm" variant="outline" disabled={busy || entities.length < 50} onClick={() => void loadEntities(entityPage + 1)}>{t("evidence.nextPage")}</Button></div>
      </>}
    </details>
    {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}
    {degradation.map(message => <p key={message} role="status" className="text-sm text-muted-foreground">{message}</p>)}
    {resultMode === "inspiration" && results.length > 0 && <p className="text-sm text-muted-foreground">{t("evidence.similarHelp")}</p>}
    {!busy && !results.length && <p className="text-sm text-muted-foreground">{t("evidence.noResult")}</p>}
    <div className="space-y-3">{results.map(hit => <article key={hit.chunkId} className="min-w-0 border-b py-3">
      <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-medium break-words">{hit.title}</h3><span className="text-xs text-muted-foreground">{resultMode === "inspiration" && <>{t("evidence.similarSource")} · </>}{t("evidence.version", { version: hit.sourceVersion })}</span></div>
      <blockquote className="my-2 whitespace-pre-wrap break-words border-l-2 pl-3 text-sm">{hit.text}</blockquote>
      <p className="mb-2 text-xs text-muted-foreground">{hit.reasons.join(" · ")} · {t("evidence.position", { start: hit.start, end: hit.end })}</p>
      <div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" onClick={() => void openSource(hit)}>{t("evidence.open")}</Button>
        <Button size="sm" variant="outline" disabled={bindings.includes(hit.materialId)} onClick={() => setBindings(previous => [...previous, hit.materialId])}>{bindings.includes(hit.materialId) ? t("evidence.bound") : t("evidence.bind")}</Button></div>
    </article>)}</div>
    {entityId && <div className="flex gap-2"><Button size="sm" variant="outline" disabled={busy || sourcePage === 0} onClick={() => void listEntitySources(entityId, sourcePage - 1)}>{t("evidence.previousPage")}</Button><span className="self-center text-sm">{t("evidence.page", { number: sourcePage + 1 })}</span><Button size="sm" variant="outline" disabled={busy || results.length < 50} onClick={() => void listEntitySources(entityId, sourcePage + 1)}>{t("evidence.nextPage")}</Button></div>}
    {selected && <section className="rounded-md border p-4"><div className="flex justify-between gap-2"><h3 className="font-medium">{selected.title}</h3><Button variant="ghost" size="sm" onClick={() => { sourceObservation.current += 1; setSelected(null); }}>{t("common.close")}</Button></div><p className="mt-3 whitespace-pre-wrap break-words text-sm">{selected.text}</p><p className="mt-2 text-xs text-muted-foreground">{t("evidence.position", { start: selected.start, end: selected.end })}</p></section>}
    {settings && <details className="rounded-md border p-4" open><summary className="cursor-pointer font-medium">{t("evidence.bindings")}</summary><p className="my-2 text-sm text-muted-foreground">{t("evidence.bindingHelp")}</p>
      <div className="max-h-64 space-y-2 overflow-auto">{materials.filter(m => m.status === "approved").map(m => <label key={m.id} className="flex items-start gap-2 text-sm"><input type="checkbox" checked={bindings.includes(m.id)} onChange={e => setBindings(previous => e.target.checked ? [...new Set([...previous, m.id])] : previous.filter(id => id !== m.id))} /><span className="break-words">{m.title}</span></label>)}</div>
      {bindings.filter(id => !materials.some(material => material.id === id && material.status === "approved")).map((id, index) => <div key={id} className="mt-2 flex items-center justify-between gap-2 text-sm"><span>{results.find(hit => hit.materialId === id)?.title ?? t("evidence.otherBinding", { number: index + 1 })}</span><Button size="sm" variant="ghost" onClick={() => setBindings(previous => previous.filter(value => value !== id))}>{t("evidence.removeBinding")}</Button></div>)}
      <Button className="mt-3" disabled={saving} onClick={() => void saveBindings()}>{saving ? t("common.loading") : t("evidence.saveBindings")}</Button>
    </details>}
  </div>;
}
