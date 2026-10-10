package com.ainovel.app.material.evidence;

import jakarta.validation.constraints.*;

import java.util.*;

public final class EvidenceTaskDtos {
    private EvidenceTaskDtos() {}

    public record Request(
            @NotBlank String kind,
            UUID manuscriptId,
            UUID branchId,
            UUID bodyVersion,
            @Size(max = 300) List<UUID> scenes,
            String sourceRevision,
            @NotBlank @Size(max = 2000) String question,
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 80) String requestKey) {}

    public record Preview(
            boolean available,
            String reason,
            int externalLimit,
            int plannedExternal,
            long maximumCredits,
            String fingerprint) {}

    public record Submit(
            @jakarta.validation.Valid @NotNull Request request,
            @NotBlank String previewFingerprint,
            @Min(0) long acceptedMaximumCredits) {}

    public record Task(
            String id,
            String kind,
            String status,
            String errorCode,
            Object result,
            int externalLimit,
            int callsReserved,
            boolean stale,
            String sourceRevision) {}

    public record Opened(
            String id,
            String kind,
            String text,
            String revisionId,
            UUID bodyVersion,
            String sceneId,
            int start,
            String conversionVersion,
            String title) {
        public Opened(
                String id,
                String kind,
                String text,
                String revisionId,
                UUID bodyVersion,
                String sceneId,
                int start,
                String conversionVersion) {
            this(id, kind, text, revisionId, bodyVersion, sceneId, start, conversionVersion, "");
        }
    }

    public record Frozen(
            UUID storyId,
            UUID manuscriptId,
            UUID branchId,
            UUID bodyVersion,
            long manuscriptVersion,
            String question,
            List<Opened> opened,
            List<String> exclusions,
            String sourceFingerprint,
            long referenceSettingsVersion,
            long canonRevision,
            long outlineRevision,
            UUID liveBranchId,
            List<com.ainovel.app.narrative.NarrativeDtos.RecordView> confirmedConditions) {}
}
