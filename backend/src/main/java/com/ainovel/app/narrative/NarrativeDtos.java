package com.ainovel.app.narrative;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NarrativeDtos {
    private NarrativeDtos() {}
    public enum Kind { FACT, UTTERANCE, BELIEF, RUMOR, INFERENCE }
    public enum Decision { ACCEPT, REJECT }
    public record Block(String id, String text) {}
    public record Evidence(String blockId, String quote, Integer start, Integer end) {}
    public record Position(UUID chapterId, String chapterTitle, int chapterOrder,
                           UUID sceneId, String sceneTitle, int sceneOrder, int index, String orderHash) {}
    public record Assertion(@NotBlank @Size(max=300) String subject,
                            UUID characterId, @NotBlank @Size(max=2000) String statement,
                            @NotNull Kind kind, UUID holderCharacterId,
                            @Size(max=500) String worldTime, @Size(max=1000) String uncertainty,
                            @NotEmpty @Size(max=10) List<Evidence> evidence, UUID supersedesId) {}
    public record Candidate(String id, Assertion assertion, String validationError) {}
    public record ApprovalRequest(@NotNull UUID sceneId, @NotNull Long expectedManuscriptVersion,
                                  @NotNull Long expectedCanonRevision) {}
    public record Approved(UUID approvalId, UUID extractionId, UUID operationId) {}
    public record ReviewItem(@NotBlank String candidateId, @NotNull Decision decision, @Valid Assertion edited) {}
    public record ReviewRequest(@NotNull Long expectedManuscriptVersion, @NotNull Long expectedCanonRevision,
                                @NotNull @Size(max=100) List<@Valid ReviewItem> decisions,
                                @NotNull @Size(max=100) List<@Valid Assertion> additions) {}
    public record ReviewResult(UUID commitId, long canonRevision, List<UUID> recordIds) {}
    public record RecordView(UUID id, UUID extractionId, UUID approvalId, UUID sceneId, Position disclosedAt,
                             Assertion assertion, String status, long createdRevision, Long invalidatedRevision,
                             Long supersededRevision, Instant createdAt) {}
    public record ExtractionView(UUID id, UUID approvalId, UUID sceneId, UUID operationId, String status,
                                 boolean stale, boolean reviewed, long baseCanonRevision, String promptVersion,
                                 String model, Object usage, List<Candidate> candidates, String error,
                                 Object review, Instant createdAt) {}
    public record StateView(long canonRevision, long manuscriptVersion, UUID branchId,
                            List<RecordView> records, List<ExtractionView> extractions) {}
    public record EvidenceView(UUID approvalId, UUID versionId, UUID sceneId, Position disclosedAt,
                               List<Block> blocks, String textHash, Instant confirmedAt) {}
    public record InputRecord(UUID id, Assertion assertion) {}
    public record ExtractionInput(UUID extractionId, UUID approvalId, UUID storyId,
                                  List<Block> blocks, List<InputRecord> previousRecords, Object entities) {}
}
