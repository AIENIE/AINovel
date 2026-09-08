import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import type { NarrativeSource, NarrativeEvidence } from "@/types/narrative";

export function NarrativeEvidenceDialog({ source, evidence, onClose }: { source: NarrativeSource | null; evidence: NarrativeEvidence[]; onClose: () => void }) {
  const { t } = useTranslation();
  return <Dialog open={!!source} onOpenChange={open => { if (!open) onClose(); }}>
    <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{t("narrative.confirmedSource")}</DialogTitle>
        <DialogDescription>{source ? source.disclosedAt.chapterTitle + " · " + source.disclosedAt.sceneTitle : ""} — {t("narrative.historicalSource")}</DialogDescription>
      </DialogHeader>
      {source?.blocks.map(block => {
        const characters = Array.from(block.text);
        const spans = evidence.filter(e => e != null && e.blockId === block.id);
        return <div key={block.id} className="space-y-1">
          <div className="text-xs text-muted-foreground">{block.id}</div>
          <p className="whitespace-pre-wrap break-words leading-7">{characters.map((char, index) => {
            const matched = spans.some(e => e.start != null && e.end != null && index >= e.start && index < e.end);
            return matched ? <mark key={index} className="bg-yellow-200 text-black">{char}</mark> : char;
          })}</p>
        </div>;
      })}
    </DialogContent>
  </Dialog>;
}
