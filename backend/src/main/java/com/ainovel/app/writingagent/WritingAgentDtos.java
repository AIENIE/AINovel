package com.ainovel.app.writingagent;

import com.ainovel.app.assistant.AssistantDocumentProjection;
import com.ainovel.app.assistant.AssistantDtos.Source;
import com.ainovel.app.assistant.AssistantContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class WritingAgentDtos {
    private WritingAgentDtos() {}
    public static final String PROTOCOL="writing-agent-v1";
    public record Limits(int modelCalls,int controlCalls,int paidTools,int revisions,int localTools) {
        public static Limits defaults(){return new Limits(24,12,12,3,32);}
        public Limits checked(){
            if(modelCalls<1||modelCalls>24||controlCalls<1||controlCalls>12||paidTools<0||paidTools>12||revisions<0||revisions>3||localTools<0||localTools>32)throw error("WRITING_AGENT_LIMIT_INVALID");
            return this;
        }
    }
    public record Request(UUID branchId,Long expectedVersion,String sourceHtml,
            AssistantDocumentProjection.Selection selection,String instruction,String intent,String mode,
            boolean languageCheck,boolean plotCheck,BigDecimal maxCostCny,Long maxProjectCredits,
            Limits limits,String contextHash,Long expectedSelectionRevision) {}
    public record Frozen(String protocol,UUID manuscriptId,UUID branchId,UUID sceneId,long sourceVersion,
            String serverHash,String instruction,String intent,String mode,
            AssistantDocumentProjection.Document document,AssistantContext.Snapshot context,
            WorldAgentTools.FrozenWorld world,List<Source> base,String languageRules,List<AiChatRequest.Message> generationRules,String planningHash,
            boolean languageCheck,boolean plotCheck,BigDecimal maxCostCny,long maxProjectCredits,Limits limits,
            AiSelectionContext.Snapshot selections,String toolsJson,List<AiChatRequest.Message> controlMessages,String candidateTemplateHash) {
        public Frozen(String protocol,UUID manuscriptId,UUID branchId,UUID sceneId,long sourceVersion,String serverHash,String instruction,String intent,String mode,
                AssistantDocumentProjection.Document document,AssistantContext.Snapshot context,WorldAgentTools.FrozenWorld world,List<Source> base,String languageRules,
                List<AiChatRequest.Message> generationRules,String planningHash,boolean languageCheck,boolean plotCheck,BigDecimal maxCostCny,long maxProjectCredits,Limits limits,AiSelectionContext.Snapshot selections){
            this(protocol,manuscriptId,branchId,sceneId,sourceVersion,serverHash,instruction,intent,mode,document,context,world,base,languageRules,generationRules,planningHash,languageCheck,plotCheck,maxCostCny,maxProjectCredits,limits,selections,null,List.of(),null);
        }
        public Frozen withProtocol(String tools,List<AiChatRequest.Message> initial,String template){return new Frozen(protocol,manuscriptId,branchId,sceneId,sourceVersion,serverHash,instruction,intent,mode,document,context,world,base,languageRules,generationRules,planningHash,languageCheck,plotCheck,maxCostCny,maxProjectCredits,limits,selections,tools,initial,template);}
        public Frozen withSelection(AiSelectionContext.Snapshot selection){return new Frozen(protocol,manuscriptId,branchId,sceneId,sourceVersion,serverHash,instruction,intent,mode,document,context,world,base,languageRules,generationRules,planningHash,languageCheck,plotCheck,maxCostCny,maxProjectCredits,limits,selection,toolsJson,controlMessages,candidateTemplateHash);}
    }
    public record Preview(String contextHash,long selectionRevision,String protocol,boolean h2,
            Object binding,List<String> tools,List<String> issues,Limits limits,BigDecimal maxCostCny,long maxProjectCredits,List<AiChatRequest.Message> firstInput) {}
    public record Candidate(UUID id,int sequence,UUID parentId,String html,String hash,int actualHan,
            List<AssistantDocumentProjection.Replacement> replacements,List<JsonNode> sources) {}
    public record StepView(int sequence,String kind,String feature,String status,JsonNode result,
            BigDecimal reservedCny,BigDecimal settledCny,Long projectCredits) {}
    public record View(UUID id,UUID manuscriptId,UUID sceneId,UUID branchId,String status,long revision,
            UUID operationId,JsonNode progress,List<Candidate> candidates,List<StepView> steps,
            BigDecimal settledCny,BigDecimal reservedCny,long projectCredits,BigDecimal maxCostCny,
            long maxProjectCredits,Instant createdAt,String sourceHtml,long sourceVersion,String intent,boolean h2,Object worldBinding,long selectionRevision) {}
    public record Accepted(UUID runId,UUID operationId) {}
    public record Revision(long expectedRevision,String answer) {public Revision(long expectedRevision){this(expectedRevision,null);}}
    public record Decision(long expectedRevision,long expectedVersion,UUID branchId,UUID candidateId) {}
    public record ModelRecord(AiChatRequest request,String feature) {}
    public static com.ainovel.app.common.ApiStatusException error(String code){return new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT,code);}
}
