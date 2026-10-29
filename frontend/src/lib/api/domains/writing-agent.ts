import { z } from "zod";
import type { DomainTransport } from "./transport";
import type { AssistantSelection } from "./writing-assistant";
import type { Manuscript } from "@/types";

const id = z.string().uuid();
const money = z.union([z.number(), z.string()]).refine(value => Number.isFinite(Number(value)) && Number(value) >= 0);
export const agentLimits = { modelCalls: 24, controlCalls: 12, paidTools: 12, revisions: 3, localTools: 32 };
export type AgentLimits = typeof agentLimits;
export type AgentRequest = { branchId: string; expectedVersion: number; sourceHtml: string; selection: AssistantSelection | null;
  instruction: string; intent: "EDIT" | "AUTHOR_QA"; mode: "fast" | "crafted"; languageCheck: boolean; plotCheck: boolean;
  maxCostCny: number; maxProjectCredits: number; limits: AgentLimits; contextHash?: string; expectedSelectionRevision?: number };
const limitsSchema = z.object({ modelCalls: z.number(), controlCalls: z.number(), paidTools: z.number(), revisions: z.number(), localTools: z.number() });
const bindingSchema = z.object({ worldId: id.nullable(), versionId: id.nullable(), sourceType: z.string(), name: z.string(), sequenceNumber: z.number() }).passthrough();
export const agentPreviewSchema = z.object({ contextHash: z.string(), selectionRevision: z.number(), protocol: z.string(), h2: z.boolean(), binding: bindingSchema.nullable(), tools: z.string().array(), issues: z.string().array(), limits: limitsSchema, maxCostCny: money, maxProjectCredits: z.number(), firstInput: z.object({ role: z.string(), content: z.string() }).array().default([]) });
export type AgentPreview = z.infer<typeof agentPreviewSchema>;
const candidateSchema = z.object({ id, sequence: z.number(), parentId: id.nullable(), html: z.string(), hash: z.string(), actualHan: z.number(), replacements: z.unknown().array(), sources: z.unknown().array() });
export type AgentCandidate = z.infer<typeof candidateSchema>;
const stepSchema = z.object({ sequence: z.number(), kind: z.string(), feature: z.string().nullable(), status: z.string(), result: z.unknown(), reservedCny: money.nullable(), settledCny: money.nullable(), projectCredits: z.number().nullable() });
export const agentViewSchema = z.object({ id, manuscriptId: id, sceneId: id, branchId: id, status: z.string(), revision: z.number(), operationId: id.nullable(), progress: z.record(z.unknown()), candidates: candidateSchema.array(), steps: stepSchema.array(),
  settledCny: money, reservedCny: money, projectCredits: z.number(), maxCostCny: money, maxProjectCredits: z.number(), createdAt: z.string(), sourceHtml: z.string(), sourceVersion: z.number(), intent: z.string(), h2: z.boolean(), worldBinding: bindingSchema.nullable(), selectionRevision: z.number() });
export type AgentRun = z.infer<typeof agentViewSchema>;
export function createWritingAgentApi(http: DomainTransport, normalize: (value: unknown) => Manuscript) {
  const base = (m: string, scene: string) => `/v1/manuscripts/${m}/scenes/${scene}/agent-runs`;
  const parse = async <S extends z.ZodTypeAny>(schema: S, path: string, init?: RequestInit): Promise<z.output<S>> => schema.parse(await http.json(path, init));
  return {
    list: (m: string, scene: string, branch: string, signal?: AbortSignal) => parse(agentViewSchema.array(), `${base(m, scene)}?${new URLSearchParams({ branchId: branch })}`, { signal }),
    preview: (m: string, scene: string, request: AgentRequest) => parse(agentPreviewSchema, `${base(m, scene)}/preview`, { method: "POST", body: JSON.stringify(request) }),
    start: (m: string, scene: string, request: AgentRequest, key: string) => parse(z.object({ runId: id, operationId: id }), base(m, scene), { method: "POST", body: JSON.stringify(request), headers: { "Idempotency-Key": key } }),
    get: (m: string, scene: string, run: string, signal?: AbortSignal) => parse(agentViewSchema, `${base(m, scene)}/${run}`, { signal }),
    control: (m: string, scene: string, run: string, action: "pause" | "resume" | "cancel", expectedRevision: number, key: string, answer?: string) => parse(agentViewSchema, `${base(m, scene)}/${run}/${action}`, { method: "POST", body: JSON.stringify({ expectedRevision, ...(answer ? { answer } : {}) }), headers: { "Idempotency-Key": key } }),
    accept: async (m: string, scene: string, run: string, decision: { expectedRevision: number; expectedVersion: number; branchId: string; candidateId: string }, key: string) => normalize(await http.json(`${base(m, scene)}/${run}/accept`, { method: "POST", body: JSON.stringify(decision), headers: { "Idempotency-Key": key } })),
  };
}
