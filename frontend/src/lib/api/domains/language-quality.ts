import { z } from "zod";
import type { DomainTransport } from "./transport";
import { aiOperationAcceptedSchema } from "@/lib/api-contracts";

const uuid = z.string().uuid();
const integer = z.number().int();
export const languageSettingsSchema = z.object({ available: z.boolean(), generationStandard: z.boolean(), checkAfterGeneration: z.boolean(), standardVersion: z.string() });
export const languageIssueSchema = z.object({
  id: z.string(), kind: z.enum(["LANGUAGE", "STYLE", "OBSERVATION"]), category: z.enum(["OMISSION", "COMPRESSION", "CHOPPY", "AWKWARD", "PARAGRAPH"]),
  quote: z.string(), impact: z.string(), direction: z.string(), start: integer.nullable(), end: integer.nullable(),
  location: z.string(), paragraph: integer, context: z.string(), currentStart: integer, currentEnd: integer, availability: z.string(),
});
const verdict = z.enum(["PASS", "UNCERTAIN", "FAIL"]);
export const languagePatchSchema = z.object({
  reason: z.string().nullable().optional(),
  id: uuid, reportId: uuid, issueId: z.string(), original: z.string(), replacement: z.string().nullable(),
  beforeContext: z.string(), afterContext: z.string().nullable(),
  review: z.object({ language: verdict, languageReason: z.string(), meaning: verdict, meaningReason: z.string(), changes: z.string().array() }).nullable(),
  status: z.string(), applicability: z.string(), operationId: uuid.nullable(), appliedSnapshotId: uuid.nullable(), undoneSnapshotId: uuid.nullable(), createdAt: z.string(),
});
export const languageReportSchema = z.object({
  id: uuid, manuscriptId: uuid, sceneId: uuid,
  source: z.object({ branchId: uuid.nullable(), bodyVersion: integer, snapshotId: uuid, projectionVersion: z.string(), htmlHash: z.string(), textHash: z.string(), contextVersion: z.string(), standardVersion: z.string() }),
  status: z.enum(["UNCHECKED", "CHECKING", "ISSUES", "NO_CLEAR_ISSUES", "INCOMPLETE", "FAILED", "LOCAL_ONLY", "STALE"]), summary: z.string(),
  issues: languageIssueSchema.array(), coverage: z.object({ index: integer, start: integer, end: integer, contextStart: integer, contextEnd: integer, state: z.string(), reason: z.string().nullable() }).array(),
  patches: languagePatchSchema.array(), operationId: uuid.nullable(), canContinueBatch: z.boolean(), createdAt: z.string(),
});
const decisionSchema = z.object({ patch: languagePatchSchema, bodyVersion: integer, branchId: uuid, content: z.string() });
export type LanguageReport = z.infer<typeof languageReportSchema>;
export type LanguagePatch = z.infer<typeof languagePatchSchema>;
export type LanguageIssue = z.infer<typeof languageIssueSchema>;
export type LanguageSettings = z.infer<typeof languageSettingsSchema>;
export type LanguageDecision = z.infer<typeof decisionSchema>;
export type LanguageVersion = { expectedBranchId: string | null; expectedVersion: number };

export function createLanguageQualityApi(http: DomainTransport) {
  const base = (manuscript: string) => `/v2/manuscripts/${manuscript}/quality-runs/language`;
  const body = (value: unknown, method = "POST") => ({ method, body: JSON.stringify(value) });
  const parse = async <T>(schema: z.ZodType<T>, path: string, init?: RequestInit) => schema.parse(await http.json(path, init));
  return {
    settings: (m: string) => parse(languageSettingsSchema, `${base(m)}/settings`),
    saveSettings: (m: string, settings: Pick<LanguageSettings, "generationStandard" | "checkAfterGeneration">) => parse(languageSettingsSchema, `${base(m)}/settings`, body(settings, "PUT")),
    reports: (m: string, scene: string, signal?: AbortSignal) => parse(languageReportSchema.array(), `${base(m)}?sceneId=${encodeURIComponent(scene)}`, { signal }),
    check: (m: string, scene: string, version: LanguageVersion) => parse(aiOperationAcceptedSchema, `/v2/manuscripts/${m}/scenes/${scene}/quality-runs/language/operations`, body(version)),
    suggest: (m: string, report: string, issue: string) => parse(aiOperationAcceptedSchema, `${base(m)}/${report}/issues/${encodeURIComponent(issue)}/suggestions/operations`, body({})),
    patch: (m: string, report: string, patch: string) => parse(languagePatchSchema, `${base(m)}/${report}/patches/${patch}`),
    decide: (m: string, report: string, patch: string, action: "accept" | "reject" | "undo", version: LanguageVersion, key: string) =>
      parse(decisionSchema, `${base(m)}/${report}/patches/${patch}/${action}`, { ...body(version), headers: { "Idempotency-Key": key } }),
  };
}
