package com.ainovel.app.world;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.auth.SsoTokenExchangeService;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.story.model.*;
import com.ainovel.app.story.repo.*;
import com.ainovel.app.user.*;
import com.ainovel.app.world.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username="world_versions")
class WorldVersionTests {
    @MockitoBean SsoTokenExchangeService sso;
    @MockitoBean AiService ai;
    @Autowired WorldService worlds;
    @Autowired WorldVersionService versions;
    @Autowired WorldBindingService bindings;
    @Autowired UserRepository users;
    @Autowired StoryRepository stories;
    @Autowired OutlineRepository outlines;
    private User user() {
        return users.findByUsername("world_versions").orElseGet(()->{
            var u=new User();u.setUsername("world_versions");u.setEmail("world_versions@example.invalid");u.setPasswordHash("x");return users.save(u);
        });
    }
    private UUID world() {return worlds.create(user(),new WorldCreateRequest("退潮群岛","",List.of(),"","")).id();}
    private Entry entry(UUID id,String body){return new Entry(id,"RULE","潮汐规则","",body,List.of("潮退"),List.of("自然"),null,null);}
    private Draft save(UUID id,Draft d,List<Entry> entries,List<Relation> relations){
        return versions.save(id,new Save(null,entries,relations,d.world().revision()));
    }
    private Version publish(UUID id){var d=versions.draft(id);return versions.publish(id,new Publish(d.world().revision(),"",UUID.randomUUID().toString()));}
    private Story story(String title){var s=new Story();s.setUser(user());s.setTitle(title);return stories.save(s);}
    @Test void immutableSnapshotsDiffAndPublicationAreIndependentOfAi() {
        UUID id=world(),entryId=UUID.randomUUID(),secondId=UUID.randomUUID(),relationId=UUID.randomUUID();
        var draft=save(id,versions.draft(id),List.of(entry(entryId,"每十七年退潮"),entry(secondId,"盐晶保存声音")),
            List.of(new Relation(relationId,entryId,secondId,"影响","潮退露出盐矿")));
        var request=new Publish(draft.world().revision(),"第一版",UUID.randomUUID().toString());
        var first=versions.publish(id,request);assertEquals(first,versions.publish(id,request));
        var noChange=new Publish(versions.draft(id).world().revision(),"无内容变化",UUID.randomUUID().toString());
        assertEquals(first.id(),versions.publish(id,noChange).id());assertEquals(1,versions.list(id).size());
        var changed=save(id,versions.draft(id),List.of(entry(entryId,"每十九年退潮")),List.of());
        assertTrue(changed.unpublishedChanges());
        assertEquals("每十七年退潮",versions.get(id,first.id()).content().entries().stream().filter(e->e.id().equals(entryId)).findFirst().orElseThrow().content());
        var diff=versions.diff(id,first.id(),null,changed.world().revision());
        assertEquals(3,diff.changes().size());
        var second=publish(id);assertEquals(2,second.sequenceNumber());
        assertEquals(first.id(),versions.publish(id,noChange).id());
        assertThrows(ApiStatusException.class,()->versions.publish(id,new Publish(noChange.expectedRevision(),"换了请求",noChange.requestKey())));
        assertFalse(versions.draft(id).unpublishedChanges());
        assertThrows(ApiStatusException.class,()->save(id,draft,List.of(),List.of()));
        assertThrows(RuntimeException.class,()->worlds.delete(id));
        verifyNoInteractions(ai);
    }
    @Test void twoStoriesAndLegacyOverrideUpgradeIndependently() {
        UUID id=world();var first=publish(id);var a=story("甲");var b=story("乙");
        bindings.change("STORY",a.getId(),new WorldBindingService.Change(null,first.id(),0L));
        bindings.change("STORY",b.getId(),new WorldBindingService.Change(null,first.id(),0L));
        var o=new Outline();o.setStory(a);o.setTitle("旧覆盖");o.setWorldId(id.toString());o.setWorldVersionId(first.id());o=outlines.save(o);
        save(id,versions.draft(id),List.of(entry(UUID.randomUUID(),"新设定")),List.of());var second=publish(id);
        assertEquals(first.id(),bindings.view("STORY",a.getId()).effective().versionId());
        var preview=bindings.preview("STORY",a.getId(),second.id());assertFalse(preview.changes().isEmpty());
        bindings.change("STORY",a.getId(),new WorldBindingService.Change(first.id(),second.id(),1L));
        assertEquals(first.id(),bindings.view("STORY",b.getId()).effective().versionId());
        assertEquals(first.id(),bindings.view("OUTLINE",o.getId()).effective().versionId());
        assertThrows(ApiStatusException.class,()->bindings.change("STORY",a.getId(),new WorldBindingService.Change(first.id(),second.id(),1L)));
        bindings.change("OUTLINE",o.getId(),new WorldBindingService.Change(first.id(),null,0L));
        assertEquals(second.id(),bindings.view("OUTLINE",o.getId()).effective().versionId());
        UUID outlineId=o.getId();
        assertThrows(ApiStatusException.class,()->bindings.change("OUTLINE",outlineId,new WorldBindingService.Change(null,first.id(),1L)));
    }
    @Test void unknownLegacyFieldsAndEntryRenameSurviveBothInterfaces() {
        UUID id=world();
        worlds.updateModules(id,new WorldModulesUpdateRequest(Map.of("geography",Map.of("terrain","群岛","unknown","未知字段原文"))));
        var draft=versions.draft(id);assertEquals(2,draft.entries().size());
        var unknown=draft.entries().stream().filter(e->"unknown".equals(e.legacyFieldKey())).findFirst().orElseThrow();
        assertEquals("CUSTOM",unknown.type());
        var next=draft.entries().stream().map(e->e.id().equals(unknown.id())?
            new Entry(e.id(),e.type(),"自定义设定",e.summary(),"新内容",e.aliases(),e.tags(),e.legacyModuleKey(),e.legacyFieldKey()):e).toList();
        save(id,draft,next,List.of());
        assertEquals("新内容",worlds.get(id).modules().get("geography").get("unknown"));
        worlds.update(id,new WorldUpdateRequest("新名称",null,null,null,null));
        assertEquals(unknown.id(),versions.draft(id).entries().stream().filter(e->"自定义设定".equals(e.title())).findFirst().orElseThrow().id());
    }
    @Test void concurrentSavesPublicationsAndUpgradesHaveOneWinner() throws Exception {
        UUID id=world();var initial=versions.draft(id);
        var saves=parallel(()->save(id,initial,List.of(entry(UUID.randomUUID(),"甲规则")),List.of()),
            ()->save(id,initial,List.of(entry(UUID.randomUUID(),"乙规则")),List.of()));
        assertEquals(1,saves.stream().filter(Draft.class::isInstance).count());
        assertEquals(1,saves.stream().filter(ApiStatusException.class::isInstance).count());
        var request=new Publish(versions.draft(id).world().revision(),"并发发布",UUID.randomUUID().toString());
        var publications=parallel(()->versions.publish(id,request),()->versions.publish(id,request));
        assertEquals(publications.get(0),publications.get(1));assertEquals(1,versions.list(id).size());
        var first=(Version)publications.getFirst();var s=story("并发升级");
        bindings.change("STORY",s.getId(),new WorldBindingService.Change(null,first.id(),0L));
        save(id,versions.draft(id),List.of(entry(UUID.randomUUID(),"新版规则")),List.of());var next=publish(id);
        var changes=parallel(()->bindings.change("STORY",s.getId(),new WorldBindingService.Change(first.id(),next.id(),1L)),
            ()->bindings.change("STORY",s.getId(),new WorldBindingService.Change(first.id(),null,1L)));
        assertEquals(1,changes.stream().filter(WorldBindingService.View.class::isInstance).count());
        assertEquals(1,changes.stream().filter(ApiStatusException.class::isInstance).count());
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
