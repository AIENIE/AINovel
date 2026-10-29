import type { DomainTransport } from './transport';
export type WorldChoice = 'AUTO' | 'INCLUDE' | 'EXCLUDE';
export type WorldSource = { id: string; type: string; title: string; content: string; contentHash: string; choice: WorldChoice; reason: string; estimatedTokens: number; required: boolean };
export type WorldSelection = { compilerVersion: string; binding: { worldId: string; versionId: string; name: string; sequenceNumber: number; sourceType: string } | null; selectionRevision: number; contextHash: string; tokenBudget: number; tokenUsed: number; content: string; included: WorldSource[]; excluded: WorldSource[]; issues: string[] };
export type WorldPreferences = { worldId: string | null; revision: number; reviewedVersionId: string | null; choices: Record<string, WorldChoice>; reviewedHashes: Record<string, string> };
export type WorldPreview = { purpose: string; contextHash: string; preferences: WorldPreferences; selection: WorldSelection | null; compiledContext: string; firstSources: {id:string;title:string;content:string}[]; availableSources: {id:string;title:string;content:string}[] };
export type OutlineAiRequest = { action: 'CHAPTER_GENERATE' | 'CHAPTER_REFINE' | 'SCENE_REFINE'; nodeId?: string; instruction?: string; text?: string; chapterNumber?: number; sectionsPerChapter?: number; expectedRevision?: number; contextHash?: string; expectedSelectionRevision?: number };
export type OutlineAiResult = { id: string; operationId: string; action: OutlineAiRequest['action']; nodeId: string | null; status: string; revision: number; sourceRevision: number; beforeText: string; candidate: {text?: string; title?: string; summary?: string; scenes?: {id:string;title:string;summary:string}[]} | null; worldContext: WorldSelection; rawResult: string | null; createdAt: string; chapterNumber:number; scenesCount:number; instruction:string };
export const outlineContextPath = (id: string) => `/v1/outlines/${id}/world-context`;
export const sceneContextPath = (id: string, scene: string) => `/v1/manuscripts/${id}/scenes/${scene}/world-context`;
export function createWorldContextApi(http: DomainTransport) {
  return {
    preview: (path: string, request: object) => http.json<WorldPreview>(`${path}/preview`, {method:'POST', body:JSON.stringify(request)}),
    save: (path: string, preview: WorldPreview, choices: Record<string,WorldChoice>) => http.json<WorldPreferences>(path, {method:'PUT',body:JSON.stringify({worldId:preview.selection?.binding?.worldId, versionId:preview.selection?.binding?.versionId, expectedRevision:preview.preferences.revision, choices})}),
    submitOutline: (id:string, request:OutlineAiRequest, key:string) => http.json<{resultId:string;operationId:string}>(`/v1/outlines/${id}/ai-operations`, {method:'POST',body:JSON.stringify(request),headers:{'Idempotency-Key':key}}),
    outlineResults: (id:string) => http.json<OutlineAiResult[]>(`/v1/outlines/${id}/ai-results`),
    acceptOutline: (id:string, result:OutlineAiResult, expectedOutlineRevision:number, contextHash:string, requestKey:string) => http.json<OutlineAiResult>(`/v1/outlines/${id}/ai-results/${result.id}/accept`, {method:'POST', body:JSON.stringify({expectedRevision:result.revision,expectedOutlineRevision,contextHash,requestKey})}),
  };
}
