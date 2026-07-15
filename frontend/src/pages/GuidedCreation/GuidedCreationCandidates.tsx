import { Check, Crown, UsersRound } from "lucide-react";
import { GuidedCreationCandidate, GuidedCreationStep } from "@/types";
import { cn } from "@/lib/utils";

type Props = {
  step: GuidedCreationStep;
  candidates: GuidedCreationCandidate[];
  recommendedId?: string;
  selectedId?: string;
  onSelect: (candidate: GuidedCreationCandidate) => void;
};

export default function GuidedCreationCandidates({
  step, candidates, recommendedId, selectedId, onSelect,
}: Props) {
  return (
    <div className="grid min-h-[280px] gap-3 xl:grid-cols-3" data-testid="guided-candidates">
      {candidates.map((candidate, index) => {
        const selected = candidate.candidateId === selectedId;
        const recommended = candidate.candidateId === recommendedId;
        return (
          <button
            key={candidate.candidateId}
            type="button"
            onClick={() => onSelect(candidate)}
            className={cn(
              "group relative min-h-[240px] overflow-hidden rounded-md border bg-white p-5 text-left transition-all duration-200",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2",
              selected
                ? "border-emerald-700 shadow-[0_8px_24px_rgba(6,78,59,0.10)]"
                : "border-zinc-200 hover:-translate-y-0.5 hover:border-zinc-400",
            )}
          >
            <div className="mb-5 flex h-7 items-center justify-between">
              <span className="text-xs font-semibold text-zinc-400">方向 {index + 1}</span>
              {recommended ? (
                <span className="flex items-center gap-1 text-xs font-medium text-emerald-700">
                  <Crown className="h-3.5 w-3.5" /> 推荐
                </span>
              ) : null}
            </div>
            <CandidateSummary step={step} candidate={candidate} />
            <div className={cn(
              "absolute bottom-4 right-4 flex h-7 w-7 items-center justify-center rounded-full border transition-colors",
              selected ? "border-emerald-700 bg-emerald-700 text-white" : "border-zinc-300 text-transparent",
            )}>
              <Check className="h-4 w-4" />
            </div>
          </button>
        );
      })}
    </div>
  );
}

function CandidateSummary({ step, candidate }: { step: GuidedCreationStep; candidate: GuidedCreationCandidate }) {
  const value = (key: string) => typeof candidate[key] === "string" ? String(candidate[key]) : "";
  if (step === "PREMISE") {
    return (
      <div className="space-y-3">
        <h3 className="text-lg font-semibold text-zinc-950">{value("title") || "未命名故事"}</h3>
        <p className="line-clamp-6 text-sm leading-6 text-zinc-600">{value("synopsis")}</p>
        <p className="text-xs font-medium text-zinc-400">{[value("genre"), value("tone")].filter(Boolean).join(" · ")}</p>
      </div>
    );
  }
  if (step === "WORLD") {
    return (
      <div className="space-y-3">
        <h3 className="text-lg font-semibold text-zinc-950">{value("name") || "未命名世界"}</h3>
        <p className="text-sm font-medium text-emerald-800">{value("tagline")}</p>
        <p className="line-clamp-6 text-sm leading-6 text-zinc-600">{value("creativeIntent")}</p>
      </div>
    );
  }
  if (step === "CHARACTERS") {
    const characters = Array.isArray(candidate.characters)
      ? candidate.characters.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
      : [];
    return (
      <div className="space-y-4">
        <h3 className="text-lg font-semibold text-zinc-950">{value("label") || `角色阵容`}</h3>
        <div className="space-y-3">
          {characters.map((character, index) => (
            <div key={index} className="flex gap-3 border-t border-zinc-100 pt-3 first:border-0 first:pt-0">
              <UsersRound className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" />
              <div>
                <p className="text-sm font-medium text-zinc-900">{String(character.name || `角色 ${index + 1}`)}</p>
                <p className="line-clamp-2 text-xs leading-5 text-zinc-500">{String(character.synopsis || "")}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }
  const chapters = Array.isArray(candidate.chapters)
    ? candidate.chapters.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
    : [];
  const sceneCount = chapters.reduce((sum, chapter) => sum + (Array.isArray(chapter.scenes) ? chapter.scenes.length : 0), 0);
  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-zinc-950">{value("title") || "章节大纲"}</h3>
      <p className="text-sm font-medium text-emerald-800">{chapters.length} 章 · {sceneCount} 场</p>
      <ol className="space-y-2 text-sm text-zinc-600">
        {chapters.slice(0, 5).map((chapter, index) => (
          <li key={index} className="line-clamp-1"><span className="mr-2 text-zinc-400">{index + 1}</span>{String(chapter.title || "未命名章节")}</li>
        ))}
      </ol>
    </div>
  );
}
