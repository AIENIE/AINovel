export type SceneCandidate = {
  id: string; operationId: string | null; branchId: string; generationSequence: number | null;
  manuscriptVersion: number | null; minHan: number | null; maxHan: number | null; actualHan: number | null;
  status: string; qualityStatus: string | null; sourceKind: string | null; createdAt: string;
};
export type SceneCandidateDetail = {
  id: string; content: string; sourceFingerprint: string; qualityStatus: string | null; sourceKind: string | null;
};
export type SceneGenerationOutcome = {
  outcome: "SAVED" | "NEEDS_REVIEW"; manuscriptId: string; sceneId: string; candidateIds: string[];
  minHan: number; maxHan: number; actualHan: number; revisionCount: number; reviewReason?: string;
};
export function sceneGenerationOutcome(json?: string | null): SceneGenerationOutcome | null {
  try {
    const result = JSON.parse(json || "null");
    return result?.outcome === "SAVED" || result?.outcome === "NEEDS_REVIEW" ? result : null;
  } catch { return null; }
}
export const SCENE_CANDIDATES_EVENT = "ainovel:scene-candidates";
