import { t } from "@/i18n";
import { assistantWarning } from "./assistant-messages";

export function storyAssistantWarning(code: string): string {
  const [kind, detail] = code.split(":", 2);
  if (kind === "STORY_CONTEXT_OMITTED") return t("storyAssistant.omittedSource", { detail: detail || "" });
  if (kind === "STORY_UNREAD_RESOURCES") return t("storyAssistant.unreadCount", { count: Number(detail) || 0 });
  if (kind === "STORY_SOURCE_NOT_FOUND") return t("assistant.sourceNotFound");
  return assistantWarning(code);
}
