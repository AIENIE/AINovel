import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { GuidedCreationCandidate, GuidedCreationStep } from "@/types";

type Props = {
  step: GuidedCreationStep;
  value: GuidedCreationCandidate;
  onChange: (value: GuidedCreationCandidate) => void;
};

const text = (value: unknown) => typeof value === "string" ? value : "";

export default function GuidedCreationCandidateEditor({ step, value, onChange }: Props) {
  const patch = (key: string, next: unknown) => onChange({ ...value, [key]: next });

  if (step === "PREMISE") {
    return (
      <div className="grid gap-5 border-t border-zinc-200 pt-6 md:grid-cols-2">
        <Field label="故事名" value={text(value.title)} onChange={(next) => patch("title", next)} />
        <Field label="类型" value={text(value.genre)} onChange={(next) => patch("genre", next)} />
        <div className="md:col-span-2">
          <Area label="故事梗概" value={text(value.synopsis)} onChange={(next) => patch("synopsis", next)} />
        </div>
        <Field label="核心承诺" value={text(value.corePromise)} onChange={(next) => patch("corePromise", next)} />
        <Field label="核心问题" value={text(value.centralQuestion)} onChange={(next) => patch("centralQuestion", next)} />
      </div>
    );
  }

  if (step === "WORLD") {
    return (
      <div className="grid gap-5 border-t border-zinc-200 pt-6 md:grid-cols-2">
        <Field label="世界名称" value={text(value.name)} onChange={(next) => patch("name", next)} />
        <Field label="一句话定位" value={text(value.tagline)} onChange={(next) => patch("tagline", next)} />
        <div className="md:col-span-2">
          <Area label="创作意图" value={text(value.creativeIntent)} onChange={(next) => patch("creativeIntent", next)} />
        </div>
      </div>
    );
  }

  if (step === "CHARACTERS") {
    const characters = Array.isArray(value.characters)
      ? value.characters.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
      : [];
    const updateCharacter = (index: number, key: string, next: string) => {
      const updated = characters.map((character, itemIndex) => itemIndex === index
        ? { ...character, [key]: next }
        : character);
      patch("characters", updated);
    };
    return (
      <div className="space-y-5 border-t border-zinc-200 pt-6">
        <Field label="阵容名称" value={text(value.label)} onChange={(next) => patch("label", next)} />
        <div className="divide-y divide-zinc-200 border-y border-zinc-200">
          {characters.map((character, index) => (
            <div key={`${index}-${text(character.name)}`} className="grid gap-4 py-5 md:grid-cols-[180px_1fr]">
              <Field label={`角色 ${index + 1}`} value={text(character.name)} onChange={(next) => updateCharacter(index, "name", next)} />
              <Area label="人物定位" value={text(character.synopsis)} onChange={(next) => updateCharacter(index, "synopsis", next)} rows={2} />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (step === "OUTLINE") {
    const chapters = Array.isArray(value.chapters)
      ? value.chapters.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
      : [];
    const updateChapter = (index: number, key: string, next: string) => {
      const updated = chapters.map((chapter, itemIndex) => itemIndex === index
        ? { ...chapter, [key]: next }
        : chapter);
      patch("chapters", updated);
    };
    return (
      <div className="space-y-5 border-t border-zinc-200 pt-6">
        <Field label="大纲名称" value={text(value.title)} onChange={(next) => patch("title", next)} />
        <div className="divide-y divide-zinc-200 border-y border-zinc-200">
          {chapters.map((chapter, index) => (
            <div key={index} className="grid gap-4 py-5 md:grid-cols-[180px_1fr]">
              <Field label={`第 ${index + 1} 章`} value={text(chapter.title)} onChange={(next) => updateChapter(index, "title", next)} />
              <Area label="章节目标" value={text(chapter.summary)} onChange={(next) => updateChapter(index, "summary", next)} rows={2} />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return null;
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div className="space-y-2">
      <Label className="text-xs font-medium text-zinc-500">{label}</Label>
      <Input value={value} onChange={(event) => onChange(event.target.value)} className="border-zinc-300 bg-white" />
    </div>
  );
}

function Area({
  label, value, onChange, rows = 4,
}: { label: string; value: string; onChange: (value: string) => void; rows?: number }) {
  return (
    <div className="space-y-2">
      <Label className="text-xs font-medium text-zinc-500">{label}</Label>
      <Textarea
        value={value}
        rows={rows}
        onChange={(event) => onChange(event.target.value)}
        className="resize-none border-zinc-300 bg-white leading-6"
      />
    </div>
  );
}
