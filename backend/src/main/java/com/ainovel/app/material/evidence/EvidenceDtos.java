package com.ainovel.app.material.evidence;

import jakarta.validation.constraints.*;

import java.util.*;

public final class EvidenceDtos {
    private EvidenceDtos() {}

    public record Search(
            @NotBlank @Size(max = 2000) String query,
            @NotNull UUID storyId,
            String mode,
            String scope,
            @Min(1) @Max(40) Integer limit) {}

    public record Hit(
            String chunkId,
            UUID materialId,
            String revisionId,
            long sourceVersion,
            String title,
            String text,
            int start,
            int end,
            List<String> reasons,
            double rank) {}

    public record Results(String mode, String scope, List<Hit> items, List<String> degradation) {}

    public record Settings(
            long version,
            String semanticProfile,
            boolean rerank,
            boolean hints,
            boolean checks,
            List<UUID> bindings) {}

    public record SettingsWrite(
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 80) String requestKey,
            String semanticProfile,
            boolean rerank,
            boolean hints,
            boolean checks,
            @NotNull @Size(max = 500) List<UUID> bindings) {}

    public record Package(long version, List<String> pinned, List<String> excluded) {}

    public record PackageWrite(
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 80) String requestKey,
            @NotNull @Size(max = 8) List<String> pinned,
            @NotNull @Size(max = 500) List<String> excluded) {}

    public record EntityWrite(
            @NotBlank @Size(max = 255) String name,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> aliases,
            @NotNull @Size(max = 500) Map<UUID, @Min(1) Long> materials,
            @NotBlank @Size(max = 80) String requestKey) {}

    public record ConfirmCandidate(
            @Min(0) @Max(29) int candidateIndex,
            @Min(1) long expectedVersion,
            @NotBlank @Size(max = 80) String requestKey) {}

    public record Annotation(
            String id, String revisionId, String kind, String name, Object candidate) {}

    public record Hint(
            @NotNull UUID manuscriptId,
            @NotNull UUID sceneId,
            @NotBlank @Size(max = 2000) String query) {}

    public record Entity(String id, String name, List<String> aliases) {}

    public record SourceStatus(
            UUID materialId,
            String revisionId,
            long version,
            String saved,
            String review,
            String basic,
            String semantic) {}

    public record SemanticResumeWrite(
            @Min(1) long expectedVersion, @NotBlank @Size(max = 80) String requestKey) {}

    public record SemanticResume(String revisionId, String profile, String status) {}

    public record SourceRevision(String id, long version, String title, String createdAt) {}

    public record RawRevision(
            String id, UUID materialId, long version, String title, String content) {}

    public record Duplicate(
            UUID first, UUID second, String kind, String firstText, String secondText) {}

    public record CitationWrite(
            @NotBlank String revisionId,
            @NotBlank String branchId,
            @NotBlank String bodyVersion,
            @NotBlank String relationType,
            int start,
            int end,
            @NotBlank String quote,
            @NotBlank String bodyQuote,
            @NotBlank @Size(max = 80) String requestKey,
            @Size(max = 80) String bodyBlockId,
            @Min(0) Long expectedManuscriptVersion) {
        public CitationWrite(
                String revisionId,
                String branchId,
                String bodyVersion,
                String relationType,
                int start,
                int end,
                String quote,
                String bodyQuote,
                String requestKey) {
            this(
                    revisionId,
                    branchId,
                    bodyVersion,
                    relationType,
                    start,
                    end,
                    quote,
                    bodyQuote,
                    requestKey,
                    null,
                    null);
        }
    }

    public record CitationBody(
            UUID branchId,
            long manuscriptVersion,
            List<com.ainovel.app.narrative.NarrativeDtos.Block> blocks) {}
}
