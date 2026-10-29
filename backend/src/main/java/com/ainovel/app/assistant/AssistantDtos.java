package com.ainovel.app.assistant;

import com.ainovel.app.manuscript.dto.ManuscriptDto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AssistantDtos {
    private AssistantDtos() {}
    public record TurnRequest(@NotNull UUID branchId, @NotNull Long expectedVersion,
            @NotBlank @Size(max=8000) String instruction, @NotNull String sourceHtml,
            AssistantDocumentProjection.Selection selection, UUID parentProposalId, String intent,
            String contextHash,Long expectedSelectionRevision) {
        public TurnRequest(UUID branchId,Long expectedVersion,String instruction,String sourceHtml,AssistantDocumentProjection.Selection selection,UUID parentProposalId,String intent){this(branchId,expectedVersion,instruction,sourceHtml,selection,parentProposalId,intent,null,null);}
    }
    public record DecisionRequest(@NotNull Long expectedVersion,@NotNull UUID branchId) {}
    public record Accepted(UUID turnId,UUID operationId) {}
    public record Source(String id,String type,String title,String content,UUID sceneId,String hash,boolean truncated,String reason) {}
    public record Usage(double inputTokens,double outputTokens,double cost,int modelCalls) {
        public static Usage zero(){return new Usage(0,0,0,0);}
    }
    public record TurnDto(UUID id,UUID operationId,String status,String resultKind,String instruction,String answer,
            UUID proposalId,UUID parentProposalId,List<Source> sources,List<String> warnings,Usage usage,Instant createdAt,
            com.ainovel.app.worldcontext.WorldContextDtos.Selection worldContext,List<StepSources> steps) {}
    public record StepSources(String step,List<Source> sources) {}
    public record SessionDto(UUID id,UUID branchId,UUID sceneId,boolean h2Enabled,List<TurnDto> turns,boolean hasMore,String nextBefore) {}
    public record ProposalDto(UUID id,UUID turnId,UUID parentProposalId,String status,boolean applicable,String staleReason,
            String beforeHtml,String afterHtml,long sourceVersion,UUID branchId,int actualHanBefore,int actualHanAfter,
            int minHan,int maxHan,String sourceHash) {}
    public record DecisionResult(UUID proposalId,String status,ManuscriptDto manuscript) {}
    public record ReadRequest(String tool,String query,String sourceId) {}
}
