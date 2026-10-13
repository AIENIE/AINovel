import { z } from "zod";
import type { DomainTransport } from "./transport";
import type { Manuscript } from "@/types";

const id = z.string().uuid();
export const assistantSelectionSchema = z.object({ startBlockIndex: z.number().int().nonnegative(), endBlockIndex: z.number().int().nonnegative(), startOffset: z.number().int().nonnegative(), endOffset: z.number().int().nonnegative() });
export type AssistantSelection = z.infer<typeof assistantSelectionSchema>;
export type AssistantIntent = "AUTO" | "EDIT" | "AUTHOR_QA";
export type AssistantTurnRequest = { branchId: string | null; expectedVersion: number; instruction: string; sourceHtml: string; selection: AssistantSelection | null; parentProposalId?: string; intent: AssistantIntent };
const sourceSchema = z.object({ id: z.string(), type: z.string(), title: z.string(), content: z.string(), sceneId: id.nullable().optional(), hash: z.string().nullable().optional(), truncated: z.boolean(), reason: z.string().nullable().optional() });
export const assistantTurnSchema = z.object({ id, operationId: id.nullable(), status: z.string(), resultKind: z.enum(["ANSWER", "NEEDS_INPUT", "PROPOSAL_READY"]).nullable(), instruction: z.string(), answer: z.string().nullable(), proposalId: id.nullable(), parentProposalId: id.nullable(), sources: sourceSchema.array(), warnings: z.string().array(), usage: z.object({ inputTokens: z.number(), outputTokens: z.number(), cost: z.number(), modelCalls: z.number() }), createdAt: z.string() });
export type AssistantTurn = z.infer<typeof assistantTurnSchema>;
const sessionSchema = z.object({ id: id.nullable(), branchId: id.nullable(), sceneId: id, h2Enabled: z.boolean(), turns: assistantTurnSchema.array(), hasMore: z.boolean(), nextBefore: z.string().nullable() });
export const assistantProposalSchema = z.object({ id, turnId: id, parentProposalId: id.nullable(), status: z.enum(["READY", "ACCEPTED", "REJECTED", "UNDONE"]), applicable: z.boolean(), staleReason: z.string().nullable(), beforeHtml: z.string(), afterHtml: z.string(), sourceVersion: z.number(), branchId: id.nullable(), actualHanBefore: z.number(), actualHanAfter: z.number(), minHan: z.number(), maxHan: z.number(), sourceHash: z.string() });
export type AssistantProposal = z.infer<typeof assistantProposalSchema>;
export type AssistantDecision = { proposalId: string; status: AssistantProposal["status"]; manuscript: Manuscript };
export function createWritingAssistantApi(http: DomainTransport, normalizeManuscript: (value: unknown) => Manuscript) {
  const base = (m: string, scene: string) => `/v1/manuscripts/${m}/scenes/${scene}/assistant`;
  const parse = async <T>(schema: z.ZodType<T>, path: string, init?: RequestInit) => schema.parse(await http.json(path, init));
  return {
    session: (m: string, scene: string, branch: string | null, before?: string, signal?: AbortSignal) => {
      const query = new URLSearchParams({ branchId: branch || "", limit: "20" });
      if (before) query.set("before", before);
      return parse(sessionSchema, `${base(m, scene)}?${query}`, { signal });
    },
    submit: (m: string, scene: string, request: AssistantTurnRequest, key: string) => parse(z.object({ turnId: id, operationId: id }), `${base(m, scene)}/turns`, { method: "POST", body: JSON.stringify(request), headers: { "Idempotency-Key": key } }),
    turn: (m: string, scene: string, turn: string, signal?: AbortSignal) => parse(assistantTurnSchema, `${base(m, scene)}/turns/${turn}`, { signal }),
    proposal: (m: string, scene: string, proposal: string, signal?: AbortSignal) => parse(assistantProposalSchema, `${base(m, scene)}/proposals/${proposal}`, { signal }),
    decide: async (m: string, scene: string, proposal: string, action: "accept" | "reject" | "undo", expectedVersion: number, branchId: string | null, key: string): Promise<AssistantDecision> => {
      const result = await parse(z.object({ proposalId: id, status: assistantProposalSchema.shape.status, manuscript: z.unknown() }), `${base(m, scene)}/proposals/${proposal}/${action}`, { method: "POST", body: JSON.stringify({ expectedVersion, branchId }), headers: { "Idempotency-Key": key } });
      return { ...result, manuscript: normalizeManuscript(result.manuscript) };
    },
  };
}
