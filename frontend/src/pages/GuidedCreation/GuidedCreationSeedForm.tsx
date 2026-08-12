import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Loader2, Minus, Plus, WandSparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { GuidedCreationSeed } from "./useGuidedCreation";

const GENRES = [
  { value: "奇幻", labelKey: "guided.genreFantasy" },
  { value: "科幻", labelKey: "guided.genreScifi" },
  { value: "悬疑", labelKey: "guided.genreMystery" },
  { value: "言情", labelKey: "guided.genreRomance" },
  { value: "武侠", labelKey: "guided.genreWuxia" },
  { value: "历史", labelKey: "guided.genreHistory" },
  { value: "都市", labelKey: "guided.genreUrban" },
  { value: "其他", labelKey: "guided.genreOther" },
];
const TONES = [
  { value: "明快", labelKey: "guided.toneBright" },
  { value: "冷峻", labelKey: "guided.toneCold" },
  { value: "温暖", labelKey: "guided.toneWarm" },
  { value: "紧张", labelKey: "guided.toneTense" },
  { value: "浪漫", labelKey: "guided.toneRomantic" },
  { value: "荒诞", labelKey: "guided.toneAbsurd" },
  { value: "史诗", labelKey: "guided.toneEpic" },
  { value: "克制", labelKey: "guided.toneRestrained" },
];

export default function GuidedCreationSeedForm({
  busy, onSubmit,
}: { busy: boolean; onSubmit: (seed: GuidedCreationSeed) => void }) {
  const { t } = useTranslation();
  const [seed, setSeed] = useState<GuidedCreationSeed>({
    seedIdea: "", genre: "悬疑", tone: "紧张", targetChapterCount: 6, autoRun: false,
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (seed.seedIdea.trim()) onSubmit({ ...seed, seedIdea: seed.seedIdea.trim() });
  };
  return (
    <form onSubmit={submit} className="mx-auto max-w-3xl animate-in fade-in slide-in-from-bottom-2 duration-300">
      <div className="mb-8">
        <p className="mb-2 text-xs font-semibold uppercase text-emerald-700">Step 00</p>
        <h2 className="text-2xl font-semibold md:text-3xl">{t("guided.seedTitle")}</h2>
      </div>
      <div className="space-y-7 border-y border-zinc-200 py-7">
        <div className="space-y-2">
          <Label htmlFor="seed-idea" className="text-sm font-medium">{t("guided.ideaLabel")}</Label>
          <Textarea
            id="seed-idea"
            autoFocus
            required
            maxLength={1000}
            value={seed.seedIdea}
            onChange={(event) => setSeed((current) => ({ ...current, seedIdea: event.target.value }))}
            placeholder={t("guided.ideaPlaceholder")}
            className="min-h-[150px] resize-none border-zinc-300 bg-white p-4 text-base leading-7"
          />
          <p className="text-right text-xs text-zinc-400">{seed.seedIdea.length}/1000</p>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <SeedSelect label={t("guided.genreLabel")} value={seed.genre} items={GENRES} onChange={(genre) => setSeed((current) => ({ ...current, genre }))} />
          <SeedSelect label={t("guided.toneLabel")} value={seed.tone} items={TONES} onChange={(tone) => setSeed((current) => ({ ...current, tone }))} />
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <div className="space-y-2">
            <Label>{t("guided.targetChapters")}</Label>
            <div className="flex h-10 w-full items-center justify-between rounded-md border border-zinc-300 bg-white px-1">
              <Button type="button" variant="ghost" size="icon" className="h-8 w-8" aria-label={t("guided.decreaseChapters")} title={t("guided.decreaseChapters")} disabled={seed.targetChapterCount <= 3} onClick={() => setSeed((current) => ({ ...current, targetChapterCount: current.targetChapterCount - 1 }))}>
                <Minus className="h-4 w-4" />
              </Button>
              <span className="w-20 text-center text-sm font-semibold tabular-nums">{t("guided.chaptersCount", { count: seed.targetChapterCount })}</span>
              <Button type="button" variant="ghost" size="icon" className="h-8 w-8" aria-label={t("guided.increaseChapters")} title={t("guided.increaseChapters")} disabled={seed.targetChapterCount >= 12} onClick={() => setSeed((current) => ({ ...current, targetChapterCount: current.targetChapterCount + 1 }))}>
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="space-y-2">
            <Label>{t("guided.advanceMode")}</Label>
            <div className="grid h-10 grid-cols-2 rounded-md border border-zinc-300 bg-white p-1">
              <button type="button" className={cn("rounded text-sm font-medium transition-colors", !seed.autoRun ? "bg-zinc-950 text-white" : "text-zinc-500")} onClick={() => setSeed((current) => ({ ...current, autoRun: false }))}>{t("guided.stepMode")}</button>
              <button type="button" className={cn("rounded text-sm font-medium transition-colors", seed.autoRun ? "bg-emerald-700 text-white" : "text-zinc-500")} onClick={() => setSeed((current) => ({ ...current, autoRun: true }))}>{t("guided.autoMode")}</button>
            </div>
          </div>
        </div>
      </div>
      <div className="flex justify-end pt-6">
        <Button type="submit" className="bg-zinc-950 px-7 text-white hover:bg-zinc-800" disabled={busy || !seed.seedIdea.trim()}>
          {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <WandSparkles className="mr-2 h-4 w-4" />}
          {t("guided.startConception")}
        </Button>
      </div>
    </form>
  );
}

function SeedSelect({ label, value, items, onChange }: { label: string; value: string; items: { value: string; labelKey: string }[]; onChange: (value: string) => void }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="border-zinc-300 bg-white"><SelectValue /></SelectTrigger>
        <SelectContent>{items.map((item) => <SelectItem key={item.value} value={item.value}>{t(item.labelKey)}</SelectItem>)}</SelectContent>
      </Select>
    </div>
  );
}
