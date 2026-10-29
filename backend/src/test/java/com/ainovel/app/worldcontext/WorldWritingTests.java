package com.ainovel.app.worldcontext;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.auth.SsoTokenExchangeService;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.story.*;
import com.ainovel.app.story.model.*;
import com.ainovel.app.story.repo.*;
import com.ainovel.app.user.*;
import com.ainovel.app.world.*;
import com.ainovel.app.world.dto.WorldCreateRequest;
import com.ainovel.app.modelconfig.AiSelectionContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.*;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test") @WithMockUser(username="world_writing")
class WorldWritingTests {
    @MockitoBean AiService ai;
    @MockitoBean SsoTokenExchangeService sso;
    @Autowired UserRepository users; @Autowired StoryRepository stories; @Autowired OutlineRepository outlines;
    @Autowired WorldService worlds; @Autowired WorldVersionService versions; @Autowired WorldBindingService bindings;
    @Autowired WorldContextPreferences preferences; @Autowired WorldContextSelector selector; @Autowired OutlineAiService outlineAi;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    User user; UUID world,version,entry; Outline outline;
    @BeforeEach void fixture() {
        user=users.findByUsername("world_writing").orElseGet(()->{var u=new User();u.setUsername("world_writing");u.setEmail("world-writing@example.invalid");u.setPasswordHash("x");return users.saveAndFlush(u);});
        world=worlds.create(user,new WorldCreateRequest("退潮世界","",List.of(),"","")).id();entry=UUID.randomUUID();
        var draft=versions.draft(world);
        versions.save(world,new WorldDraftDtos.Save(null,List.of(new WorldDraftDtos.Entry(entry,"RULE","潮汐登记","","北堤通行必须登记",List.of("通行令"),List.of(),null,null)),List.of(),draft.world().revision()));
        version=versions.publish(world,new WorldDraftDtos.Publish(versions.draft(world).world().revision(),"",UUID.randomUUID().toString())).id();
        var story=new Story();story.setUser(user);story.setTitle("北堤故事");story=stories.saveAndFlush(story);
        bindings.change("STORY",story.getId(),new WorldBindingService.Change(null,version,0L));
        outline=new Outline();outline.setStory(stories.findById(story.getId()).orElseThrow());outline.setTitle("规划");outline.setContentJson("{\"planning\":{\"goal\":\"持通行令进入北堤\"},\"chapters\":[]}");outline=outlines.saveAndFlush(outline);
    }
    private OutlineAiService.Request request(){return new OutlineAiService.Request("CHAPTER_GENERATE",null,null,null,1,5,null,null,null);}
    private AiChatResponse response(String text){return new AiChatResponse("assistant",text,null,0);}
    private String chapter() throws Exception {var scenes=new ArrayList<Map<String,String>>();for(int i=0;i<5;i++)scenes.add(Map.of("title","场景"+i,"summary","调查北堤通行令"));return json.writeValueAsString(Map.of("title","北堤来客","summary","登记处的异样","scenes",scenes));}
    @Test void preferencesPersistCheckRevisionAndDoNotCrossWorlds() {
        var saved=preferences.save(user,outline.getId(),null,null,new Save(world,version,0L,Map.of("entry:"+entry,Choice.EXCLUDE)));
        assertEquals(saved,preferences.get(user,outline.getId(),null,null));
        assertThrows(ApiStatusException.class,()->preferences.save(user,outline.getId(),null,null,new Save(world,version,0L,Map.of())));
        var f=outlineAi.prepare(user,outline.getId(),request());assertTrue(f.worldContext().excluded().stream().anyMatch(s->s.id().equals("entry:"+entry)&&s.reason().equals("AUTHOR_EXCLUDED")));
        assertFalse(f.prompt().messages().toString().contains("通行必须登记"));
        assertThrows(ApiStatusException.class,()->outlineAi.prepare(user,outline.getId(),new OutlineAiService.Request("CHAPTER_GENERATE",null,null,null,1,5,"old",null,null)));
        verifyNoInteractions(ai);
    }
    @Test void lateChapterIsDurableThenAppendIsIdempotent() throws Exception {
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenAnswer(call->{
            preferences.save(user,outline.getId(),null,null,new Save(world,version,0L,Map.of("entry:"+entry,Choice.EXCLUDE)));
            return response(chapter());
        });
        var result=outlineAi.compatible(user,outline.getId(),request());
        assertEquals("READY",result.status());assertEquals(0,json.readTree(outlines.findById(outline.getId()).orElseThrow().getContentJson()).path("chapters").size());
        assertEquals(result,outlineAi.get(user,outline.getId(),result.id()));assertEquals(1,outlineAi.list(user,outline.getId()).size());
        assertTrue(result.worldContext().included().stream().anyMatch(s->s.id().equals("entry:"+entry)));
        var current=outlines.findById(outline.getId()).orElseThrow();var preview=outlineAi.prepare(user,outline.getId(),request());
        var decision=new OutlineAiService.Decision(result.revision(),current.getRevision(),outlineAi.fingerprint(preview),"accept-once");
        var accepted=outlineAi.accept(user,outline.getId(),result.id(),decision);assertEquals("APPLIED",accepted.status());
        assertEquals(accepted,outlineAi.accept(user,outline.getId(),result.id(),decision));
        assertEquals(1,json.readTree(outlines.findById(outline.getId()).orElseThrow().getContentJson()).path("chapters").size());
        verify(ai,times(1)).chatWithIdempotency(eq(user),any(),anyString());
    }
    @Test void malformedResultNeverCreatesPlaceholderChapter() throws Exception {
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response("{\"title\":\"只有标题\"}"));
        assertThrows(ApiStatusException.class,()->outlineAi.compatible(user,outline.getId(),request()));
        var result=outlineAi.list(user,outline.getId()).getFirst();assertEquals("INVALID",result.status());assertNotNull(result.rawResult());
        assertEquals(0,json.readTree(outlines.findById(outline.getId()).orElseThrow().getContentJson()).path("chapters").size());
    }
    @Test void quotationIdentityReplaysTheOriginalResultWithoutAnotherCall() throws Exception {
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(chapter()));
        try(var ignored=AiSelectionContext.install(new AiSelectionContext.Snapshot("w4-"+outline.getId(),Map.of(),"",Map.of(),null,null))) {
            var first=outlineAi.compatible(user,outline.getId(),request());assertEquals("APPLIED",first.status());
            assertEquals(first.id(),outlineAi.compatible(user,outline.getId(),request()).id());
        }
        verify(ai,times(1)).chatWithIdempotency(eq(user),any(),anyString());
    }
    @Test void twoConcurrentPreferenceSavesHaveOneWinner() throws Exception {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext();var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<String> save=()->{org.springframework.security.core.context.SecurityContextHolder.setContext(auth);try{start.await();preferences.save(user,outline.getId(),null,null,new Save(world,version,0L,Map.of("entry:"+entry,Choice.INCLUDE)));return "saved";}
                catch(ApiStatusException conflict){return conflict.getMessage();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
            var one=executor.submit(save);var two=executor.submit(save);start.countDown();
            assertEquals(Set.of("saved","WORLD_CONTEXT_SELECTION_CHANGED"),Set.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS)));
        }
        assertEquals(1,preferences.get(user,outline.getId(),null,null).revision());
    }
}
