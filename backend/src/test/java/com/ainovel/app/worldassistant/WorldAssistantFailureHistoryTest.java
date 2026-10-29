package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldAssistantFailureHistoryTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final WorldAssistantStore store=spy(new WorldAssistantStore(null,json));
    private final WorldAssistantResources resources=mock(WorldAssistantResources.class);
    private final WorldAssistantContext context=mock(WorldAssistantContext.class);
    private final AiOperationService operations=mock(AiOperationService.class);
    private final AiService ai=mock(AiService.class);
    private final WorldAssistantService service=new WorldAssistantService(store,resources,context,operations,ai,json);
    private final User user=new User();
    private final UUID world=UUID.randomUUID(),session=UUID.randomUUID(),turn=UUID.randomUUID(),operation=UUID.randomUUID();
    private void prepare(String body,String code,AiOperationStatus status) {
        user.setId(UUID.randomUUID());
        var frozen=new WorldAssistantService.Frozen(world,session,"OVERVIEW","整合概览",null,null,null,null,List.of(),null,"world.overview.integrate");
        var response=new AiChatResponse("assistant",body,new AiUsageDto(100,8192,0,0,13),20);
        var row=new WorldAssistantStore.Turn(turn,session,"hash",operation,store.write(frozen),"{}",body==null?null:store.write(response),null,null,null,null,Instant.now());
        doReturn(row).when(store).turn(turn);
        doReturn(new WorldAssistantStore.Session(session,world,user.getId(),"世界概览整合",Instant.now(),Instant.now())).when(store).session(session,false);
        when(operations.get(user,operation)).thenReturn(new AiOperationDtos.Progress(operation,WorldAssistantService.TYPE,"WORLD_ASSISTANT_SESSION",session,status,"",1,0,1,0,false,1,null,code,null,null,null));
        when(context.read(any(),anyList())).thenReturn(new WorldAssistantContext.Reads(List.of(),List.of()));
    }
    @Test void historicEmptyResultUsesOwnedReceiptWithoutResubmittingOrChangingHistory() {
        prepare("","WORLD_ASSISTANT_RESULT_INVALID",AiOperationStatus.FAILED);
        when(ai.storedResultMetadata(user,operation+":world-assistant:plan")).thenReturn(new AiResponseMetadata(1,AiResponseMetadata.FinishReason.LENGTH,8192L,true));
        var first=service.turn(user,world,turn);var refreshed=service.turn(user,world,turn);
        assertEquals(WorldAssistantResultFailure.REASONING_LIMIT,first.failure().code());
        assertEquals(first.failure(),refreshed.failure());assertEquals(13,first.usage().cost());assertTrue(first.proposalIds().isEmpty());
        verify(ai,never()).chatWithIdempotency(any(),any(),anyString());verify(store,never()).checkpoint(any(),any(),any());
    }
    @Test void oldMissingMetadataRemainsUnknownAndUncertainCallRemainsPending() {
        prepare("","WORLD_ASSISTANT_RESULT_INVALID",AiOperationStatus.FAILED);
        assertEquals(WorldAssistantResultFailure.EMPTY,service.turn(user,world,turn).failure().code());
        prepare(null,"AI_RESULT_RECONCILIATION_REQUIRED",AiOperationStatus.RECOVERY_REQUIRED);
        var pending=service.turn(user,world,turn);
        assertEquals("AI_RESULT_RECONCILIATION_REQUIRED",pending.failure().code());
        assertNull(pending.failure().completionTokens());assertNull(pending.failure().usageAuthoritative());
        verify(ai,never()).chatWithIdempotency(any(),any(),anyString());
    }
    @Test void savedSecondStageUsesFinalReceiptAndRetainsBothStepsUsage() {
        prepare("","WORLD_ASSISTANT_RESULT_INVALID",AiOperationStatus.FAILED);
        var old=store.turn(turn);
        var frozen=new WorldAssistantService.Frozen(world,session,"PROPOSE","整理提案",null,null,null,null,List.of(),null,null);
        var plan=new AiChatResponse("assistant","{\"kind\":\"READ_BATCH\",\"reads\":[]}",new AiUsageDto(100,20,0,0,1),20);
        doReturn(new WorldAssistantStore.Turn(turn,session,old.requestHash(),operation,store.write(frozen),old.planRequestJson(),store.write(plan),"{\"original\":true}",old.planResultJson(),"{}",null,old.createdAt())).when(store).turn(turn);
        when(ai.storedResultMetadata(user,operation+":world-assistant:final")).thenReturn(new AiResponseMetadata(1,AiResponseMetadata.FinishReason.LENGTH,8192L,true));
        var result=service.turn(user,world,turn);
        assertEquals("final",result.failure().phase());assertEquals(WorldAssistantResultFailure.REASONING_LIMIT,result.failure().code());
        assertEquals(2,result.usage().modelCalls());assertEquals(14,result.usage().cost());
        verify(ai,never()).storedResultMetadata(user,operation+":world-assistant:plan");
        verify(ai,never()).chatWithIdempotency(any(),any(),anyString());verify(store,never()).checkpoint(any(),any(),any());
    }
    @Test void replayingSavedEmptyBodyDoesNotGenerateProposalsOrCallModel() {
        prepare(" \n","WORLD_ASSISTANT_RESULT_INVALID",AiOperationStatus.FAILED);
        var execution=new AiOperationExecution(operation,user,"{}",json,(a,b,c)->{});
        var error=assertThrows(com.ainovel.app.common.ApiStatusException.class,()->service.execute(execution,turn));
        assertEquals(WorldAssistantResultFailure.EMPTY,error.getMessage());
        verify(ai,never()).chatWithIdempotency(any(),any(),anyString());verify(store,never()).createProposal(any(),anyInt(),any());verify(store,never()).finish(any(),any());
    }
}
