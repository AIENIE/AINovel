import { z } from "zod";
import type { DomainTransport } from "./transport";

const id = z.string().uuid();
export const storyAssistantSourceSchema = z.object({ id: z.string(), type: z.string(), title: z.string(), content: z.string(), hash: z.string().nullable().optional(), truncated: z.boolean(), reason: z.string().nullable().optional() });
export type StoryAssistantSource = z.infer<typeof storyAssistantSourceSchema>;
const suggestionSchema = z.object({ id: z.string(), title: z.string(), reason: z.string(), sourceTurnIds: id.array() });
export type StoryAssistantSuggestion = z.infer<typeof suggestionSchema>;
export const storyAssistantTurnSchema = z.object({ id, operationId: id.nullable(), status: z.string(), resultKind: z.string().nullable(), instruction: z.string(), answer: z.string().nullable(), intent: z.enum(["DISCUSS", "PROPOSE", "REFINE"]), outlineId: id.nullable().optional(), suggestions: suggestionSchema.array(), proposalIds: id.array(), parentProposalId: id.nullable().optional(), sources: storyAssistantSourceSchema.array(), warnings: z.string().array(), usage: z.object({ inputTokens: z.number(), outputTokens: z.number(), cost: z.number(), modelCalls: z.number() }), createdAt: z.string() });
export type StoryAssistantTurn = z.infer<typeof storyAssistantTurnSchema>;
const summarySchema = z.object({ id, storyId: id, title: z.string(), createdAt: z.string(), updatedAt: z.string() });
const sessionSchema = z.object({ id: id.nullable(), storyId: id, title: z.string().nullable(), turns: storyAssistantTurnSchema.array(), hasMore: z.boolean(), nextBefore: z.string().nullable() });
export type StoryAssistantSession = z.infer<typeof sessionSchema>;
export type StoryAssistantCandidate = Record<string, unknown>;
export const storyAssistantProposalSchema = z.object({ id, turnId: id, parentProposalId: id.nullable().optional(), action: z.enum(["CREATE", "UPDATE"]), resourceType: z.enum(["CHARACTER", "CHAPTER", "SCENE"]), targetId: id.nullable(), outlineId: id.nullable(), parentId: id.nullable(), status: z.enum(["READY", "ACCEPTED", "REJECTED", "UNDONE"]), revision: z.number(), resourceRevision: z.number().nullable(), before: z.record(z.unknown()).nullable(), candidate: z.record(z.unknown()), modelCandidate: z.record(z.unknown()), current: z.record(z.unknown()).nullable(), rationale: z.string().nullable(), sources: storyAssistantSourceSchema.array(), sourceTurnIds: id.array(), applicable: z.boolean(), staleReason: z.string().nullable(), undoAllowed: z.boolean(), createdAt: z.string() });
export type StoryAssistantProposal = z.infer<typeof storyAssistantProposalSchema>;
export type StoryAssistantTurnRequest = { sessionId: string; intent: "DISCUSS" | "PROPOSE" | "REFINE"; instruction: string; outlineId?: string; sourceTurnIds?: string[]; suggestionId?: string; parentProposalId?: string };
const decisionSchema = z.object({ proposal: storyAssistantProposalSchema, resourceRevision: z.number().nullable() });

export function createStoryAssistantApi(http: DomainTransport) {
  const base = (story: string) => `/v1/story-cards/${story}/assistant`;
  const parse = async <T>(schema: z.ZodType<T>, path: string, init?: RequestInit) => schema.parse(await http.json(path, init));
  const write = (payload: unknown, key: string, method = "POST") => ({ method, body: JSON.stringify(payload), headers: { "Idempotency-Key": key } });
  return {
    sessions: (story: string, signal?: AbortSignal) => parse(summarySchema.array(), `${base(story)}/sessions`, { signal }),
    createSession: (story: string, title: string, key: string) => parse(summarySchema, `${base(story)}/sessions`, write({ title }, key)),
    renameSession: (story: string, session: string, title: string, key: string) => parse(summarySchema, `${base(story)}/sessions/${session}`, write({ title }, key, "PUT")),
    session: (story: string, session?: string, before?: string, signal?: AbortSignal) => {
      const params = new URLSearchParams({ limit: "20" });
      if (session) params.set("sessionId", session);
      if (before) params.set("before", before);
      return parse(sessionSchema, `${base(story)}?${params}`, { signal });
    },
    submit: (story: string, request: StoryAssistantTurnRequest, key: string) => parse(z.object({ turnId: id, operationId: id }), `${base(story)}/turns`, write(request, key)),
    turn: (story: string, turn: string, signal?: AbortSignal) => parse(storyAssistantTurnSchema, `${base(story)}/turns/${turn}`, { signal }),
    proposal: (story: string, proposal: string, signal?: AbortSignal) => parse(storyAssistantProposalSchema, `${base(story)}/proposals/${proposal}`, { signal }),
    editProposal: (story: string, proposal: string, expectedRevision: number, candidate: StoryAssistantCandidate, key: string) => parse(storyAssistantProposalSchema, `${base(story)}/proposals/${proposal}`, write({ expectedRevision, candidate }, key, "PUT")),
    decide: (story: string, proposal: string, action: "accept" | "reject" | "undo", expectedRevision: number, expectedResourceRevision: number | null, key: string) => parse(decisionSchema, `${base(story)}/proposals/${proposal}/${action}`, write({ expectedRevision, ...(expectedResourceRevision === null ? {} : { expectedResourceRevision }) }, key)),
  };
}
