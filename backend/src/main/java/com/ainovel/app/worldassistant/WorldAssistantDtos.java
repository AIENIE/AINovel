package com.ainovel.app.worldassistant;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class WorldAssistantDtos {
    private WorldAssistantDtos() {}
    public record SessionRequest(@NotBlank @Size(max=120) String title) {}
    public record SessionSummary(UUID id,UUID worldId,String title,Instant createdAt,Instant updatedAt) {}
    public record SessionDto(UUID id,UUID worldId,String title,List<TurnDto> turns,boolean hasMore,String nextBefore) {}
    public record TurnRequest(@NotNull UUID sessionId,@NotBlank String intent,@NotBlank @Size(max=8000) String instruction,
            List<UUID> sourceTurnIds,UUID suggestionId,UUID parentProposalId,Long expectedDraftRevision) {}
    public record SingleRequest(@NotNull Long expectedRevision,@Size(max=8000) String instruction) {}
    public record Accepted(UUID turnId,UUID operationId,UUID sessionId) {}
    public record Source(String id,String type,String title,String content,String hash,boolean truncated,String reason) {}
    public record Suggestion(UUID id,String title,String reason,List<UUID> sourceTurnIds) {}
    public record SemanticConflict(String title,String explanation,List<String> sourceIds) {}
    public record Usage(double inputTokens,double outputTokens,double cost,int modelCalls) {}
    public record FailureDiagnostics(String phase,String code,String finishReason,Long reasoningTokens,
            Long completionTokens,Boolean usageAuthoritative) {}
    public record TurnDto(UUID id,UUID operationId,String status,String resultKind,String instruction,String answer,String intent,
            List<Suggestion> suggestions,List<UUID> proposalIds,UUID parentProposalId,List<Source> sources,List<String> warnings,
            List<SemanticConflict> conflicts,Usage usage,Instant createdAt,FailureDiagnostics failure) {}
    public record ProposalDto(UUID id,UUID turnId,UUID sessionId,UUID parentProposalId,String action,String resourceType,
            UUID targetId,String fieldKey,String status,long revision,long draftRevision,JsonNode before,JsonNode candidate,
            JsonNode modelCandidate,JsonNode current,String rationale,List<Source> sources,List<UUID> sourceTurnIds,
            List<SemanticConflict> conflicts,boolean candidateEdited,boolean contextChanged,boolean applicable,String staleReason,Instant createdAt,List<com.ainovel.app.world.WorldDraftDtos.Change> contextChanges) {}
    public record ProposalPage(List<ProposalDto> items,boolean hasMore,String nextBefore) {}
    public record EditRequest(@NotNull Long expectedRevision,@NotNull JsonNode candidate) {}
    public record DecisionRequest(@NotNull Long expectedRevision,Long expectedDraftRevision,boolean acknowledgeContextChange) {}
    public record DecisionResult(ProposalDto proposal,long draftRevision) {}
    public record ReadRequest(String tool,String query,String sourceId) {}
}
