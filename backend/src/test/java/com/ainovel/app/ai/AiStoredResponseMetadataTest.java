package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.economy.repo.AiCreditReservationRepository;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import fireflychat.ai.v1.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiStoredResponseMetadataTest {
    private final AiCreditReservationRepository reservations=mock(AiCreditReservationRepository.class);
    private final AiValidationCallRepository calls=mock(AiValidationCallRepository.class);
    private final ObjectMapper json=new ObjectMapper();
    private final User user=new User();
    private final AiCreditReservation reservation=new AiCreditReservation();
    private final AiStoredResponseMetadata reader=new AiStoredResponseMetadata(reservations,calls,json,new ExternalServiceProperties());
    AiStoredResponseMetadataTest() {
        user.setId(UUID.randomUUID());user.setRemoteUid(123L);reservation.setUser(user);
        reservation.setStatus(AiCreditReservation.Status.COMPLETED);reservation.setResultContent("");
        reservation.setPromptTokens(100);reservation.setCompletionTokens(8192);reservation.setCacheTokens(20);
        reservation.setActualModelKey("test-model");
        when(reservations.findResultByUserAndIdempotencyKey(user,"original")).thenReturn(Optional.of(reservation));
    }

    @Test void readsNewMetadataWithoutConsultingValidationHistory() throws Exception {
        var metadata=AiResponseMetadata.reported("length",8192L,true);
        reservation.setResultMetadataJson(json.writeValueAsString(metadata));
        assertEquals(metadata,reader.find(user,"original"));verifyNoInteractions(calls);
    }

    @Test void derivesOnlyTheOwnedMatchingSettledReceiptWithoutBackfill() throws Exception {
        when(calls.findByRequestIdAndSettlementState("original","SETTLED")).thenReturn(List.of(receipt(123,"ainovel","original","",8192)));
        var metadata=reader.find(user,"original");
        assertEquals(AiResponseMetadata.reported("length",8192L,true),metadata);
        assertNull(reservation.getResultMetadataJson());
        verify(reservations,never()).save(any());verify(calls,never()).save(any());
    }

    @Test void rejectsUnownedUnknownAmbiguousOrMismatchedEvidence() throws Exception {
        var other=new User();other.setId(UUID.randomUUID());other.setRemoteUid(123L);
        when(reservations.findResultByUserAndIdempotencyKey(other,"original")).thenReturn(Optional.empty());
        assertNull(reader.find(other,"original"));verifyNoInteractions(calls);
        for(var bad:List.of(receipt(999,"ainovel","original","",8192),receipt(123,"another-project","original","",8192),
                receipt(123,"ainovel","another-request","",8192),receipt(123,"ainovel","original","different",8192),
                receipt(123,"ainovel","original","",8191))) {
            when(calls.findByRequestIdAndSettlementState("original","SETTLED")).thenReturn(List.of(bad));
            assertNull(reader.find(user,"original"));
        }
        var matching=receipt(123,"ainovel","original","",8192);
        matching.setRequestHash("wrong");when(calls.findByRequestIdAndSettlementState("original","SETTLED")).thenReturn(List.of(matching));
        assertNull(reader.find(user,"original"));
        when(calls.findByRequestIdAndSettlementState("original","SETTLED")).thenReturn(List.of(matching,matching));
        assertNull(reader.find(user,"original"));
        reservation.setStatus(AiCreditReservation.Status.RECONCILIATION_REQUIRED);assertNull(reader.find(user,"original"));
    }

    @Test void oldResponseJsonAndUnreportedMetadataRemainUnknown() throws Exception {
        var old=json.readValue("{\"role\":\"assistant\",\"content\":\"\",\"usage\":null,\"remainingCredits\":0}",AiChatResponse.class);
        assertNull(old.metadata());assertNull(AiResponseMetadata.reported(null,null,null));
        assertEquals(0L,AiResponseMetadata.reported("stop",0L,false).reasoningTokens());
        assertEquals(false,AiResponseMetadata.reported("stop",0L,false).usageAuthoritative());
        assertEquals(AiResponseMetadata.FinishReason.OTHER,AiResponseMetadata.reported("untrusted provider text",null,null).finishReason());
        assertNull(AiResponseMetadata.reported("stop",-1L,true).reasoningTokens());
        assertFalse(json.writeValueAsString(AiResponseMetadata.reported("untrusted provider text",null,null)).contains("untrusted provider text"));
        var bad=new AiValidationCall();bad.setStatus("COMPLETED");bad.setRequestJson("null");bad.setResultJson("null");
        when(calls.findByRequestIdAndSettlementState("original","SETTLED")).thenReturn(List.of(bad));
        assertNull(reader.find(user,"original"));
    }

    private AiValidationCall receipt(long owner,String project,String requestId,String content,long output) throws Exception {
        var request=ChatCompletionsRequest.newBuilder().setUserId(owner).setProjectKey(project).setRequestId(requestId).setModel("test-model").build();
        var response=ChatCompletionsResponse.newBuilder().setContent(content).setModelKey("test-model").setPromptTokens(100)
                .setCompletionTokens(output).setCacheTokens(20).setFinishReason("length").setReasoningTokens(output).setUsageAuthoritative(true).build();
        var call=new AiValidationCall();call.setRequestId("original");call.setStatus("COMPLETED");call.setSettlementState("SETTLED");call.setRequestHash(AiValidationCallBudget.hash(request));
        call.setRequestJson(json.writeValueAsString(Map.of("requestBase64",Base64.getEncoder().encodeToString(request.toByteArray()))));
        call.setResultJson(json.writeValueAsString(Map.of("responseBase64",Base64.getEncoder().encodeToString(response.toByteArray()))));return call;
    }
}
