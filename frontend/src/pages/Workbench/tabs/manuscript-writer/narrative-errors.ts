import { isApiError } from "@/lib/api-client";
import { localizedErrorMessage } from "@/lib/error-messages";
import { t } from "@/i18n";

// Only exact, known domain codes are translated. Never render backend messages.
const codes: Record<string, [number, string]> = {
  NARRATIVE_EVIDENCE_AMBIGUOUS: [422, "ambiguous"],
  NARRATIVE_EVIDENCE_MISMATCH: [422, "evidence"],
  NARRATIVE_EVIDENCE_BLOCK_MISSING: [422, "evidence"],
  NARRATIVE_EVIDENCE_REQUIRED: [422, "evidence"],
  NARRATIVE_BELIEF_HOLDER_REQUIRED: [422, "holder"],
  NARRATIVE_UNKNOWN_CHARACTER: [422, "holder"],
  NARRATIVE_INVALID_ASSERTION: [422, "assertion"],
  NARRATIVE_REVIEW_INCOMPLETE: [422, "incomplete"],
  NARRATIVE_REPLACEMENT_INVALID: [422, "replacement"],
  NARRATIVE_EMPTY_SCENE: [422, "empty"],
  NARRATIVE_SOURCE_STALE: [409, "stale"],
  NARRATIVE_VERSION_CHANGED: [409, "version"],
  NARRATIVE_BRANCH_CHANGED: [409, "version"],
  NARRATIVE_ALREADY_REVIEWED: [409, "reviewed"],
  NARRATIVE_CANCELLED: [409, "cancelled"],
  NARRATIVE_EXTRACTION_NOT_READY: [409, "notReady"],
};

export function narrativeErrorMessage(error: unknown): string {
  if (!isApiError(error)) return localizedErrorMessage(error);
  const known = Object.prototype.hasOwnProperty.call(codes, error.message) ? codes[error.message] : undefined;
  if (!known || error.status !== known[0]) return localizedErrorMessage(error);
  const message = t("narrative.error." + known[1]);
  return error.requestId && /^[a-zA-Z0-9-]{1,64}$/.test(error.requestId)
    ? `${message} ${t("narrative.error.requestId", { id: error.requestId })}` : message;
}
