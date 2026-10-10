import { z } from "zod";
import type { DomainTransport } from "./transport";
export const evidenceHitSchema = z.object({
  chunkId: z.string().uuid(), materialId: z.string().uuid(), revisionId: z.string().uuid(), sourceVersion: z.number().int(),
  title: z.string(), text: z.string(), start: z.number().int().nonnegative(), end: z.number().int().nonnegative(), reasons: z.array(z.string()), rank: z.number().finite(),
});
export const evidenceSettingsSchema = z.object({ version: z.number().int().nonnegative(), semanticProfile: z.string(), rerank: z.boolean(), hints: z.boolean(), checks: z.boolean(), bindings: z.array(z.string().uuid()) });
export const referencePackageSchema = z.object({ version: z.number().int().nonnegative(), pinned: z.array(z.string().uuid()), excluded: z.array(z.string().uuid()) });
export const evidenceTaskSchema = z.object({ id: z.string().uuid(), kind: z.string(), status: z.string(), errorCode: z.string().nullable(), result: z.unknown().nullable(), externalLimit: z.number().int(), callsReserved: z.number().int(), stale: z.boolean(), sourceRevision: z.string().nullable().optional() });
export const evidenceQuoteSchema = z.object({ sourceId: z.string(), quote: z.string(), start: z.number().int().nonnegative(), end: z.number().int().nonnegative() });
export const evidenceOpenedSchema = z.object({ id: z.string(), kind: z.string(), text: z.string(), revisionId: z.string().nullable(), bodyVersion: z.string().nullable(), sceneId: z.string().nullable(), start: z.number().int(), conversionVersion: z.string(), title: z.string().optional() });
export const evidenceReportSchema = z.object({ report: z.object({ coverage: z.string(), findings: z.array(z.object({ kind: z.enum(["FACT", "BELIEF", "DISCLOSURE", "PLAN"]), claim: z.string(), decision: z.enum(["SUPPORTED", "CONTRADICTED", "INSUFFICIENT", "UNKNOWN"]), condition: z.string(), evidence: evidenceQuoteSchema.array() })) }), opened: evidenceOpenedSchema.array(), exclusions: z.string().array() });
export const materialAnalysisSchema = z.object({ analysis: z.object({ coverage: z.string(), candidates: z.array(z.object({ type: z.enum(["ENTITY", "TAG", "FACT"]), name: z.string(), aliases: z.string().array(), statement: z.string(), condition: z.string(), evidence: evidenceQuoteSchema.array() })) }), opened: evidenceOpenedSchema.array(), exclusions: z.string().array() });
export type EvidenceHit = z.infer<typeof evidenceHitSchema>;
export type EvidenceSettings = z.infer<typeof evidenceSettingsSchema>;
export type ReferencePackage = z.infer<typeof referencePackageSchema>;
export type EvidenceTask = z.infer<typeof evidenceTaskSchema>;
export type EvidenceTaskRequest = { kind: "CHECK" | "EXTRACT"; manuscriptId?: string; branchId?: string; bodyVersion?: string; scenes?: string[]; sourceRevision?: string; question: string; expectedVersion: number; requestKey: string };
export function createMaterialEvidenceApi(http: DomainTransport) {
  const base = "/v1/material-evidence";
  const body = (payload: unknown, method = "POST", signal?: AbortSignal) => ({ method, body: JSON.stringify(payload), signal });
  const parse = async <T>(schema: z.ZodType<T>, path: string, init?: RequestInit) => schema.parse(await http.json(path, init));
  return {
    search: (payload: { query: string; storyId: string; mode: "fact" | "inspiration"; scope: "bound" | "personal" | "public"; limit?: number }, signal?: AbortSignal) => parse(z.object({ mode: z.string(), scope: z.string(), items: evidenceHitSchema.array(), degradation: z.string().array() }), `${base}/search`, body(payload, "POST", signal)),
    hints: (manuscriptId: string, sceneId: string, query: string, signal?: AbortSignal) => parse(z.object({ mode: z.string(), scope: z.string(), items: evidenceHitSchema.array(), degradation: z.string().array() }), `${base}/hints`, body({ manuscriptId, sceneId, query }, "POST", signal)),
    open: (id: string) => parse(evidenceHitSchema, `${base}/chunks/${id}`),
    settings: (story: string) => parse(evidenceSettingsSchema, `${base}/works/${story}/settings`),
    saveSettings: (story: string, payload: Omit<EvidenceSettings, "version"> & { expectedVersion: number; requestKey: string }) => parse(evidenceSettingsSchema, `${base}/works/${story}/settings`, body(payload, "PUT")),
    package: (manuscript: string, scene: string) => parse(referencePackageSchema, `${base}/manuscripts/${manuscript}/scenes/${scene}/package`),
    savePackage: (manuscript: string, scene: string, payload: Omit<ReferencePackage, "version"> & { expectedVersion: number; requestKey: string }) => parse(referencePackageSchema, `${base}/manuscripts/${manuscript}/scenes/${scene}/package`, body(payload, "PUT")),
    statuses: () => parse(z.array(z.object({ materialId: z.string().uuid(), revisionId: z.string().uuid(), version: z.number().int(), saved: z.string(), review: z.string(), basic: z.string(), semantic: z.string() })), `${base}/statuses`),
    resumeSemantic: (revision: string, profile: string, payload: { expectedVersion: number; requestKey: string }) => parse(z.object({ revisionId: z.string().uuid(), profile: z.string(), status: z.string() }), `${base}/revisions/${encodeURIComponent(revision)}/semantic/${encodeURIComponent(profile)}/resume`, body(payload)),
    revisions: (material: string,page=0) => parse(z.array(z.object({ id:z.string().uuid(),version:z.number().int(),title:z.string(),createdAt:z.string() })),`${base}/sources/${material}/revisions?page=${page}`),
    rawRevision: (revision: string) => parse(z.object({ id:z.string().uuid(),materialId:z.string().uuid(),version:z.number().int(),title:z.string(),content:z.string() }),`${base}/revisions/${revision}/raw`),
    entities: (page = 0) => parse(z.array(z.object({ id: z.string().uuid(), name: z.string(), aliases: z.string().array() })), `${base}/entities?page=${page}`),
    entitySources: (id: string, story: string, page = 0) => parse(evidenceHitSchema.array(), `${base}/entities/${id}/sources?${new URLSearchParams({ story, page: String(page) })}`),
    duplicates: () => parse(z.array(z.object({ first: z.string().uuid(), second: z.string().uuid(), kind: z.string(), firstText: z.string(), secondText: z.string() })), `${base}/duplicates`),
    merge: (payload: { first: string; second: string; firstVersion: number; secondVersion: number; title: string; content: string; requestKey: string }) => http.json(`${base}/sources/merge`, body(payload)),
    confirm: (task: string, candidateIndex: number, expectedVersion: number, requestKey: string) => http.json(`${base}/tasks/${task}/confirm`, body({ candidateIndex, expectedVersion, requestKey })),
    annotations: (revision: string) => http.json<Array<{ id: string; revisionId: string; kind: string; name: string; candidate: unknown }>>(`${base}/revisions/${revision}/annotations`),
    createEntity: (payload: { name: string; aliases: string[]; materials: Record<string, number>; requestKey: string }) => http.json(`${base}/entities`, body(payload)),
    citations: (manuscript: string) => http.json<Array<{ id: string; relation_type: string; state: string; quote: string | null; scene_id: string; report_task_id?: string | null; report_finding_index?: number | null }>>(`${base}/manuscripts/${manuscript}/citations`),
    citationBody: (manuscript: string, scene: string) => parse(z.object({ branchId: z.string().uuid(), manuscriptVersion: z.number().int(), blocks: z.array(z.object({ id: z.string(), text: z.string() })) }), `${base}/manuscripts/${manuscript}/scenes/${scene}/citation-body`),
    citation: (manuscript: string, scene: string, payload: { revisionId: string; branchId: string; bodyVersion: string; relationType: "CONFIRMED"; start: number; end: number; quote: string; bodyQuote: string; requestKey: string; bodyBlockId: string; expectedManuscriptVersion: number }) => parse(z.object({ id: z.string().uuid() }), `${base}/manuscripts/${manuscript}/scenes/${scene}/citations`, body(payload)),
    preview: (request: EvidenceTaskRequest) => parse(z.object({ available: z.boolean(), reason: z.string(), externalLimit: z.number().int(), plannedExternal: z.number().int(), maximumCredits: z.number().int(), fingerprint: z.string() }), `${base}/tasks/preview`, body(request)),
    submit: (request: EvidenceTaskRequest, previewFingerprint: string, acceptedMaximumCredits: number) => parse(evidenceTaskSchema, `${base}/tasks`, body({ request, previewFingerprint, acceptedMaximumCredits })),
    tasks: (manuscript?: string) => parse(evidenceTaskSchema.array(), `${base}/tasks${manuscript ? `?manuscript=${encodeURIComponent(manuscript)}` : ""}`),
    task: (id: string) => parse(evidenceTaskSchema, `${base}/tasks/${id}`),
    cancel: (id: string) => parse(evidenceTaskSchema, `${base}/tasks/${id}/cancel`, body({})),
    resume: (id: string) => parse(evidenceTaskSchema, `${base}/tasks/${id}/resume`, body({})),
  };
}
