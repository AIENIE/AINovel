import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { assistantTextDiff } from "@/lib/assistant-editor";
import { localizedErrorMessage } from "@/lib/error-messages";

export type WorldRefineTarget = { worldId: string; revision: number; moduleKey: string; fieldKey: string; label: string; original: string };

export function WorldRefineReview({ target, stale, onClose, onGenerate, onAccept }: {
  target: WorldRefineTarget; stale: boolean; onClose: () => void;
  onGenerate: (instruction: string) => Promise<string>; onAccept: (text: string) => void;
}) {
  const { t } = useTranslation();
  const [instruction, setInstruction] = useState("");
  const [candidate, setCandidate] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const diff = useMemo(() => candidate === null ? [] : assistantTextDiff(target.original, candidate), [target.original, candidate]);
  return <Dialog open onOpenChange={open => { if (!open && !busy) onClose(); }}>
    <DialogContent className="max-w-4xl max-h-[90dvh] overflow-y-auto">
      <DialogHeader><DialogTitle>{t("worlds.refineReview", { field: target.label })}</DialogTitle><DialogDescription>{t("worlds.refineReviewHelp")}</DialogDescription></DialogHeader>
      {stale && <p role="status" className="text-sm text-amber-700">{t("worlds.refineStale")}</p>}
      <label className="space-y-2 text-sm">{t("worlds.refineInstruction")}<Textarea value={instruction} onChange={event => setInstruction(event.target.value)} disabled={busy} /></label>
      <div className="grid gap-4 md:grid-cols-2">
        <section className="min-w-0 space-y-2"><h3 className="text-sm font-medium">{t("worlds.original")}</h3><p className="whitespace-pre-wrap break-words rounded border p-3 text-sm">{target.original}</p></section>
        {candidate !== null && <label className="min-w-0 space-y-2 text-sm">{t("worlds.candidate")}<Textarea className="min-h-48" value={candidate} disabled={busy} onChange={event => setCandidate(event.target.value)} /></label>}
      </div>
      {candidate !== null && <details open><summary>{t("worlds.fieldDiff")}</summary><div className="whitespace-pre-wrap break-words rounded border p-3 text-sm">{diff.map((part, index) => part.kind === "removed" ? <del key={index} className="bg-red-100 text-red-950">{part.text}</del> : part.kind === "added" ? <ins key={index} className="bg-green-100 text-green-950">{part.text}</ins> : <span key={index}>{part.text}</span>)}</div></details>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <DialogFooter className="gap-2">
        <Button variant="outline" disabled={busy} onClick={onClose}>{t("worlds.discardCandidate")}</Button>
        <Button variant="secondary" disabled={busy || stale} onClick={async () => { setBusy(true); setError(""); try { setCandidate(await onGenerate(instruction)); } catch (failure) { if (!(failure instanceof Error && failure.message === "AI_QUOTE_CANCELLED")) setError(localizedErrorMessage(failure)); } finally { setBusy(false); } }}>{busy ? t("common.loading") : t("worlds.generateCandidate")}</Button>
        {candidate !== null && <Button disabled={busy || stale || !candidate.trim()} onClick={() => onAccept(candidate)}>{t("worlds.adoptCandidate")}</Button>}
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}
