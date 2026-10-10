import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { MaterialTextDiff } from "./MaterialTextDiff";

export function MaterialRevisionHistory({ materialId, currentRevision }: { materialId: string; currentRevision: string }) {
  const { t } = useTranslation();
  const [rows,setRows] = useState<Awaited<ReturnType<typeof api.evidence.revisions>> | null>(null);
  const [raw,setRaw] = useState<Awaited<ReturnType<typeof api.evidence.rawRevision>> | null>(null);
  const [current,setCurrent] = useState<typeof raw>(null);
  const [page,setPage] = useState(0), [busy,setBusy] = useState(false), [error,setError] = useState("");
  const observation = useRef(0);
  useEffect(() => { observation.current += 1; setRows(null); setRaw(null); setCurrent(null); setError(""); setPage(0); setBusy(false); return () => { observation.current += 1; }; },[materialId,currentRevision]);
  const load = async (nextPage: number) => {
    const epoch=++observation.current; setBusy(true); setError("");
    try { const result = await api.evidence.revisions(materialId,nextPage); if(epoch === observation.current) { setRows(result); setPage(nextPage); } }
    catch(e:unknown) { if(epoch === observation.current) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { if(epoch === observation.current) setBusy(false); }
  };
  const open = async (revision: string) => {
    const epoch=++observation.current; setBusy(true); setError("");
    try { const [selected,latest] = await Promise.all([api.evidence.rawRevision(revision),api.evidence.rawRevision(currentRevision)]); if(epoch === observation.current) { setRaw(selected); setCurrent(latest); } }
    catch(e:unknown) { if(epoch === observation.current) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { if(epoch === observation.current) setBusy(false); }
  };
  return <details className="space-y-3 rounded-md border p-3"><summary className="cursor-pointer text-sm font-medium">{t("evidence.revisionHistory")}</summary>
    {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}
    {rows === null ? <Button size="sm" variant="outline" disabled={busy} onClick={() => void load(0)}>{t("evidence.loadRevisions")}</Button> : <>
      <div className="flex flex-wrap gap-2">{rows.map(row => <Button key={row.id} size="sm" variant="outline" disabled={busy} onClick={() => void open(row.id)}>{t("evidence.version", { version:row.version })} · {row.title}</Button>)}</div>
      <div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" disabled={busy || page === 0} onClick={() => void load(page-1)}>{t("evidence.previousPage")}</Button><Button size="sm" variant="outline" disabled={busy || rows.length < 50} onClick={() => void load(page+1)}>{t("evidence.nextPage")}</Button></div>
    </>}
    {raw && <div className="space-y-3"><p className="text-sm">{raw.title} · {t("evidence.version", { version:raw.version })}</p><details><summary className="cursor-pointer text-sm">{t("evidence.completeRaw")}</summary><pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs">{raw.content}</pre></details>
      {current && current.id !== raw.id && <><p className="text-sm">{t("evidence.diffVersions", { before:raw.version,after:current.version })}</p><MaterialTextDiff before={raw.content} after={current.content} /></>}
    </div>}
  </details>;
}
