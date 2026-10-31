package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiResponseMetadata;
import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.economy.repo.AiCreditReservationRepository;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatCompletionsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Objects;

/** Read-only compatibility for safe metadata in original, owned durable response receipts. */
@Service
public class AiStoredResponseMetadata {
    private final AiCreditReservationRepository reservations;
    private final AiValidationCallRepository calls;
    private final ObjectMapper json;
    private final ExternalServiceProperties properties;

    public AiStoredResponseMetadata(AiCreditReservationRepository reservations,AiValidationCallRepository calls,
                                    ObjectMapper json,ExternalServiceProperties properties) {
        this.reservations=reservations;this.calls=calls;this.json=json;this.properties=properties;
    }

    @Transactional(readOnly=true)
    public AiResponseMetadata find(User user,String requestId) {
        if(user==null||requestId==null||requestId.isBlank())return null;
        var reservation=reservations.findResultByUserAndIdempotencyKey(user,requestId).orElse(null);
        if(reservation==null||!Objects.equals(user.getId(),reservation.getUser().getId())
                ||!java.util.Set.of(AiCreditReservation.Status.RESULT_READY,AiCreditReservation.Status.COMPLETED).contains(reservation.getStatus()))return null;
        if(reservation.getResultMetadataJson()!=null) {
            try{return json.readValue(reservation.getResultMetadataJson(),AiResponseMetadata.class);}
            catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,"STORED_DATA_INVALID");}
        }
        if(user.getRemoteUid()==null||user.getRemoteUid()<=0)return null;
        var candidates=calls.findByRequestIdAndSettlementState(requestId,"SETTLED");
        if(candidates.size()!=1)return null;
        var call=candidates.getFirst();
        if(call.getRequestJson()==null||call.getResultJson()==null||!"COMPLETED".equals(call.getStatus()))return null;
        try {
            var requestJson=json.readTree(call.getRequestJson());var resultJson=json.readTree(call.getResultJson());
            if(requestJson==null||resultJson==null)return null;
            var request=ChatCompletionsRequest.parseFrom(Base64.getDecoder().decode(requestJson.path("requestBase64").asText()));
            var response=ChatCompletionsResponse.parseFrom(Base64.getDecoder().decode(resultJson.path("responseBase64").asText()));
            if(!requestId.equals(call.getRequestId())||!requestId.equals(request.getRequestId())
                    ||!properties.getProjectKey().equals(request.getProjectKey())||request.getUserId()!=user.getRemoteUid()
                    ||!Objects.equals(call.getRequestHash(),AiValidationCallBudget.hash(request))
                    ||!Objects.equals(reservation.getResultContent(),response.getContent())
                    ||reservation.getPromptTokens()!=response.getPromptTokens()||reservation.getCompletionTokens()!=response.getCompletionTokens()
                    ||reservation.getCacheTokens()!=response.getCacheTokens()
                    ||reservation.getActualModelKey()!=null&&!reservation.getActualModelKey().equals(response.getModelKey()))return null;
            return AiResponseMetadata.reported(response.hasFinishReason()?response.getFinishReason():null,
                    response.hasReasoningTokens()?response.getReasoningTokens():null,
                    response.hasUsageAuthoritative()?response.getUsageAuthoritative():null);
        } catch(java.io.IOException|IllegalArgumentException invalid) {
            throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,"STORED_DATA_INVALID");
        }
    }
}
