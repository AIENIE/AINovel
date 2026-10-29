package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.user.User;
import com.ainovel.app.world.*;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import static com.ainovel.app.world.WorldDraftDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldAssistantTimelineTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final WorldRepository worlds=mock(WorldRepository.class);
    private final WorldEntryStore entries=mock(WorldEntryStore.class);
    private final WorldAssistantResources resources=new WorldAssistantResources(worlds,entries,json);
    private final WorldAssistantStore store=spy(new WorldAssistantStore(null,json));
    private final WorldAssistantContext context=new WorldAssistantContext(resources,store,mock(WorldPromptRenderer.class));
    private final AiService ai=mock(AiService.class);
    private final WorldAssistantService service=new WorldAssistantService(store,resources,context,mock(AiOperationService.class),ai,json);
    private final UUID worldId=UUID.randomUUID(),entryId=UUID.randomUUID(),eraId=UUID.randomUUID(),eventId=UUID.randomUUID();
    private Content content(boolean linked) {
        return new Content("群岛","",List.of(),"","",linked?List.of(new Entry(entryId,"LOCATION","潮港","","已保存条目",List.of(),List.of(),null,null)):List.of(),
            List.of(),Map.of(),null,"",new Timeline(1,"潮历",linked?List.of(new Era(eraId,"旧潮时代","时代依据",null,0)):List.of(),List.of()));
    }
    private WorldAssistantService.ProposalData eventProposal() {
        var data=new WorldAssistantService.ProposalData();data.resourceType="EVENT";data.action="CREATE";data.targetId=eventId;
        data.candidate=resources.normalize("EVENT",null,"CREATE",json.valueToTree(Map.of("title","风暴","entryIds",List.of(entryId),"eraIds",List.of(eraId))),null);return data;
    }
    @Test void pendingEventDependenciesAreReviewedAndSingleTargetMergeKeepsCurrentTimeline() {
        var world=new World();world.setId(worldId);var data=eventProposal();when(entries.content(world)).thenReturn(content(false));
        assertEquals("WORLD_PROPOSAL_DEPENDENCY_MISSING",resources.state(world,data).reason());
        var unrelated=new Event(UUID.randomUUID(),"同时新增的事件","","不能丢失",List.of(),List.of(),null,List.of(),List.of(),2);
        var base=content(true);var current=new Content(base.name(),base.tagline(),base.themes(),base.creativeIntent(),base.notes(),base.entries(),base.relations(),base.modules(),null,"",
            new Timeline(1,"潮历",base.timeline().eras(),List.of(unrelated)));
        when(entries.content(world)).thenReturn(current);assertNull(resources.state(world,data).reason());
        when(entries.write(any())).thenAnswer(i->json.writeValueAsString(i.getArgument(0)));
        doAnswer(i->{Consumer<World> mutation=i.getArgument(3);mutation.accept(world);return null;}).when(entries).save(eq(world),anyList(),anyList(),any(Consumer.class));
        resources.apply(world,data);var saved=WorldTimeline.read(world.getTimelineJson(),json);
        assertEquals("潮历",saved.calendarName());assertEquals(List.of(eraId),saved.eras().stream().map(Era::id).toList());
        assertEquals(Set.of(unrelated.id(),eventId),new HashSet<>(saved.events().stream().map(Event::id).toList()));
    }
    @Test void timelineProposalDatesAreStrictAndMissingDatesStayUnknown() {
        assertTrue(eventProposal().candidate.path("date").isNull());
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->resources.normalize("ERA",null,"CREATE",
            json.valueToTree(Map.of("name","纪元","date",Map.of("start",Map.of("month",2)))),null));
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->resources.normalize("ERA",null,"CREATE",
            json.valueToTree(Map.of("name","纪元","date",Map.of("start",Map.of("year","20")))),null));
    }
    private WorldAssistantService.Frozen frozen(Content content,String protocol) {
        var available=resources.resources(worldId,content);var pool=available.stream().map(r->WorldAssistantContext.source(WorldAssistantResources.sourceId(r),r.type(),r.title(),store.write(r.fields()))).toList();
        return new WorldAssistantService.Frozen(worldId,UUID.randomUUID(),"PROPOSE","整理历史",null,null,null,
            new WorldAssistantContext.Snapshot(worldId,1,"hash",content,available,pool),List.of(),null,null,protocol);
    }
    @Test void discussionsReadAllSavedTimelineAndOversizeStopsBeforeAi() {
        var base=content(true);var timeline=new Timeline(1,"潮历",base.timeline().eras(),List.of(new Event(eventId,"无关事件","","完整保存材料",List.of(),List.of(),null,List.of(entryId),List.of(eraId),0)));
        var value=new Content(base.name(),"",List.of(),"","",base.entries(),List.of(),Map.of(),null,"",timeline);
        var reads=context.read(frozen(value,WorldAssistantService.PROTOCOL),List.of());
        assertTrue(reads.sources().stream().anyMatch(s->s.type().equals("ERA")));assertTrue(reads.sources().stream().anyMatch(s->s.type().equals("EVENT")));
        var large=new Content(base.name(),"",List.of(),"","",base.entries(),List.of(),Map.of(),null,"",new Timeline(1,"",base.timeline().eras(),List.of(new Event(eventId,"超限事件","","完整材料".repeat(12000),List.of(),List.of(),null,List.of(),List.of(),0))));
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->context.read(frozen(large,WorldAssistantService.PROTOCOL),List.of()));verifyNoInteractions(ai);
    }
    private UUID preparedTurn(WorldAssistantService.Frozen frozen,Object response,String finalRequest,String reads) {
        UUID turn=UUID.randomUUID(),operation=UUID.randomUUID();var user=new User();user.setId(UUID.randomUUID());
        var row=new WorldAssistantStore.Turn(turn,frozen.sessionId(),"request",operation,store.write(frozen),null,
            store.write(new AiChatResponse("assistant",store.write(response),null,99)),finalRequest,null,reads,null,Instant.now());
        doReturn(row).when(store).turn(turn);doReturn(new WorldAssistantStore.Session(frozen.sessionId(),worldId,user.getId(),"历史",Instant.now(),Instant.now())).when(store).session(frozen.sessionId(),false);
        doNothing().when(store).finish(any(),any());doNothing().when(store).touch(any());doReturn(UUID.randomUUID()).when(store).createProposal(any(),anyInt(),any());
        executions.put(turn,new AiOperationExecution(operation,user,"{}",json,(a,b,c)->{}));return turn;
    }
    private final Map<UUID,AiOperationExecution> executions=new HashMap<>();
    @Test void batchIdsArePreallocatedAcrossEntryEraAndEventRegardlessOfOrder() {
        var event=Map.of("action","CREATE","resourceType","EVENT","clientKey","storm","fields",Map.of("title","风暴","entryIds",List.of("port"),"eraIds",List.of("age")),"rationale","整理");
        var era=Map.of("action","CREATE","resourceType","ERA","clientKey","age","fields",Map.of("name","潮封时代"),"rationale","整理");
        var entry=Map.of("action","CREATE","resourceType","ENTRY","clientKey","port","fields",Map.of("type","LOCATION","title","港口"),"rationale","整理");
        var turn=preparedTurn(frozen(content(false),WorldAssistantService.PROTOCOL),Map.of("kind","PROPOSALS","answer","请审阅","items",List.of(event,era,entry)),null,null);
        service.execute(executions.get(turn),turn);var captured=ArgumentCaptor.forClass(WorldAssistantService.ProposalData.class);
        verify(store,times(3)).createProposal(eq(turn),anyInt(),captured.capture());var values=captured.getAllValues();
        assertEquals(values.get(1).targetId.toString(),values.get(0).candidate.path("eraIds").get(0).asText());
        assertEquals(values.get(2).targetId.toString(),values.get(0).candidate.path("entryIds").get(0).asText());verifyNoInteractions(ai);
    }
    @Test void legacyReadBatchWithoutFrozenSecondRequestStopsWithoutNewCall() {
        var frozen=frozen(content(false),null);var turn=preparedTurn(frozen,Map.of("kind","READ_BATCH","reads",List.of()),null,null);
        var error=assertThrows(com.ainovel.app.common.ApiStatusException.class,()->service.execute(executions.get(turn),turn));
        assertEquals("WORLD_ASSISTANT_LEGACY_INPUT_UNAVAILABLE",error.getMessage());verifyNoInteractions(ai);verify(store,never()).checkpoint(any(),any(),any());
    }
    @Test void legacyFinalResultWithoutSavedReadsRemainsStoredAndDoesNotReconstructSources() {
        var frozen=frozen(content(false),null);var turn=preparedTurn(frozen,Map.of("kind","READ_BATCH","reads",List.of()),"original-request",null);
        var old=store.turn(turn);String result=store.write(new AiChatResponse("assistant","{\"kind\":\"ANSWER\",\"answer\":\"历史已生成回答\"}",null,99));
        var row=new WorldAssistantStore.Turn(old.id(),old.sessionId(),old.requestHash(),old.operationId(),old.frozenJson(),old.planRequestJson(),old.planResultJson(),old.finalRequestJson(),result,null,null,old.createdAt());
        doReturn(row).when(store).turn(turn);
        var error=assertThrows(com.ainovel.app.common.ApiStatusException.class,()->service.execute(executions.get(turn),turn));
        assertEquals("WORLD_ASSISTANT_LEGACY_INPUT_UNAVAILABLE",error.getMessage());assertEquals(result,store.turn(turn).finalResultJson());
        verifyNoInteractions(ai);verify(store,never()).checkpoint(any(),any(),any());verify(store,never()).finish(any(),any());
    }
    @Test void legacySavedSecondRequestResumesExactProtocolAndIdentity() {
        var frozen=frozen(content(false),null);var original=new AiChatRequest(List.of(new AiChatRequest.Message("system","冻结 V1 原提示")),"old-model",null,"world_assistant.propose.final");
        var reads=context.read(frozen,List.of());var turn=preparedTurn(frozen,Map.of("kind","READ_BATCH","reads",List.of()),store.write(original),store.write(reads));
        // JsonNode JSON null becomes NullNode when read back; compare the exact stored request representation.
        var restored=store.read(store.turn(turn).finalRequestJson(),AiChatRequest.class);
        assertEquals(store.write(original),store.write(restored));
        when(ai.chatWithIdempotency(any(),eq(restored),anyString())).thenReturn(new AiChatResponse("assistant","{\"kind\":\"ANSWER\",\"answer\":\"按原输入回答\"}",null,99));
        doNothing().when(store).checkpoint(any(),anyString(),any());service.execute(executions.get(turn),turn);
        verify(ai).chatWithIdempotency(eq(executions.get(turn).user()),eq(restored),eq(executions.get(turn).operationId()+":world-assistant:final"));
        verify(store,never()).checkpoint(any(),eq("final_request_json"),any());
    }
}
