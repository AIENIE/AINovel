import { useEffect, useState } from "react";
import { ArrowRight, Loader2, RefreshCcw, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { GuidedCreationCandidate } from "@/types";

type Props = {
  candidate: GuidedCreationCandidate;
  busy: boolean;
  onDevelop: (action: "continue" | "rewrite", instruction: string) => Promise<void>;
  onExpand: () => Promise<void>;
};

const text = (value: unknown) => typeof value === "string" ? value : "";

export default function GuidedCreationOutlineDirection({ candidate, busy, onDevelop, onExpand }: Props) {
  const [instruction, setInstruction] = useState("");
  const development = candidate.development && typeof candidate.development === "object"
    ? candidate.development as Record<string, unknown>
    : undefined;

  useEffect(() => setInstruction(""), [candidate.candidateId]);

  return (
    <div className="space-y-6 border-t border-zinc-200 pt-6" data-testid="outline-direction-workspace">
      <div className="grid gap-5 md:grid-cols-2">
        <div>
          <p className="text-xs font-medium text-zinc-500">当前根方向</p>
          <h3 className="mt-2 text-lg font-semibold text-zinc-950">{text(candidate.title)}</h3>
          <p className="mt-2 text-sm leading-6 text-zinc-600">{text(candidate.summary)}</p>
        </div>
        <div className="border-l-0 border-zinc-200 md:border-l md:pl-5">
          <p className="text-xs font-medium text-zinc-500">
            最新发展稿 · 第 {Number(candidate.developmentRevision || 0)} 版
          </p>
          {development ? (
            <div className="mt-2 space-y-2 text-sm leading-6 text-zinc-600">
              <p className="font-medium text-zinc-900">{text(development.title)}</p>
              <p>{text(development.narrativeArc)}</p>
              <p>{text(development.endingDirection)}</p>
            </div>
          ) : (
            <p className="mt-2 text-sm leading-6 text-zinc-500">尚未深入发展，可直接继续推演，也可先补充要求。</p>
          )}
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="outline-direction-instruction" className="text-xs font-medium text-zinc-500">
          补充要求或重写反馈
        </Label>
        <Textarea
          id="outline-direction-instruction"
          value={instruction}
          maxLength={500}
          rows={3}
          disabled={busy}
          onChange={(event) => setInstruction(event.target.value)}
          placeholder="例如：加强主角主动选择，让中段转折更意外；按反馈重写时此项必填。"
          className="resize-none border-zinc-300 bg-white leading-6"
        />
        <p className="text-right text-[11px] text-zinc-400">{instruction.length}/500</p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
        <Button variant="outline" disabled={busy} onClick={() => void onDevelop("continue", instruction)}>
          {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
          继续发展
        </Button>
        <Button
          variant="outline"
          disabled={busy || !instruction.trim()}
          onClick={() => void onDevelop("rewrite", instruction)}
        >
          <RefreshCcw className="mr-2 h-4 w-4" /> 按反馈重写
        </Button>
        <Button className="bg-zinc-950 text-white hover:bg-zinc-800" disabled={busy} onClick={() => void onExpand()}>
          选定并展开完整大纲 <ArrowRight className="ml-2 h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
