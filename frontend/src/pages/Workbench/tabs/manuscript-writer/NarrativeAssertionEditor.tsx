import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import type { NarrativeAssertion, NarrativeKind, NarrativeRecord, NarrativeSource } from "@/types/narrative";

type Props = {
  value: NarrativeAssertion; onChange: (value: NarrativeAssertion) => void;
  characters: Array<{ id: string; name: string }>; records: NarrativeRecord[];
  source?: NarrativeSource; disabled?: boolean;
};
const selectClass = "w-full rounded-md border bg-background px-2 py-2 text-sm";
export function NarrativeAssertionEditor({ value: supplied, onChange, characters, records, source, disabled }: Props) {
  const { t } = useTranslation();
  const id = useId();
  // Invalid model candidates remain reviewable; only the server can accept their evidence.
  const evidence = (Array.isArray(supplied.evidence) ? supplied.evidence : [])
    .filter(item => item && typeof item.blockId === "string" && typeof item.quote === "string");
  const value: NarrativeAssertion = {
    ...supplied, subject: supplied.subject || "", statement: supplied.statement || "",
    kind: supplied.kind || "INFERENCE", evidence,
  };
  const update = (patch: Partial<NarrativeAssertion>) => onChange({ ...value, ...patch });
  const characterOptions = <><option value="">{t("narrative.unbound")}</option>{characters.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</>;
  return <fieldset disabled={disabled} className="space-y-2 min-w-0">
    <label htmlFor={id + "-subject"} className="block text-xs">{t("narrative.subject")}</label>
    <Input id={id + "-subject"} value={value.subject} maxLength={300} onChange={e => update({ subject: e.target.value })} />
    <label htmlFor={id + "-statement"} className="block text-xs">{t("narrative.statement")}</label>
    <Textarea id={id + "-statement"} value={value.statement} maxLength={2000} onChange={e => update({ statement: e.target.value })} />
    <div className="grid grid-cols-1 gap-2">
      <label className="space-y-1 text-xs">{t("narrative.kind")}
        <select className={selectClass} value={value.kind} onChange={e => update({ kind: e.target.value as NarrativeKind })}>
          {(["FACT", "UTTERANCE", "BELIEF", "RUMOR", "INFERENCE"] as const).map(kind => <option key={kind} value={kind}>{t("narrative.kind." + kind)}</option>)}
        </select>
      </label>
      <label className="space-y-1 text-xs">{t("narrative.character")}
        <select className={selectClass} value={value.characterId || ""} onChange={e => update({ characterId: e.target.value || null })}>{characterOptions}</select>
      </label>
      <label className="space-y-1 text-xs">{t("narrative.holder")}
        <select className={selectClass} value={value.holderCharacterId || ""} onChange={e => update({ holderCharacterId: e.target.value || null })}>{characterOptions}</select>
      </label>
    </div>
    <label className="block space-y-1 text-xs">{t("narrative.worldTime")}
      <Input value={value.worldTime || ""} maxLength={500} placeholder={t("narrative.unknown")} onChange={e => update({ worldTime: e.target.value || null })} />
    </label>
    <label className="block space-y-1 text-xs">{t("narrative.uncertainty")}
      <Textarea value={value.uncertainty || ""} maxLength={1000} onChange={e => update({ uncertainty: e.target.value })} />
    </label>
    {value.evidence.map((evidence, index) => <div key={index} className="rounded border p-2 space-y-2">
      <label className="block text-xs">{t("narrative.block")}
        <select className={selectClass} value={evidence.blockId} onChange={e => update({ evidence: value.evidence.map((v, i) => i === index ? { blockId: e.target.value, quote: "" } : v) })}>
          <option value="">{t("narrative.selectBlock")}</option>
          {!source && evidence.blockId && <option value={evidence.blockId}>{evidence.blockId}</option>}
          {source?.blocks.map(b => <option key={b.id} value={b.id}>{b.id} · {b.text.slice(0, 30)}</option>)}
        </select>
      </label>
      <label className="block text-xs">{t("narrative.quote")}
        <Textarea value={evidence.quote} onChange={e => update({ evidence: value.evidence.map((v, i) => i === index ? { blockId: v.blockId, quote: e.target.value } : v) })} />
      </label>
      {value.evidence.length > 1 && <Button type="button" size="sm" variant="ghost" onClick={() => update({ evidence: value.evidence.filter((_, i) => i !== index) })}>{t("narrative.removeEvidence")}</Button>}
    </div>)}
    <Button type="button" size="sm" variant="outline" disabled={disabled || value.evidence.length >= 10} onClick={() => update({ evidence: [...value.evidence, { blockId: "", quote: "" }] })}>{t("narrative.addEvidence")}</Button>
    <label className="block space-y-1 text-xs">{t("narrative.replaces")}
      <select className={selectClass} value={value.supersedesId || ""} onChange={e => update({ supersedesId: e.target.value || null })}>
        <option value="">{t("narrative.append")}</option>
        {records.filter(r => r.status === "CONFIRMED").map(r => <option key={r.id} value={r.id}>{r.assertion.subject}: {r.assertion.statement.slice(0, 55)}</option>)}
      </select>
    </label>
  </fieldset>;
}
