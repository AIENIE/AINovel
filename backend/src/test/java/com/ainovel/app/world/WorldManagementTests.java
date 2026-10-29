package com.ainovel.app.world;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.auth.SsoTokenExchangeService;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.*;
import com.ainovel.app.world.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username="world_management")
class WorldManagementTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.ainovel.app.security.remote.UserSessionValidator userSessionValidator;

    @MockitoBean SsoTokenExchangeService sso;
    @MockitoBean AiService ai;
    @Autowired WorldService worlds;
    @Autowired WorldVersionService versions;
    @Autowired WorldBindingService bindings;
    @Autowired WorldManagementService management;
    @Autowired StoryWorldEraService eras;
    @Autowired UserRepository users;
    @Autowired StoryRepository stories;
    @Autowired com.ainovel.app.world.repo.WorldRepository worldRows;
    @Autowired ObjectMapper json;

    private User user(){return users.findByUsername("world_management").orElseGet(()->{var u=new User();u.setUsername("world_management");u.setEmail("world_management@example.invalid");u.setPasswordHash("x");return users.saveAndFlush(u);});}
    private UUID world(){return worlds.create(user(),new WorldCreateRequest("群岛","简介",List.of("迁徙"),"意图","作者备注")).id();}
    private Story story(){var s=new Story();s.setUser(user());s.setTitle("多时代作品");return stories.saveAndFlush(s);}
    private Version publish(UUID id){return versions.publish(id,new Publish(versions.draft(id).world().revision(),"作者发布",UUID.randomUUID().toString()));}
    private Draft fixture(UUID id) {
        var d=versions.draft(id);UUID a=UUID.randomUUID(),b=UUID.randomUUID(),era=UUID.randomUUID();
        var entries=List.of(new Entry(a,"LOCATION","旧灯塔","摘要","完整中文正文",List.of("灯台"),List.of("北岸"),null,null),new Entry(b,"PERSON","林","","迁徙者",List.of(),List.of(),null,null));
        var timeline=new Timeline(1,"退潮纪年",List.of(new Era(era,"迁徙时代","时代说明",new DateRange(new DatePoint(-5,null,null),new DatePoint(0,13,null)),1)),
            List.of(new Event(UUID.randomUUID(),"迁徙","摘要","未知日期的完整事件",List.of("北迁"),List.of("迁徙"),null,List.of(a,b),List.of(era),2)));
        return versions.save(id,new Save(null,entries,List.of(new Relation(UUID.randomUUID(),a,b,"守望","关系说明")),d.world().revision(),timeline));
    }
    @Test void copyRemapsAllIdsPreservesRawLegacyAndRetriesAfterSourceChanges() {
        UUID id=world();worlds.updateModules(id,new WorldModulesUpdateRequest(Map.of("unrecognized",Map.of("keep","原始兼容数据"))));
        fixture(id);var original=versions.draft(id);var published=publish(id);
        var request=new WorldManagementService.Copy("VERSION",null,published.id(),null,UUID.randomUUID().toString());
        var copied=management.copy(user(),id,request);var copy=versions.draft(copied.id());
        assertEquals("群岛（副本）",copy.world().name());assertTrue(versions.list(copied.id()).isEmpty());
        var sourceIds=original.entries().stream().map(Entry::id).toList();assertTrue(copy.entries().stream().noneMatch(e->sourceIds.contains(e.id())));
        assertNotEquals(original.relations().getFirst().id(),copy.relations().getFirst().id());
        assertNotEquals(original.timeline().eras().getFirst().id(),copy.timeline().eras().getFirst().id());
        assertNotEquals(original.timeline().events().getFirst().id(),copy.timeline().events().getFirst().id());
        assertTrue(copy.entries().stream().map(Entry::id).toList().containsAll(copy.timeline().events().getFirst().entryIds()));
        assertEquals(List.of(copy.timeline().eras().getFirst().id()),copy.timeline().events().getFirst().eraIds());
        assertTrue(copy.entries().stream().anyMatch(e->e.id().equals(copy.relations().getFirst().sourceId())));
        worlds.update(id,new WorldUpdateRequest("改变后的草稿",null,null,null,null,null,null));
        assertEquals(copied.id(),management.copy(user(),id,request).id());
        assertThrows(ApiStatusException.class,()->management.copy(user(),id,new WorldManagementService.Copy("VERSION",null,published.id(),"不同副本",request.requestKey())));
        var copyVersion=publish(copied.id());assertEquals(1,copyVersion.sequenceNumber());
        assertEquals(published.content().legacyModulesJson(),copyVersion.content().legacyModulesJson());verifyNoInteractions(ai);
    }
    @Test void deletedCopyReceiptDoesNotRecreateAndStaleDraftCannotBeCopiedOrExported() {
        UUID id=world();var draft=versions.draft(id);
        var request=new WorldManagementService.Copy("DRAFT",draft.world().revision(),null,null,UUID.randomUUID().toString());
        var copy=management.copy(user(),id,request);worlds.delete(copy.id());
        assertThrows(ApiStatusException.class,()->management.copy(user(),id,request));
        fixture(id);
        assertThrows(ApiStatusException.class,()->management.copy(user(),id,new WorldManagementService.Copy("DRAFT",draft.world().revision(),null,null,UUID.randomUUID().toString())));
        assertThrows(ApiStatusException.class,()->management.export(id,"DRAFT",draft.world().revision(),null,"JSON"));
    }
    @Test void exportsUseFullChosenSnapshotNotNewerDraftOrVisibleFilter() throws Exception {
        UUID id=world();fixture(id);var first=publish(id);
        worlds.update(id,new WorldUpdateRequest("后来名称",null,null,null,null,null,null));
        var jsonFile=management.export(id,"VERSION",null,first.id(),"JSON");
        var data=json.readTree(jsonFile.bytes());assertEquals(first.contentHash(),data.path("source").path("contentHash").asText());
        assertEquals("群岛",data.path("content").path("name").asText());assertEquals(first.id().toString(),data.path("source").path("versionId").asText());
        assertEquals(2,data.path("content").path("entries").size());assertEquals(1,data.path("content").path("timeline").path("events").size());
        String markdown=new String(management.export(id,"VERSION",null,first.id(),"MARKDOWN").bytes(),StandardCharsets.UTF_8);
        for(String expected:List.of("完整中文正文","未知日期的完整事件","迁徙时代","退潮纪年","作者备注","关系说明",first.contentHash()))assertTrue(markdown.contains(expected),expected);
        assertFalse(markdown.contains("后来名称"));assertTrue(jsonFile.filename().endsWith("-v1.json"));verifyNoInteractions(ai);
    }
    @Test void eraLabelsStayIndependentAndRetainChangedMissingSelectionsAcrossUpgradeAndWorldSwitch() {
        UUID id=world();var draft=fixture(id);var first=publish(id);var story=story();
        bindings.change("STORY",story.getId(),new WorldBindingService.Change(null,first.id(),0L));
        var eraId=draft.timeline().eras().getFirst().id();long worldRevision=versions.draft(id).world().revision();
        var saved=eras.save(story.getId(),new StoryWorldEraService.Save(id,first.id(),1L,0L,List.of(eraId)));
        assertEquals(1,saved.revision());assertEquals(1,bindings.view("STORY",story.getId()).bindingRevision());assertEquals(worldRevision,versions.draft(id).world().revision());
        assertThrows(ApiStatusException.class,()->eras.save(story.getId(),new StoryWorldEraService.Save(id,first.id(),1L,0L,List.of())));
        draft=versions.draft(id);var renamed=new Era(eraId,"迁徙时代更名","新说明",draft.timeline().eras().getFirst().date(),1);
        versions.save(id,new Save(null,draft.entries(),draft.relations(),draft.world().revision(),new Timeline(1,"退潮纪年",List.of(renamed),draft.timeline().events())));
        var second=publish(id);bindings.change("STORY",story.getId(),new WorldBindingService.Change(first.id(),second.id(),1L));
        assertEquals(List.of(eraId),eras.view(story.getId()).changedEraIds());assertEquals(List.of(eraId),eras.view(story.getId()).eraIds());
        draft=versions.draft(id);versions.save(id,new Save(null,draft.entries(),draft.relations(),draft.world().revision(),new Timeline(1,"退潮纪年",List.of(),draft.timeline().events())));
        var third=publish(id);bindings.change("STORY",story.getId(),new WorldBindingService.Change(second.id(),third.id(),2L));
        assertEquals(List.of(eraId),eras.view(story.getId()).missingEraIds());assertEquals("迁徙时代",eras.view(story.getId()).reviewedEras().getFirst().name());
        var other=world();var otherVersion=publish(other);bindings.change("STORY",story.getId(),new WorldBindingService.Change(third.id(),otherVersion.id(),3L));
        assertTrue(eras.view(story.getId()).eraIds().isEmpty());
        bindings.change("STORY",story.getId(),new WorldBindingService.Change(otherVersion.id(),first.id(),4L));
        assertEquals(List.of(eraId),eras.view(story.getId()).eraIds());assertTrue(eras.view(story.getId()).missingEraIds().isEmpty());verifyNoInteractions(ai);
    }

    @Test void concurrentCopyRetriesCreateExactlyOneDraft() throws Exception {
        var owner=user();UUID id=world();fixture(id);var draft=versions.draft(id);
        long worldCount=worldRows.countByUser(owner);
        var request=new WorldManagementService.Copy("DRAFT",draft.world().revision(),null,"并发副本",UUID.randomUUID().toString());
        var outcomes=parallel(()->management.copy(owner,id,request),()->management.copy(owner,id,request));
        assertEquals(2,outcomes.stream().filter(WorldDetailDto.class::isInstance).count());
        var first=(WorldDetailDto)outcomes.getFirst();var second=(WorldDetailDto)outcomes.getLast();
        assertEquals(first.id(),second.id());assertEquals(worldCount+1,worldRows.countByUser(owner));
        assertEquals("并发副本",versions.draft(first.id()).world().name());assertTrue(versions.list(first.id()).isEmpty());
        assertEquals(1,versions.draft(first.id()).timeline().events().size());
        assertEquals(draft.world().revision(),versions.draft(id).world().revision());verifyNoInteractions(ai);
    }

    @Test void concurrentEraSavesHaveOneWinnerWithoutChangingStoryBindingOrWorld() throws Exception {
        UUID id=world();var draft=fixture(id);var published=publish(id);var story=story();
        bindings.change("STORY",story.getId(),new WorldBindingService.Change(null,published.id(),0L));
        var eraId=draft.timeline().eras().getFirst().id();long worldRevision=versions.draft(id).world().revision();
        var storyUpdatedAt=stories.findById(story.getId()).orElseThrow().getUpdatedAt();
        for(long revision:List.of(0L,1L)) {
            var selected=new StoryWorldEraService.Save(id,published.id(),1L,revision,List.of(eraId));
            var cleared=new StoryWorldEraService.Save(id,published.id(),1L,revision,List.of());
            var outcomes=parallel(()->eras.save(story.getId(),selected),()->eras.save(story.getId(),cleared));
            assertEquals(1,outcomes.stream().filter(StoryWorldEraService.View.class::isInstance).count());
            assertEquals(1,outcomes.stream().filter(ApiStatusException.class::isInstance).count());
            assertEquals("WORLD_ERAS_CONFLICT",outcomes.stream().filter(ApiStatusException.class::isInstance).map(ApiStatusException.class::cast).findFirst().orElseThrow().getMessage());
            var winner=(StoryWorldEraService.View)outcomes.stream().filter(StoryWorldEraService.View.class::isInstance).findFirst().orElseThrow();
            assertEquals(revision+1,winner.revision());assertEquals(winner,eras.view(story.getId()));
            assertEquals(1,bindings.view("STORY",story.getId()).bindingRevision());
            assertEquals(worldRevision,versions.draft(id).world().revision());
            assertEquals(storyUpdatedAt,stories.findById(story.getId()).orElseThrow().getUpdatedAt());
        }
        verifyNoInteractions(ai);
    }

    private List<Object> parallel(java.util.concurrent.Callable<?> left,java.util.concurrent.Callable<?> right) throws Exception {
        var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<java.util.concurrent.Future<Object>>();
            for(var action:List.of(left,right))futures.add(pool.submit(()->{
                var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);ready.countDown();
                try{if(!start.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Start timeout");return action.call();}
                catch(ApiStatusException expected){return expected;}
                finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            }));
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            var result=new ArrayList<Object>();for(var future:futures)result.add(future.get(30,java.util.concurrent.TimeUnit.SECONDS));return result;
        }
    }
}
