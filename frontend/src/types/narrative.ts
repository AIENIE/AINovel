export type NarrativeKind = "FACT" | "UTTERANCE" | "BELIEF" | "RUMOR" | "INFERENCE";
export type NarrativeEvidence = { blockId: string; quote: string; start?: number; end?: number };
export type NarrativeAssertion = {
  subject: string; characterId: string | null; statement: string; kind: NarrativeKind;
  holderCharacterId: string | null; worldTime: string | null; uncertainty: string;
  evidence: NarrativeEvidence[]; supersedesId: string | null;
};
export type NarrativeCandidate = { id: string; assertion: NarrativeAssertion | null; validationError: string | null };
export type NarrativePosition = { chapterId: string; chapterTitle: string; chapterOrder: number; sceneId: string; sceneTitle: string; sceneOrder: number; index: number; orderHash: string };
export type NarrativeExtraction = {
  id: string; approvalId: string; sceneId: string; operationId: string | null; status: string;
  stale: boolean; reviewed: boolean; baseCanonRevision: number; promptVersion: string; model: string | null;
  usage: { inputTokens: number; outputTokens: number; cost: number } | null;
  candidates: NarrativeCandidate[]; error: string | null; review: NarrativeReview | null; createdAt: string;
};
export type NarrativeRecord = {
  id: string; extractionId: string; approvalId: string; sceneId: string; disclosedAt: NarrativePosition;
  assertion: NarrativeAssertion; status: "CONFIRMED" | "STALE" | "SUPERSEDED"; createdRevision: number;
  invalidatedRevision: number | null; supersededRevision: number | null; createdAt: string;
};
export type NarrativeState = {
  canonRevision: number; manuscriptVersion: number; branchId: string;
  records: NarrativeRecord[]; extractions: NarrativeExtraction[];
};
export type NarrativeSource = {
  approvalId: string; versionId: string; sceneId: string; disclosedAt: NarrativePosition;
  blocks: Array<{ id: string; text: string }>; textHash: string; confirmedAt: string;
};
export type NarrativeReview = {
  expectedManuscriptVersion: number; expectedCanonRevision: number;
  decisions: Array<{ candidateId: string; decision: "ACCEPT" | "REJECT"; edited: NarrativeAssertion | null }>;
  additions: NarrativeAssertion[];
};
