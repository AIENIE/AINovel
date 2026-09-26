import { z } from "zod";

const id = z.string().min(1);
const version = z.number().int().nonnegative();
export const manuscriptSummarySchema = z.object({
  id, outlineId: id, title: z.string(), worldId: z.string().nullable().optional(),
  currentBranchId: id.nullable().optional(), version, updatedAt: z.string(),
});
export const manuscriptSchema = manuscriptSummarySchema.extend({
  sections: z.record(z.string()), lastGenerationRun: z.object({ id, generationVersionId: id,
    status: z.string(), createdAt: z.string() }).nullable().optional(),
});
export const sceneContentSchema = z.object({ manuscriptId: id, branchId: id.nullable(), sceneId: id,
  content: z.string(), version, updatedAt: z.string() });
export type SceneContent = z.infer<typeof sceneContentSchema>;
export type ManuscriptSectionUpdate = { id: string; currentBranchId?: string | null; version: number;
  updatedAt: string; sections: Record<string, string> };

// Critical write/recovery domains validate network data before it enters editor state.
export const branchStatusSchema = z.enum(["active", "abandoned", "merged"]);
export const branchSchema = z.object({ id, manuscriptId: id, name: z.string(), description: z.string().nullable(),
  sourceVersionId: id.nullable(), status: branchStatusSchema, isMain: z.boolean(), createdAt: z.string(), updatedAt: z.string() });
export const createBranchSchema = z.object({ name: z.string().trim().min(1).max(100), description: z.string().optional(), sourceVersionId: id.optional() }).strict();
export const updateBranchSchema = z.object({ name: z.string().trim().min(1).max(100).optional(), description: z.string().optional(), status: branchStatusSchema.optional() }).strict();
export const mergeBranchSchema = z.object({ strategy: z.enum(["REPLACE_ALL", "SCENE_SELECT"]).optional(),
  sceneResolutions: z.record(z.enum(["target", "source"])).optional(), label: z.string().trim().min(1).max(200).optional() }).strict();
export const conflictSchema = z.object({ sceneId: id, targetLength: version, sourceLength: version,
  mainContent: z.string(), branchContent: z.string(), reason: z.string() });
const mergeBase = z.object({ manuscriptId: id, sourceBranchId: id, targetBranchId: id });
export const mergeResultSchema = z.discriminatedUnion("status", [
  mergeBase.extend({ status: z.literal("conflict"), conflicts: z.array(conflictSchema) }),
  mergeBase.extend({ status: z.literal("merged"), mergeVersionId: id, backupVersionId: id }),
]);
export const versionSchema = z.object({ id, manuscriptId: id, branchId: id, versionNumber: version,
  label: z.string().nullable(), snapshotType: z.string(), contentHash: z.string(), sectionsJson: z.string(),
  metadata: z.record(z.unknown()), parentVersionId: id.nullable(), createdBy: id, createdAt: z.string(), deduplicated: z.boolean().optional() });
export const diffSchema = z.object({ fromVersionId: id, toVersionId: id, changedScenes: version,
  generatedAt: z.string(), changes: z.array(z.object({ sceneId: id, beforeLength: version, afterLength: version,
    beforeWordCount: version, afterWordCount: version, delta: z.number().int(), beforeContent: z.string(), afterContent: z.string() })) });
export const autoSaveSchema = z.object({ autoSaveIntervalSeconds: z.number().int().min(30), maxAutoVersions: z.number().int().min(10) });
export const exportFormatSchema = z.enum(["txt", "docx", "epub", "pdf"]);
export const exportConfigSchema = z.object({ includeTitlePage: z.boolean().optional(), includeTableOfContents: z.boolean().optional(),
  includeToc: z.boolean().optional(), txtEncoding: z.string().optional(), encoding: z.string().optional(), lineEnding: z.string().optional(),
  includeMetadata: z.boolean().optional(), authorName: z.string().optional(), selectedSceneIds: z.array(id).optional(), lineSpacing: z.number().optional() }).catchall(z.unknown());
export const exportRequestSchema = z.object({ format: exportFormatSchema.optional(), templateId: id.optional(),
  config: exportConfigSchema.optional(), chapterRange: z.string().max(200).optional() }).strict();
export const templateRequestSchema = z.object({ name: z.string().min(1).max(100), description: z.string().max(500).optional(),
  format: exportFormatSchema, config: exportConfigSchema.optional(), isDefault: z.boolean().optional() }).strict();
export const exportJobSchema = z.object({ id, manuscriptId: id, userId: id, storyId: id, templateId: id.nullable(), format: exportFormatSchema,
  config: exportConfigSchema, chapterRange: z.string().nullable(), status: z.enum(["queued", "processing", "completed", "failed", "expired", "cancelled"]),
  progress: z.number().int().min(0).max(100), fileName: z.string().nullable(), filePath: z.string().nullable(), fileSizeBytes: version,
  errorMessage: z.string().nullable(), checksum: z.string().nullable(), contentType: z.string(), expiresAt: z.string().nullable(),
  createdAt: z.string(), startedAt: z.string().nullable(), completedAt: z.string().nullable() });
export const exportTemplateSchema = z.object({ id, userId: id.nullable(), name: z.string(), description: z.string().nullable(),
  format: exportFormatSchema, config: exportConfigSchema, isDefault: z.boolean(), createdAt: z.string(), updatedAt: z.string() });
export const aiOperationAcceptedSchema = z.object({ operationId: id });
export const aiOperationProgressSchema = z.object({ id, operationType: z.string(), scopeType: z.string().nullish(), scopeId: id.nullish(),
  status: z.enum(["QUEUED", "RUNNING", "STREAMING", "RECOVERY_REQUIRED", "SUCCEEDED", "FAILED", "CANCELLED"]),
  currentStep: z.string().nullish(), totalSteps: version, completedSteps: version, remainingSteps: version, currentStepOutputTokens: version,
  outputTokensEstimated: z.boolean(), attemptCount: version, resultJson: z.string().nullish(), errorMessage: z.string().nullish(),
  createdAt: z.string().nullish(), updatedAt: z.string().nullish(), completedAt: z.string().nullish() });
export type Branch = z.infer<typeof branchSchema>;
export type BranchUpdate = z.infer<typeof updateBranchSchema>;
export type MergeConflict = z.infer<typeof conflictSchema>;
export type VersionDiff = z.infer<typeof diffSchema>;
export type AutoSaveConfig = z.infer<typeof autoSaveSchema>;
export type ExportTemplate = z.infer<typeof exportTemplateSchema>;
export type ExportFormat = z.infer<typeof exportFormatSchema>;
