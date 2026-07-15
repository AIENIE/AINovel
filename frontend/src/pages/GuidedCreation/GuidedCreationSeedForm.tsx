import { FormEvent, useState } from "react";
import { Loader2, Minus, Plus, WandSparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { GuidedCreationSeed } from "./useGuidedCreation";

const GENRES = ["奇幻", "科幻", "悬疑", "言情", "武侠", "历史", "都市", "其他"];
const TONES = ["明快", "冷峻", "温暖", "紧张", "浪漫", "荒诞", "史诗", "克制"];

export default function GuidedCreationSeedForm({
  busy, onSubmit,
}: { busy: boolean; onSubmit: (seed: GuidedCreationSeed) => void }) {
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
        <h2 className="text-2xl font-semibold md:text-3xl">建立创作起点</h2>
      </div>
      <div className="space-y-7 border-y border-zinc-200 py-7">
        <div className="space-y-2">
          <Label htmlFor="seed-idea" className="text-sm font-medium">一句话想法</Label>
          <Textarea
            id="seed-idea"
            autoFocus
            required
            maxLength={1000}
            value={seed.seedIdea}
            onChange={(event) => setSeed((current) => ({ ...current, seedIdea: event.target.value }))}
            placeholder="例如：一名失忆的钟表匠发现，整座城市每天都在同一分钟停摆。"
            className="min-h-[150px] resize-none border-zinc-300 bg-white p-4 text-base leading-7"
          />
          <p className="text-right text-xs text-zinc-400">{seed.seedIdea.length}/1000</p>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <SeedSelect label="类型" value={seed.genre} items={GENRES} onChange={(genre) => setSeed((current) => ({ ...current, genre }))} />
          <SeedSelect label="基调" value={seed.tone} items={TONES} onChange={(tone) => setSeed((current) => ({ ...current, tone }))} />
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <div className="space-y-2">
            <Label>目标章节</Label>
            <div className="flex h-10 w-full items-center justify-between rounded-md border border-zinc-300 bg-white px-1">
              <Button type="button" variant="ghost" size="icon" className="h-8 w-8" aria-label="减少目标章节" title="减少目标章节" disabled={seed.targetChapterCount <= 3} onClick={() => setSeed((current) => ({ ...current, targetChapterCount: current.targetChapterCount - 1 }))}>
                <Minus className="h-4 w-4" />
              </Button>
              <span className="w-20 text-center text-sm font-semibold tabular-nums">{seed.targetChapterCount} 章</span>
              <Button type="button" variant="ghost" size="icon" className="h-8 w-8" aria-label="增加目标章节" title="增加目标章节" disabled={seed.targetChapterCount >= 12} onClick={() => setSeed((current) => ({ ...current, targetChapterCount: current.targetChapterCount + 1 }))}>
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="space-y-2">
            <Label>推进方式</Label>
            <div className="grid h-10 grid-cols-2 rounded-md border border-zinc-300 bg-white p-1">
              <button type="button" className={cn("rounded text-sm font-medium transition-colors", !seed.autoRun ? "bg-zinc-950 text-white" : "text-zinc-500")} onClick={() => setSeed((current) => ({ ...current, autoRun: false }))}>逐步选择</button>
              <button type="button" className={cn("rounded text-sm font-medium transition-colors", seed.autoRun ? "bg-emerald-700 text-white" : "text-zinc-500")} onClick={() => setSeed((current) => ({ ...current, autoRun: true }))}>自动完成</button>
            </div>
          </div>
        </div>
      </div>
      <div className="flex justify-end pt-6">
        <Button type="submit" className="bg-zinc-950 px-7 text-white hover:bg-zinc-800" disabled={busy || !seed.seedIdea.trim()}>
          {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <WandSparkles className="mr-2 h-4 w-4" />}
          开始构思
        </Button>
      </div>
    </form>
  );
}

function SeedSelect({ label, value, items, onChange }: { label: string; value: string; items: string[]; onChange: (value: string) => void }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="border-zinc-300 bg-white"><SelectValue /></SelectTrigger>
        <SelectContent>{items.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent>
      </Select>
    </div>
  );
}
