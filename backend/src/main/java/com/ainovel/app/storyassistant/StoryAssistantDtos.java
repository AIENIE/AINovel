package com.ainovel.app.storyassistant;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class StoryAssistantDtos {
    private StoryAssistantDtos() {}
    public record SessionRequest(@NotBlank @Size(max=120) String title) {}
    public record SessionSummary(UUID id, UUID storyId, String title, Instant createdAt, Instant updatedAt) {}
    public record SessionDto(UUID id, UUID storyId, String title, List<TurnDto> turns, boolean hasMore, String nextBefore) {}
    public record TurnRequest(@NotNull UUID sessionId, @NotBlank String intent,
            @NotBlank @Size(max=8000) String instruction, UUID outlineId, List<UUID> sourceTurnIds,
            UUID suggestionId, UUID parentProposalId) {}
    public record Accepted(UUID turnId, UUID operationId) {}
    public record Source(String id, String type, String title, String content, String hash, boolean truncated, String reason) {}
    public record Suggestion(UUID id, String title, String reason, List<UUID> sourceTurnIds) {}
    public record Usage(double inputTokens, double outputTokens, double cost, int modelCalls) {}
    public record TurnDto(UUID id, UUID operationId, String status, String resultKind, String instruction,
            String answer, String intent, UUID outlineId, List<Suggestion> suggestions, List<UUID> proposalIds,
            UUID parentProposalId, List<Source> sources, List<String> warnings, Usage usage, Instant createdAt) {}
    public record ProposalDto(UUID id, UUID turnId, UUID parentProposalId, String action, String resourceType,
            UUID targetId, UUID outlineId, UUID parentId, String status, long revision, Long resourceRevision,
            JsonNode before, JsonNode candidate, JsonNode modelCandidate, JsonNode current, String rationale,
            List<Source> sources, List<UUID> sourceTurnIds, boolean applicable, String staleReason,
            boolean undoAllowed, Instant createdAt) {}
    public record EditRequest(@NotNull Long expectedRevision, @NotNull JsonNode candidate) {}
    public record DecisionRequest(@NotNull Long expectedRevision, Long expectedResourceRevision) {}
    public record DecisionResult(ProposalDto proposal, Long resourceRevision) {}
    public record ReadRequest(String tool, String query, String sourceId) {}
}
