import { useTranslation } from "react-i18next";
import type { StoryAssistantSource } from "@/lib/api/domains/story-assistant";
import { assistantSourceReason } from "@/lib/assistant-messages";

export function StoryDiscussionSources({ sources }: { sources: StoryAssistantSource[] }) {
  const { t } = useTranslation();
  if (!sources.length) return null;
  return <details className="text-xs"><summary className="cursor-pointer">{t("assistant.sources", { count: sources.length })}</summary>
    <div className="mt-2 space-y-2">{sources.map((source, index) => <details key={`${source.id}:${index}`} className="rounded border p-2"><summary className="cursor-pointer">{source.title}</summary>
      <p className="mt-2 whitespace-pre-wrap break-words">{source.content}</p>
      {source.truncated && <p className="mt-1 text-amber-700">{t("assistant.sourceTruncated")}</p>}
      {source.reason && <p className="mt-1 text-muted-foreground">{assistantSourceReason(source.reason)}</p>}
    </details>)}</div>
  </details>;
}
