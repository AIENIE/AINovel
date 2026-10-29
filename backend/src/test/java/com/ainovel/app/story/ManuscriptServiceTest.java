package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.ManuscriptService;
import com.ainovel.app.manuscript.SceneGenerationPromptBuilder;
import com.ainovel.app.manuscript.SceneGenerationService;
import com.ainovel.app.manuscript.ScenePlotQualitySupport;
import com.ainovel.app.manuscript.attribution.SceneGenerationAttributionService;
import com.ainovel.app.manuscript.attribution.SceneGenerationRunStatus;
import com.ainovel.app.manuscript.attribution.SceneGenerationSnapshotStore;
import com.ainovel.app.manuscript.dto.ManuscriptDto;
import com.ainovel.app.manuscript.dto.SceneGenerationRunDto;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.MaterialRetrievalService;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.prompt.PromptAssemblyService;
import com.ainovel.app.quality.PlotQualityRequest;
import com.ainovel.app.quality.PlotQualityService;
import com.ainovel.app.quality.SlopQualityGate;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.quality.SlopQualityResult;
import com.ainovel.app.quality.SlopQualityStatus;
import com.ainovel.app.quality.SlopSeverity;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManuscriptServiceTest {
    @Test void worldUpgradeRetainsOldVersionResultWithoutWritingTheCurrentBody() {
        var repository=mock(ManuscriptRepository.class);
        var service=service(repository,mock(OutlineRepository.class),mock(CharacterCardRepository.class),mock(AiService.class),mock(ResourceAccessGuard.class),
            mock(SlopQualityGate.class),mock(PlotQualityService.class),mock(PromptAssemblyService.class),mock(MaterialRetrievalService.class),mock(StyleContextProvider.class));
        var owner=user("world-upgrade");var story=story(owner);var outline=outline(story,List.of());
        var manuscript=manuscript(UUID.randomUUID(),outline,Map.of());var scene=UUID.randomUUID();var candidate=UUID.randomUUID();
        var frozen=new com.ainovel.app.world.WorldBindingService.Resolved(UUID.randomUUID(),UUID.randomUUID(),"STORY",story.getId(),1,"群岛",1,"PUBLISHED","old",null,null);
        var latest=new com.ainovel.app.world.WorldBindingService.Resolved(frozen.worldId(),UUID.randomUUID(),"STORY",story.getId(),2,"群岛",2,"PUBLISHED","new",null,null);
        var manifest=new com.ainovel.app.manuscript.context.SceneDraftContextManifest("v4","hash","SHA-256",3500,10,story.getId(),outline.getId(),manuscript.getId(),scene,frozen.worldId(),1,1,"",List.of(),List.of(),null,frozen);
        var generator=mock(SceneGenerationService.class);when(generator.generateSceneSection(any(),any(),any(),any(),any(),any(),eq(false))).thenReturn(
            new SceneGenerationService.GenerationResult("<p>旧世界版本生成结果</p>",new SceneGenerationService.GenerationMetadata("test","v4","hash",1,manifest),List.of(candidate),null,null,null,false,null));
        var worlds=mock(com.ainovel.app.world.WorldBindingService.class);when(worlds.lockCurrent(manuscript)).thenReturn(latest);when(worlds.same(frozen,latest)).thenCallRealMethod();
        ReflectionTestUtils.setField(service,"sceneGenerationService",generator);ReflectionTestUtils.setField(service,"worldBindings",worlds);
        when(repository.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        var result=service.generateSceneOutcome(manuscript.getId(),scene,com.ainovel.app.manuscript.GenerationMode.FAST,null);
        assertEquals("NEEDS_REVIEW",result.outcome());assertEquals("WORLD_BINDING_CHANGED",result.reviewReason());assertEquals(List.of(candidate),result.candidateIds());
        assertEquals("{}",manuscript.getSectionsJson());verify(repository,never()).saveAndFlush(any());
    }
    @Test void planningChangeDuringGenerationRetainsCandidateWithoutSaving() {
        var repository=mock(ManuscriptRepository.class);
        var service=service(repository,mock(OutlineRepository.class),mock(CharacterCardRepository.class),mock(AiService.class),mock(ResourceAccessGuard.class),
                mock(SlopQualityGate.class),mock(PlotQualityService.class),mock(PromptAssemblyService.class),mock(MaterialRetrievalService.class),mock(StyleContextProvider.class));
        var owner=new User();var story=new Story();story.setUser(owner);var outline=new Outline();outline.setStory(story);
        var manuscript=new Manuscript();manuscript.setId(UUID.randomUUID());manuscript.setOutline(outline);manuscript.setSectionsJson("{}");
        var scene=UUID.randomUUID();var candidate=UUID.randomUUID();var generator=mock(SceneGenerationService.class);
        when(generator.frozenPlanningHash(manuscript)).thenReturn("original");when(generator.planningHash(manuscript)).thenReturn("edited");
        when(generator.generateSceneSection(any(),any(),any(),any(),any(),any(),eq(false))).thenReturn(
                new SceneGenerationService.GenerationResult("<p>保留候选</p>",null,List.of(candidate),100,200,150,false,null));
        ReflectionTestUtils.setField(service,"sceneGenerationService",generator);
        when(repository.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        var result=service.generateSceneOutcome(manuscript.getId(),scene,com.ainovel.app.manuscript.GenerationMode.FAST,null);
        assertEquals("NEEDS_REVIEW",result.outcome());assertEquals("SCENE_INPUT_CHANGED",result.reviewReason());
        assertEquals(List.of(candidate),result.candidateIds());assertEquals("{}",manuscript.getSectionsJson());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void h2VersionConflictRetainsCandidateWithoutWritingTheCurrentBody() {
        var repository=mock(ManuscriptRepository.class);var access=mock(ResourceAccessGuard.class);
        var s=service(repository,mock(OutlineRepository.class),mock(CharacterCardRepository.class),mock(AiService.class),access,
                mock(SlopQualityGate.class),mock(PlotQualityService.class),mock(PromptAssemblyService.class),mock(MaterialRetrievalService.class),mock(StyleContextProvider.class));
        var owner=new User();owner.setId(UUID.randomUUID());var story=new Story();story.setUser(owner);
        var outline=new Outline();outline.setStory(story);var m=new Manuscript();m.setId(UUID.randomUUID());m.setOutline(outline);m.setSectionsJson("{}");m.setCurrentBranchId(UUID.randomUUID());
        UUID scene=UUID.randomUUID();var stamp=new com.ainovel.app.narrative.NarrativeContextDtos.Stamp(m.getId(),m.getCurrentBranchId(),0,0,1,1,"hash");
        var manifest=new com.ainovel.app.manuscript.context.SceneDraftContextManifest("h2","hash","SHA-256",3500,10,null,null,m.getId(),scene,null,1,1,"",List.of(),List.of(),stamp);
        var generator=mock(SceneGenerationService.class);when(generator.generateSceneSection(any(),any(),any(),any(),any(),any(),eq(false))).thenReturn(
                new SceneGenerationService.GenerationResult("<p>生成候选</p>",new SceneGenerationService.GenerationMetadata("test","h2","hash",1,manifest)));
        var context=mock(com.ainovel.app.narrative.NarrativeContextService.class);var archive=mock(com.ainovel.app.narrative.NarrativeGenerationCandidateStore.class);
        ReflectionTestUtils.setField(s,"sceneGenerationService",generator);ReflectionTestUtils.setField(s,"narrativeContext",context);ReflectionTestUtils.setField(s,"retainedCandidates",archive);
        when(repository.findWithStoryById(m.getId())).thenReturn(Optional.of(m));
        org.mockito.Mockito.doThrow(new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT,"H2_CONTEXT_CHANGED")).when(context).requireStamp(owner,stamp);
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->s.generateForScene(m.getId(),scene));
        verify(archive,never()).retain(any(),any(),any(),any());verify(archive,never()).applied(any());verify(repository,never()).saveAndFlush(any());assertEquals("{}",m.getSectionsJson());
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnCodec jsonColumnCodec = new JsonColumnCodec(objectMapper);

    @Test
    void generateForSceneShouldPersistAcceptedGateResult() {
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        AiService aiService = mock(AiService.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        SlopQualityGate slopQualityGate = mock(SlopQualityGate.class);
        PlotQualityService plotQualityService = mock(PlotQualityService.class);
        PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
        MaterialRetrievalService materialRetrievalService = mock(MaterialRetrievalService.class);
        StyleContextProvider styleContextProvider = mock(StyleContextProvider.class);
        ManuscriptService service = service(
                manuscriptRepository,
                mock(OutlineRepository.class),
                characterCardRepository,
                aiService,
                accessGuard,
                slopQualityGate,
                plotQualityService,
                promptAssemblyService,
                materialRetrievalService,
                styleContextProvider
        );

        User owner = user("manuscript_author");
        UUID previousSceneId = UUID.randomUUID();
        UUID targetSceneId = UUID.randomUUID();
        Story story = story(owner);
        Outline outline = outline(story, List.of(
                new OutlineSaveRequest.ChapterPayload(
                        UUID.randomUUID(),
                        "第一章",
                        "旧案重启",
                        1,
                        Map.of("purpose", "setup"),
                        List.of(
                                new OutlineSaveRequest.ScenePayload(previousSceneId, "线索浮现", "发现铜扣", null, 1, Map.of()),
                                new OutlineSaveRequest.ScenePayload(targetSceneId, "追入雨巷", "主角追查误导线索", null, 2, Map.of())
                        )
                )
        ));
        Manuscript manuscript = manuscript(UUID.randomUUID(), outline, Map.of(previousSceneId.toString(), "<p>上一场景留下铜扣线索。</p>"));
        CharacterCard character = character(story, "林烬", "谨慎的调查员", "总先排除伪证", "与周燃互相制衡");
        String acceptedText = "稿".repeat(2900);

        when(manuscriptRepository.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        when(manuscriptRepository.saveAndFlush(any(Manuscript.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(characterCardRepository.findByStory(story)).thenReturn(List.of(character));
        when(promptAssemblyService.assembleSceneDraft(any())).thenReturn(new AssembledPrompt(
                List.of(new AiChatRequest.Message("user", "生成本场景")),
                128000
        ));
        when(materialRetrievalService.search(eq(owner), any())).thenReturn(List.of());
        when(styleContextProvider.buildSlopContext(story)).thenReturn("冷峻、压迫感强");
        when(aiService.chat(eq(owner), any())).thenReturn(new AiChatResponse("assistant", "文".repeat(2900), null, 0));
        when(slopQualityGate.evaluate(eq(owner), any())).thenReturn(new SlopQualityResult(
                UUID.randomUUID(),
                acceptedText,
                18,
                SlopSeverity.LOW,
                false,
                0,
                SlopQualityStatus.ACCEPTED,
                List.of()
        ));

        ManuscriptDto dto = service.generateForScene(manuscript.getId(), targetSceneId);

        assertEquals(manuscript.getId(), dto.id());
        assertTrue(dto.sections().get(targetSceneId.toString()).startsWith("<p>稿稿稿"));
        assertEquals("<p>上一场景留下铜扣线索。</p>", dto.sections().get(previousSceneId.toString()));
        verify(accessGuard, times(3)).assertOwner(owner);
        verify(manuscriptRepository).saveAndFlush(manuscript);

        ArgumentCaptor<SlopQualityRequest> gateRequestCaptor = ArgumentCaptor.forClass(SlopQualityRequest.class);
        verify(slopQualityGate).evaluate(eq(owner), gateRequestCaptor.capture());
        SlopQualityRequest gateRequest = gateRequestCaptor.getValue();
        assertSame(manuscript.getId(), gateRequest.manuscriptId());
        assertEquals(targetSceneId, gateRequest.sceneId());
        assertTrue(gateRequest.previousContext().contains("铜扣线索"));
        assertEquals("冷峻、压迫感强", gateRequest.styleContext());

        ArgumentCaptor<PlotQualityRequest> plotRequestCaptor = ArgumentCaptor.forClass(PlotQualityRequest.class);
        verify(plotQualityService).analyzeDuringGeneration(eq(owner), plotRequestCaptor.capture());
        assertEquals(targetSceneId, plotRequestCaptor.getValue().sceneId());
        assertEquals(acceptedText, plotRequestCaptor.getValue().sceneText());
    }

    @Test
    void generateForSceneShouldStopAfterOneRevisionAndExposeReviewOutcome() {
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        AiService aiService = mock(AiService.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
        MaterialRetrievalService materialRetrievalService = mock(MaterialRetrievalService.class);
        StyleContextProvider styleContextProvider = mock(StyleContextProvider.class);
        ManuscriptService service = service(
                manuscriptRepository,
                mock(OutlineRepository.class),
                characterCardRepository,
                aiService,
                accessGuard,
                mock(SlopQualityGate.class),
                mock(PlotQualityService.class),
                promptAssemblyService,
                materialRetrievalService,
                styleContextProvider
        );

        User owner = user("retry_author");
        UUID targetSceneId = UUID.randomUUID();
        Story story = story(owner);
        Outline outline = outline(story, List.of(
                new OutlineSaveRequest.ChapterPayload(
                        UUID.randomUUID(),
                        "第一章",
                        "章摘要",
                        1,
                        Map.of(),
                        List.of(new OutlineSaveRequest.ScenePayload(targetSceneId, "目标场景", "场景摘要", null, 1, Map.of()))
                )
        ));
        Manuscript manuscript = manuscript(UUID.randomUUID(), outline, Map.of());

        when(manuscriptRepository.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        when(characterCardRepository.findByStory(story)).thenReturn(List.of());
        when(promptAssemblyService.assembleSceneDraft(any())).thenReturn(new AssembledPrompt(
                List.of(new AiChatRequest.Message("user", "生成本场景")),
                128000
        ));
        when(materialRetrievalService.search(eq(owner), any())).thenReturn(List.of());
        when(styleContextProvider.buildSlopContext(story)).thenReturn("暂无风格画像");
        when(aiService.chat(eq(owner), any())).thenReturn(new AiChatResponse("assistant", "短".repeat(2000), null, 0));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generateForScene(manuscript.getId(), targetSceneId));

        assertEquals("SCENE_LENGTH_OUT_OF_RANGE",ex.getMessage());
        verify(aiService, times(2)).chat(eq(owner), any());
        verify(promptAssemblyService, times(1)).assembleSceneDraft(any());
        verify(manuscriptRepository, never()).save(any(Manuscript.class));
    }

    private ManuscriptService service(
            ManuscriptRepository manuscriptRepository,
            OutlineRepository outlineRepository,
            CharacterCardRepository characterCardRepository,
            AiService aiService,
            ResourceAccessGuard accessGuard,
            SlopQualityGate slopQualityGate,
            PlotQualityService plotQualityService,
            PromptAssemblyService promptAssemblyService,
            MaterialRetrievalService materialRetrievalService,
            StyleContextProvider styleContextProvider
    ) {
        ManuscriptService service = new ManuscriptService();
        ReflectionTestUtils.setField(service, "manuscriptRepository", manuscriptRepository);
        ReflectionTestUtils.setField(service, "outlineRepository", outlineRepository);
        ReflectionTestUtils.setField(service, "accessGuard", accessGuard);
        ReflectionTestUtils.setField(service, "jsonColumnCodec", jsonColumnCodec);
        com.ainovel.app.manuscript.ManuscriptContentTestSupport.injectLegacy(service);
        ReflectionTestUtils.setField(service, "sceneGenerationService", sceneGenerationService(
                characterCardRepository,
                aiService,
                slopQualityGate,
                plotQualityService,
                promptAssemblyService,
                materialRetrievalService,
                styleContextProvider
        ));
        SceneGenerationSnapshotStore snapshotStore = mock(SceneGenerationSnapshotStore.class);
        when(snapshotStore.createGenerationSnapshot(any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        SceneGenerationAttributionService attributionService = mock(SceneGenerationAttributionService.class);
        when(attributionService.recordGeneration(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Manuscript generatedManuscript = invocation.getArgument(0);
                    UUID generatedSceneId = invocation.getArgument(1);
                    UUID generationVersionId = invocation.getArgument(2);
                    Instant createdAt = Instant.now();
                    return new SceneGenerationRunDto(
                            UUID.randomUUID(),
                            generatedManuscript.getId(),
                            generatedSceneId,
                            generatedManuscript.getOutline().getStory().getUser().getId(),
                            "fast",
                            SceneGenerationRunStatus.GENERATED,
                            "deepseek-v4-flash",
                            "scene-draft-v2",
                            1,
                            "a".repeat(64),
                            Map.of(
                                    "promptVersion", "scene-draft-v2",
                                    "tokenBudget", 0,
                                    "tokenUsed", 0,
                                    "sources", List.of()
                            ),
                            generationVersionId,
                            null,
                            null,
                            null,
                            0,
                            0,
                            1.0d,
                            false,
                            List.of(),
                            null,
                            false,
                            createdAt,
                            createdAt
                    );
                });
        ReflectionTestUtils.setField(service, "sceneGenerationSnapshotStore", snapshotStore);
        ReflectionTestUtils.setField(service, "sceneGenerationAttributionService", attributionService);
        ReflectionTestUtils.setField(service, "eventPublisher", mock(ApplicationEventPublisher.class));
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        ReflectionTestUtils.setField(service, "transactionTemplate", transactions);
        var worldBindings=mock(com.ainovel.app.world.WorldBindingService.class);
        when(worldBindings.same(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull())).thenReturn(true);
        ReflectionTestUtils.setField(service,"worldBindings",worldBindings);
        var preferences=mock(com.ainovel.app.worldcontext.WorldContextPreferences.class);
        when(preferences.read(any(),any())).thenReturn(com.ainovel.app.worldcontext.WorldContextDtos.Preferences.defaults(null));
        ReflectionTestUtils.setField(service,"worldPreferences",preferences);
        ReflectionTestUtils.setField(service, "languageQuality", mock(com.ainovel.app.quality.language.LanguageQualityService.class));
        ReflectionTestUtils.setField(service, "materialEvidence", mock(com.ainovel.app.material.evidence.MaterialEvidenceService.class));
        return service;
    }

    private SceneGenerationService sceneGenerationService(
            CharacterCardRepository characterCardRepository,
            AiService aiService,
            SlopQualityGate slopQualityGate,
            PlotQualityService plotQualityService,
            PromptAssemblyService promptAssemblyService,
            MaterialRetrievalService materialRetrievalService,
            StyleContextProvider styleContextProvider
    ) {
        SceneGenerationService service = new SceneGenerationService();
        com.ainovel.app.manuscript.SceneGenerationTestSupport.install(service, objectMapper, characterCardRepository);
        SceneGenerationPromptBuilder promptBuilder = new SceneGenerationPromptBuilder();
        ScenePlotQualitySupport plotQualitySupport = new ScenePlotQualitySupport();
        ReflectionTestUtils.setField(service, "characterCardRepository", characterCardRepository);
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "slopQualityGate", slopQualityGate);
        ReflectionTestUtils.setField(promptBuilder, "promptAssemblyService", promptAssemblyService);
        ReflectionTestUtils.setField(promptBuilder, "materialRetrievalService", materialRetrievalService);
        ReflectionTestUtils.setField(plotQualitySupport, "plotQualityService", plotQualityService);
        ReflectionTestUtils.setField(plotQualitySupport, "styleContextProvider", styleContextProvider);
        ReflectionTestUtils.setField(service, "sceneGenerationPromptBuilder", promptBuilder);
        ReflectionTestUtils.setField(service, "scenePlotQualitySupport", plotQualitySupport);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        return service;
    }

    private Manuscript manuscript(UUID manuscriptId, Outline outline, Map<String, String> sections) {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setOutline(outline);
        manuscript.setTitle("第一稿");
        manuscript.setWorldId("world-1");
        manuscript.setSectionsJson(jsonColumnCodec.writeRequired(sections));
        manuscript.setCharacterLogsJson("[]");
        return manuscript;
    }

    private Outline outline(Story story, List<OutlineSaveRequest.ChapterPayload> chapters) {
        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);
        outline.setTitle("案件大纲");
        outline.setWorldId("world-1");
        outline.setContentJson(jsonColumnCodec.writeRequired(Map.of(
                "planning", Map.of("mystery", "副将复活"),
                "chapters", chapters
        )));
        return outline;
    }

    private Story story(User owner) {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("雨城疑案");
        story.setSynopsis("主角在雨城重查副将复活真相。");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        return story;
    }

    private CharacterCard character(Story story, String name, String synopsis, String details, String relationships) {
        CharacterCard card = new CharacterCard();
        card.setId(UUID.randomUUID());
        card.setStory(story);
        card.setName(name);
        card.setSynopsis(synopsis);
        card.setDetails(details);
        card.setRelationships(relationships);
        return card;
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRemoteUid(10003L);
        return user;
    }
}
