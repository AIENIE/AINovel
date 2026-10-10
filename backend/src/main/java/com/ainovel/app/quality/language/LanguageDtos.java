package com.ainovel.app.quality.language;

import java.time.Instant;
import java.util.*;

public final class LanguageDtos {
    private LanguageDtos() {}
    public enum Kind { LANGUAGE, STYLE, OBSERVATION }
    public enum Category { OMISSION, COMPRESSION, CHOPPY, AWKWARD, PARAGRAPH }
    public enum Status { UNCHECKED, CHECKING, ISSUES, NO_CLEAR_ISSUES, INCOMPLETE, FAILED, LOCAL_ONLY, STALE }
    public enum Verdict { PASS, UNCERTAIN, FAIL }
    public record Settings(boolean available, boolean generationStandard, boolean checkAfterGeneration, String standardVersion) {}
    public record SettingsRequest(boolean generationStandard, boolean checkAfterGeneration) {}
    public record Source(UUID branchId, long bodyVersion, UUID snapshotId, String projectionVersion, String htmlHash,
                         String textHash, String contextVersion, String standardVersion) {}
    public record CheckRequest(UUID expectedBranchId, long expectedVersion) {}
    public record DecisionRequest(UUID expectedBranchId, long expectedVersion) {}
    public record Issue(String id, Kind kind, Category category, String quote, String impact, String direction,
                        Integer start, Integer end, String location, int paragraph, String context,
                        int currentStart, int currentEnd, String availability) {}
    public record Coverage(int index, int start, int end, int contextStart, int contextEnd, String state, String reason) {}
    public record Review(Verdict language, String languageReason, Verdict meaning, String meaningReason, List<String> changes) {}
    public record PatchDto(UUID id, UUID reportId, String issueId, String original, String replacement,
                           String beforeContext, String afterContext, Review review, String status, String applicability,
                           UUID operationId, UUID appliedSnapshotId, UUID undoneSnapshotId, Instant createdAt, String reason) {}
    public record ReportDto(UUID id, UUID manuscriptId, UUID sceneId, Source source, Status status, String summary,
                            List<Issue> issues, List<Coverage> coverage, List<PatchDto> patches, UUID operationId,
                            boolean canContinueBatch, Instant createdAt) {}
    public record DecisionResult(PatchDto patch, long bodyVersion, UUID branchId, String content) {}
    /** Persisted document, including immutable projection and independently checkpointed chunks. */
    public static class ReportData {
        public Source source;
        public String profileHash;
        public LanguageProjection.Projection projection;
        public Status status=Status.CHECKING;
        public String summary="等待语言检查";
        public List<Issue> issues=new ArrayList<>();
        public List<Coverage> coverage=new ArrayList<>();
        public long applicableVersion;
        public String applicableHtmlHash;
        public UUID operationId;
    }
    public static class PatchData {
        public String reason;
        public String original;
        public String replacement;
        public String beforeContext;
        public int contextOffset;
        public String afterContext;
        public Review review;
        public String status="GENERATING";
        public String applicability="PENDING";
        public int start;
        public int end;
        public int paragraph;
        public long applicableVersion;
        public String applicableHtmlHash;
        public UUID operationId;
        public UUID appliedSnapshotId;
        public UUID undoneSnapshotId;
        public String candidateRaw;
        public String reviewRaw;
    }
}
