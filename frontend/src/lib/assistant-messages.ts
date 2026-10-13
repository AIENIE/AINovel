import { t } from "@/i18n";

export function assistantWarning(code: string): string {
  const separator = code.indexOf(":");
  const kind = separator === -1 ? code : code.slice(0, separator);
  const detail = separator === -1 ? "" : code.slice(separator + 1);
  if (detail && kind === "ASSISTANT_CONTEXT_OMITTED") return t("assistant.omittedSource", { detail });
  if (detail && kind === "ASSISTANT_SOURCE_NOT_FOUND") return t("assistant.sourceSearchMiss", { detail });
  const keys: Record<string, string> = { H2_EDIT_CONTEXT_ONLY: "h2Sources", ASSISTANT_SOURCE_NOT_FOUND: "sourceNotFound", ASSISTANT_CONTEXT_OMITTED: "contextOmitted", ASSISTANT_HISTORY_OMITTED: "historyOmitted", SCENE_LENGTH_OUT_OF_RANGE: "outside" };
  const message = keys[kind] ? t(`assistant.${keys[kind]}`) : /^[A-Z][A-Z0-9_]+$/.test(kind) ? t("assistant.checkSources") : code;
  return kind === "ASSISTANT_HISTORY_OMITTED" && /^\d+$/.test(detail) ? `${message} ${t("assistant.omittedCount", { count: Number(detail) })}` : message;
}
export function assistantSourceReason(reason: string): string {
  const excerpt = /^EXCERPT:(\d+):(\d+)$/.exec(reason);
  return excerpt ? t("assistant.excerptRange", { start: Number(excerpt[1]) + 1, end: excerpt[2] }) : assistantWarning(reason);
}
