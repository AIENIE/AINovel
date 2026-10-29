package com.ainovel.app.manuscript;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.narrative.*;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.quality.*;
import com.ainovel.app.story.model.*;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SceneLengthRevisionTest {
    final SceneGenerationService service=new SceneGenerationService();
    final AiService ai=mock(AiService.class);
    final SlopQualityGate quality=mock(SlopQualityGate.class);
    final SceneGenerationPromptBuilder builder=mock(SceneGenerationPromptBuilder.class);
    final NarrativeGenerationCandidateStore archive=mock(NarrativeGenerationCandidateStore.class);
    final Manuscript manuscript=new Manuscript();
    final UUID scene=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();
        SceneGenerationTestSupport.install(service,json,mock(CharacterCardRepository.class));
        var user=new User();user.setId(UUID.randomUUID());
        var story=new Story();story.setId(UUID.randomUUID());story.setUser(user);
        var outline=new Outline();outline.setStory(story);
        outline.setContentJson(json.writeValueAsString(Map.of("chapters",List.of(Map.of("id",UUID.randomUUID(),"title","章","order",1,
                "scenes",List.of(Map.of("id",scene,"title","节","order",1,"planning",Map.of("minHan",2800,"maxHan",3200))))))));
        manuscript.setId(UUID.randomUUID());manuscript.setOutline(outline);manuscript.setCurrentBranchId(UUID.randomUUID());
        ReflectionTestUtils.setField(service,"objectMapper",json);
        ReflectionTestUtils.setField(service,"aiService",ai);
        ReflectionTestUtils.setField(service,"sceneGenerationPromptBuilder",builder);
        ReflectionTestUtils.setField(service,"candidateStore",archive);
        ReflectionTestUtils.setField(service,"slopQualityGate",quality);
        ReflectionTestUtils.setField(service,"scenePlotQualitySupport",mock(ScenePlotQualitySupport.class));
        ReflectionTestUtils.setField(service,"characterCardRepository",mock(CharacterCardRepository.class));
        ManuscriptContentTestSupport.injectLegacy(service);
        when(builder.build(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),anyInt(),anyInt(),any(),any(),anyList()))
                .thenReturn(new AssembledPrompt(List.of(new AiChatRequest.Message("user","冻结大纲：归还钥匙，不打开门。")),128000));
        when(archive.retainDraft(any(),any(),any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),any())).thenAnswer(i->UUID.randomUUID());
    }
    SceneGenerationService.GenerationResult generate(){return service.generateSceneSection(manuscript,scene,Map.of(),GenerationMode.FAST);}
    AiChatResponse response(int han){return new AiChatResponse("assistant","汉".repeat(han),null,0);}
    void quality(int han){when(quality.evaluate(any(),any())).thenReturn(new SlopQualityResult(UUID.randomUUID(),"汉".repeat(han),0,SlopSeverity.LOW,false,0,SlopQualityStatus.ACCEPTED,List.of()));}

    @ParameterizedTest @CsvSource({"3000,0,3000,1,false","2719,3000,3000,2,false","2719,2750,2750,2,true","2719,3400,3400,2,true","3400,3000,3000,2,false"})
    void returnsDeliverableOutcomeWithoutThirdDraft(int first,int second,int expected,int calls,boolean review) {
        when(ai.chat(any(),any())).thenReturn(response(first),response(second));quality(expected);
        var result=generate();assertEquals(review,result.needsReview());assertEquals(expected,result.actualHan());
        assertEquals(calls,result.candidateIds().size());verify(ai,times(calls)).chat(any(),any());
        verify(builder,times(1)).build(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),anyInt(),anyInt(),any(),any(),anyList());
    }
    @Test void persistsInitialBeforeFailedSecondCallAndPreservesFailure() {
        when(ai.chat(any(),any())).thenReturn(response(2719)).thenThrow(new IllegalStateException("network failure"));
        assertThrows(IllegalStateException.class,this::generate);
        var order=inOrder(ai,archive);order.verify(ai).chat(any(),any());
        order.verify(archive).retainDraft(any(),eq(manuscript),eq(scene),anyString(),eq(1),eq(2800),eq(3200),eq(2719),any());
        order.verify(ai).chat(any(),any());verify(archive,never()).qualityResult(any(),anyString(),anyInt());
    }
    @Test void qualityOverflowGoesStraightToReview() {
        when(ai.chat(any(),any())).thenReturn(response(3000));quality(3300);
        var result=generate();assertTrue(result.needsReview());assertEquals(3300,result.actualHan());
        assertEquals("QUALITY_LENGTH_OUT_OF_RANGE",result.reviewReason());verify(ai,times(1)).chat(any(),any());
    }
    @Test void fullDraftAndEndingAreSentWithComputedDelta() {
        String draft="汉".repeat(2710)+"他归还钥匙没有开门。";
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant",draft,null,0),response(3000));quality(3000);
        generate();var calls=ArgumentCaptor.forClass(AiChatRequest.class);verify(ai,times(2)).chat(any(),calls.capture());
        String prompt=calls.getAllValues().get(1).messages().getFirst().content();
        assertTrue(prompt.contains(draft));assertTrue(prompt.contains("净增加约 281 汉字"));assertTrue(prompt.startsWith("冻结大纲"));
        assertFalse(prompt.contains("可重写"));
    }
    @Test void revisionBudgetStopsWithoutTruncationOrAnotherCall() {
        var admission=new AiAdmissionGuard(null,new ObjectMapper(),64,3500,100000,20,2,32);
        ReflectionTestUtils.setField(service,"admissionGuard",admission);
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant","汉".repeat(2700)+"a".repeat(1200),null,0));
        var result=generate();assertTrue(result.needsReview());assertEquals("REVISION_INPUT_BUDGET_EXCEEDED",result.reviewReason());
        verify(ai,times(1)).chat(any(),any());assertTrue(result.html().contains("a".repeat(1200)));
    }

    @Test void recoveryReusesInitialDraftAndExactFrozenRevisionRequest() throws Exception {
        var rows=new HashMap<Integer,NarrativeGenerationCandidate>();
        var repository=mock(NarrativeGenerationCandidateRepository.class);
        when(repository.findByOperationIdAndGenerationSequence(any(),anyInt()))
                .thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(1))));
        when(repository.saveAndFlush(any())).thenAnswer(i->{
            NarrativeGenerationCandidate row=i.getArgument(0);
            if(row.getId()==null) ReflectionTestUtils.setField(row,"id",UUID.randomUUID());
            rows.put(row.getGenerationSequence(),row);return row;
        });
        ReflectionTestUtils.setField(service,"candidateStore",new NarrativeGenerationCandidateStore(repository,new ObjectMapper().findAndRegisterModules()));
        String initial="汉".repeat(2700)+"\n结尾保留。\n\n不得越过门口。";
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant",initial,null,0))
                .thenThrow(new AiResultUncertainException(new IllegalStateException("connection lost")))
                .thenReturn(response(2750));
        UUID operation=UUID.randomUUID();
        Class<?> fence=Class.forName("com.ainovel.app.aioperation.AiOperationExecutionContext$Fence");
        Object passthrough=java.lang.reflect.Proxy.newProxyInstance(fence.getClassLoader(),new Class<?>[]{fence},
                (proxy,method,args)->((java.util.function.Supplier<?>)args[0]).get());
        var install=com.ainovel.app.aioperation.AiOperationExecutionContext.class.getDeclaredMethod("install",UUID.class,fence);
        install.setAccessible(true);
        try(var identity=(AutoCloseable)install.invoke(null,operation,passthrough)) {
            assertThrows(AiResultUncertainException.class,this::generate);
            // Changes after the original call must not rebuild the frozen prompt or mode.
            manuscript.getOutline().setContentJson("{\"chapters\":[]}");
            var recovered=service.generateSceneSection(manuscript,scene,Map.of(),GenerationMode.CRAFTED);
            assertTrue(recovered.needsReview());assertEquals(2,recovered.candidateIds().size());
        }
        var requests=ArgumentCaptor.forClass(AiChatRequest.class);
        verify(ai,times(3)).chat(any(),requests.capture());
        var wireJson=new ObjectMapper();
        assertEquals(wireJson.writeValueAsString(requests.getAllValues().get(1)),
                wireJson.writeValueAsString(requests.getAllValues().get(2)));
        assertTrue(requests.getAllValues().get(2).messages().getFirst().content().contains(initial));
        verify(builder,times(1)).build(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),anyInt(),anyInt(),any(),any(),anyList());
        assertEquals(2,rows.size());
    }
}
