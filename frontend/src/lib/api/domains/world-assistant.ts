import { z } from "zod";
import type { DomainTransport } from "./transport";
import { storyAssistantSourceSchema } from "./story-assistant";
const id = z.string().uuid();
const conflict = z.object({title:z.string(),explanation:z.string(),sourceIds:z.string().array()});
const failure=z.object({phase:z.enum(["plan","final"]),code:z.string(),finishReason:z.string().nullable(),reasoningTokens:z.number().nullable(),completionTokens:z.number().nullable(),usageAuthoritative:z.boolean().nullable()});
export const worldTurnSchema=z.object({id,operationId:id.nullable(),status:z.string(),resultKind:z.string().nullable(),instruction:z.string(),answer:z.string().nullable(),intent:z.string(),suggestions:z.object({id,title:z.string(),reason:z.string(),sourceTurnIds:id.array()}).array(),proposalIds:id.array(),parentProposalId:id.nullable(),sources:storyAssistantSourceSchema.array(),warnings:z.string().array(),conflicts:conflict.array(),usage:z.object({inputTokens:z.number(),outputTokens:z.number(),cost:z.number(),modelCalls:z.number()}),createdAt:z.string(),failure:failure.nullish()});
export type WorldTurn=z.infer<typeof worldTurnSchema>;
const summary=z.object({id,worldId:id,title:z.string(),createdAt:z.string(),updatedAt:z.string()});
const session=z.object({id:id.nullable(),worldId:id,title:z.string().nullable(),turns:worldTurnSchema.array(),hasMore:z.boolean(),nextBefore:z.string().nullable()});
export const worldProposalSchema=z.object({id,turnId:id,sessionId:id,parentProposalId:id.nullable(),action:z.enum(["CREATE","UPDATE"]),resourceType:z.enum(["METADATA","ENTRY","RELATION","OVERVIEW","ERA","EVENT"]),targetId:id,fieldKey:z.string().nullable(),status:z.enum(["READY","ACCEPTED","REJECTED"]),revision:z.number(),draftRevision:z.number(),before:z.record(z.unknown()).nullable(),candidate:z.record(z.unknown()),modelCandidate:z.record(z.unknown()),current:z.record(z.unknown()).nullable(),rationale:z.string(),sources:storyAssistantSourceSchema.array(),sourceTurnIds:id.array(),conflicts:conflict.array(),contextChanges:z.object({kind:z.enum(["METADATA","ENTRY","RELATION","TIMELINE","ERA","EVENT"]),id:z.string(),label:z.string(),before:z.unknown(),after:z.unknown()}).array(),candidateEdited:z.boolean(),contextChanged:z.boolean(),applicable:z.boolean(),staleReason:z.string().nullable(),createdAt:z.string()});
export type WorldProposal=z.infer<typeof worldProposalSchema>;
export type WorldTurnRequest={sessionId:string;intent:"DISCUSS"|"PROPOSE"|"REFINE";instruction:string;sourceTurnIds?:string[];suggestionId?:string;parentProposalId?:string;expectedDraftRevision:number};
const accepted=z.object({turnId:id,operationId:id,sessionId:id});
export function createWorldAssistantApi(http:DomainTransport) {
  const base=(world:string)=>`/v1/worlds/${world}/assistant`;
  const parse=async <T>(schema:z.ZodType<T>,path:string,init?:RequestInit)=>schema.parse(await http.json(path,init));
  const write=(payload:unknown,key:string,method="POST")=>({method,body:JSON.stringify(payload),headers:{"Idempotency-Key":key}});
  return {
    sessions:(world:string,signal?:AbortSignal)=>parse(summary.array(),`${base(world)}/sessions`,{signal}),
    createSession:(world:string,title:string,key:string)=>parse(summary,`${base(world)}/sessions`,write({title},key)),
    renameSession:(world:string,sessionId:string,title:string,key:string)=>parse(summary,`${base(world)}/sessions/${sessionId}`,write({title},key,"PUT")),
    session:(world:string,sessionId?:string,before?:string,signal?:AbortSignal)=>{const params=new URLSearchParams({limit:"20"});if(sessionId)params.set("sessionId",sessionId);if(before)params.set("before",before);return parse(session,`${base(world)}?${params}`,{signal});},
    submit:(world:string,request:WorldTurnRequest,key:string)=>parse(accepted,`${base(world)}/turns`,write(request,key)),
    proposals:(world:string,filters:{status?:string;type?:string;sessionId?:string;before?:string},signal?:AbortSignal)=>parse(z.object({items:worldProposalSchema.array(),hasMore:z.boolean(),nextBefore:z.string().nullable()}),`${base(world)}/proposals?${new URLSearchParams(Object.entries(filters).filter(([,v])=>!!v) as [string,string][])}`,{signal}),
    proposal:(world:string,proposal:string,signal?:AbortSignal)=>parse(worldProposalSchema,`${base(world)}/proposals/${proposal}`,{signal}),
    editProposal:(world:string,proposal:string,expectedRevision:number,candidate:Record<string,unknown>,key:string)=>parse(worldProposalSchema,`${base(world)}/proposals/${proposal}`,write({expectedRevision,candidate},key,"PUT")),
    decide:(world:string,proposal:string,action:"accept"|"reject",expectedRevision:number,expectedDraftRevision:number,acknowledgeContextChange:boolean,key:string)=>parse(z.object({proposal:worldProposalSchema,draftRevision:z.number()}),`${base(world)}/proposals/${proposal}/${action}`,write({expectedRevision,expectedDraftRevision,acknowledgeContextChange},key)),
    single:(world:string,entryId:string|null,expectedRevision:number,instruction:string,key:string)=>parse(accepted,`/v1/worlds/${world}/${entryId?`entries/${entryId}/refine`:"overview/integrate"}/operations`,write({expectedRevision,instruction},key)),
  };
}
