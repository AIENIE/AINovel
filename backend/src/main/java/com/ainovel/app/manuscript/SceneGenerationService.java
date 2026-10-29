package com.ainovel.app.manuscript;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.context.CompiledSceneDraftContext;
import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.manuscript.context.SceneDraftContextManifest;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.SlopQualityGate;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.quality.SlopQualityResult;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SceneGenerationService {
    public record Admission(com.ainovel.app.quality.language.LanguageStandard.Selection language,com.ainovel.app.world.WorldBindingService.Frozen world) {}
    @Autowired
    private com.ainovel.app.quality.language.LanguageFeature languageFeature;
    @Autowired
    private com.ainovel.app.material.evidence.SceneReferenceSelectionService referenceSelection;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.ManuscriptContentService contents;
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    public static final String SCENE_DRAFT_SYSTEM_RULES_VERSION = "AINOVEL_SCENE_DRAFT_RULES_V1";

    @Autowired
    private CharacterCardRepository characterCardRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private SlopQualityGate slopQualityGate;
    @Autowired
    private ScenePlotQualitySupport scenePlotQualitySupport;
    @Autowired
    private SceneGenerationPromptBuilder sceneGenerationPromptBuilder;
    @Autowired
    private SceneDraftContextCompiler sceneDraftContextCompiler;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private com.ainovel.app.narrative.NarrativeGenerationCandidateStore candidateStore;
    @Autowired
    private com.ainovel.app.ai.AiValidationCallBudget validationBudget;

    public record EvaluationPair(String fastText, String craftedText) {
    }

    public record GenerationMetadata(
            String modelKey,
            String promptVersion,
            String promptHash,
            int attemptCount,
            SceneDraftContextManifest contextManifest,
            List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> references,
            String languageStandardVersion,
            com.ainovel.app.modelconfig.AiModelSelection selection
    ) {
        public GenerationMetadata(String modelKey,String promptVersion,String promptHash,int attemptCount,SceneDraftContextManifest contextManifest,List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> references,String languageStandardVersion) {
            this(modelKey,promptVersion,promptHash,attemptCount,contextManifest,references,languageStandardVersion,null);
        }
        public GenerationMetadata(String modelKey,String promptVersion,String promptHash,int attemptCount,SceneDraftContextManifest contextManifest){this(modelKey,promptVersion,promptHash,attemptCount,contextManifest,List.of(),null);}
        public GenerationMetadata(String modelKey,String promptVersion,String promptHash,int attemptCount,SceneDraftContextManifest contextManifest,List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> references){this(modelKey,promptVersion,promptHash,attemptCount,contextManifest,references,null);}
    }

    public record GenerationResult(String html, GenerationMetadata metadata, List<UUID> candidateIds,
            Integer minHan, Integer maxHan, Integer actualHan, boolean needsReview, String reviewReason) {
        public GenerationResult(String html,GenerationMetadata metadata){this(html,metadata,List.of(),null,null,null,false,null);}
    }
    @Autowired private com.ainovel.app.ai.AiAdmissionGuard admissionGuard;


    public String generateSceneSectionHtml(Manuscript manuscript, UUID sceneId, Map<String, String> existingSections) {
        return generateSceneSectionHtml(manuscript, sceneId, existingSections, GenerationMode.FAST);
    }

    public String generateSceneSectionHtml(Manuscript manuscript, UUID sceneId, Map<String, String> existingSections,
                                            GenerationMode mode) {
        return generateSceneSection(manuscript, sceneId, existingSections, mode).html();
    }

    public GenerationResult generateSceneSection(Manuscript manuscript, UUID sceneId,
                                                   Map<String, String> existingSections,
                                                   GenerationMode mode) {
        return generateSceneSection(manuscript,sceneId,existingSections,mode,languageSelection(manuscript));
    }
    public com.ainovel.app.quality.language.LanguageStandard.Selection languageSelection(Manuscript manuscript) {
        UUID storyId=manuscript.getOutline().getStory().getId();
        return languageFeature.selection(storyId);
    }
    public GenerationResult generateSceneSection(Manuscript manuscript, UUID sceneId, Map<String,String> existingSections,
            GenerationMode mode, com.ainovel.app.quality.language.LanguageStandard.Selection languageSelection) {
        return generateSceneSection(manuscript,sceneId,existingSections,mode,languageSelection,null,false);
    }
    public GenerationResult generateSceneSection(Manuscript manuscript,UUID sceneId,Map<String,String> existingSections,
            GenerationMode mode,com.ainovel.app.quality.language.LanguageStandard.Selection languageSelection,
            com.ainovel.app.world.WorldBindingService.Frozen admittedWorld,boolean requireHistoricalInput) {
        UUID operationId=com.ainovel.app.aioperation.AiOperationExecutionContext.operationId();
        if(operationId==null) operationId=UUID.randomUUID();
        var checkpoint=candidateStore.draft(operationId,1);
        FrozenInput frozen;
        if(checkpoint!=null&&checkpoint.getRequestJson()!=null) {
            if(checkpoint.getManuscriptVersion()!=manuscript.getVersion()||!checkpoint.getBranchId().equals(manuscript.getCurrentBranchId()))
                throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT,"SCENE_CANDIDATE_VERSION_CONFLICT");
            try {frozen=objectMapper.readValue(checkpoint.getRequestJson(),FrozenInput.class);}
            catch(Exception e){throw new com.ainovel.app.ai.AiResultUncertainException(e);}
        } else {
            if(requireHistoricalInput)throw new com.ainovel.app.ai.AiResultUncertainException(new IllegalStateException("WORLD_TASK_BASELINE_REQUIRED"));
            var selection=com.ainovel.app.modelconfig.AiSelectionContext.current();
            var writing=selection==null?null:selection.writingInput();
            if(writing!=null&&"SCENE".equals(writing.purpose()))frozen=objectMapper.convertValue(writing.frozen(),FrozenInput.class);
            else frozen=prepare(manuscript,sceneId,existingSections,mode,languageSelection,admittedWorld);
            if(frozen.sourceVersion()!=null&&frozen.branchId()!=null&&(frozen.sourceVersion()!=manuscript.getVersion()||!java.util.Objects.equals(frozen.branchId(),manuscript.getCurrentBranchId())))
                throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT,"SCENE_CANDIDATE_VERSION_CONFLICT");
            if(frozen.compiled()!=null)com.ainovel.app.worldcontext.WorldContextSelector.requireUsable(frozen.compiled().manifest().worldContext());
            candidateStore.freeze(operationId,manuscript,sceneId,frozen);
        }
        var sceneContext=frozen.scene(); var compiledContext=frozen.compiled();
        var characterContext=frozen.characters(); var previousContext=frozen.previous(); var outlinePlanning=frozen.planning();
        var frozenReferences=frozen.references();var initialPrompt=frozen.prompt();mode=frozen.mode();languageSelection=frozen.language();
        boolean languageStandard=languageSelection.generation();
        var story=manuscript.getOutline().getStory(); var owner=ownerOf(manuscript);
        int min=sceneContext.lengthRange().minHan(),max=sceneContext.lengthRange().maxHan();
        List<UUID> ids=new ArrayList<>();
        GenerationResult previous=null;
        String previousDraft=null;
        for(int attempt=1;attempt<=2;attempt++) {
            com.ainovel.app.aioperation.SceneGenerationProgress.report(attempt==1?"起草":"篇幅修订 1/1",attempt-1);
            referenceSelection.revalidate(owner,frozenReferences);
            // Freeze the entire first prompt, including materials and language settings; append only the revision instruction.
            var messages=new ArrayList<>(initialPrompt.messages());
            if(attempt==2) {
                int last=messages.size()-1;
                var message=messages.get(last);
                messages.set(last,new AiChatRequest.Message(message.role(),message.content()
                        +SceneGenerationPromptBuilder.lengthRevision(previousDraft,previous.actualHan(),min,max)));
            }
            var retained=candidateStore.draft(operationId,attempt);
            var request=new AiChatRequest(messages,null,null).forFeature("scene."+mode.name().toLowerCase(java.util.Locale.ROOT)+(attempt==1?".draft":".length"));
            if(attempt==2&&retained!=null&&retained.getRequestJson()!=null) {
                try {request=objectMapper.readValue(retained.getRequestJson(),AiChatRequest.class);messages=new ArrayList<>(request.messages());}
                catch(Exception e){throw new com.ainovel.app.ai.AiResultUncertainException(e);}
            }
            try {
                long bytes=messages.stream().mapToLong(m->m.content().getBytes(StandardCharsets.UTF_8).length).sum()+Math.max(2048L,(long)max*3);
                if(bytes>initialPrompt.tokenBudget()) throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"SCENE_PROMPT_BUDGET_EXCEEDED");
                admissionGuard.validate(request);
            } catch(com.ainovel.app.common.ApiStatusException e) {
                if(previous!=null && List.of("SCENE_PROMPT_BUDGET_EXCEEDED","AI_REQUEST_TOO_LARGE","AI_MESSAGE_CONTENT_INVALID").contains(e.getMessage()))
                    return new GenerationResult(previous.html(),previous.metadata(),List.copyOf(ids),min,max,previous.actualHan(),true,"REVISION_INPUT_BUDGET_EXCEEDED");
                throw e;
            }
            if(attempt==2) candidateStore.freezeRequest(operationId,manuscript,sceneId,2,request);
            var aiResponse=retained!=null&&retained.getActualHan()!=null?null:aiService.chat(owner,request);
            String normalized=retained!=null&&retained.getActualHan()!=null
                    ?normalizeGeneratedText(org.springframework.web.util.HtmlUtils.htmlUnescape(retained.getContent().replace("</p>","\n\n").replace("<br />","\n").replaceAll("<[^>]+>","")))
                    :normalizeGeneratedText(aiResponse.content());
            int count=countHanCharacters(normalized);
            String actualModel=aiResponse==null?null:aiResponse.actualModelKey();
            var modelSelection=aiResponse==null?null:aiResponse.selection();
            if(aiResponse==null&&retained!=null&&retained.getMetadataJson()!=null) {
                try {var source=objectMapper.readValue(retained.getMetadataJson(),com.ainovel.app.narrative.NarrativeGenerationCandidateStore.Source.class).generation();actualModel=source.modelKey();modelSelection=source.selection();}
                catch(Exception e){throw new com.ainovel.app.ai.AiResultUncertainException(e);}
            }
            var metadata=new GenerationMetadata(actualModel,compiledContext==null?SceneDraftContextCompiler.COMPILER_VERSION:compiledContext.compilerVersion(),
                    promptHash(messages),attempt,compiledContext==null?null:compiledContext.manifest(),frozenReferences,languageStandard?languageSelection.version():null,modelSelection);
            UUID id=candidateStore.retainDraft(operationId,manuscript,sceneId,toEditorHtml(normalized),attempt,min,max,count,
                    new com.ainovel.app.narrative.NarrativeGenerationCandidateStore.Source(mode,metadata,languageSelection));
            if(id!=null) ids.add(id);
            if(retained!=null&&"PROCESSED".equals(retained.getQualityStatus()))
                return new GenerationResult(retained.getContent(),metadata,List.copyOf(ids),min,max,retained.getActualHan(),count<min||count>max,count<min||count>max?"QUALITY_LENGTH_OUT_OF_RANGE":null);
            if(count>=min && count<=max) {
                var quality=evaluateGenerationQuality(owner,languageSelection.replacesLegacy(),compiledContext!=null&&compiledContext.isolated()
                        ?scenePlotQualitySupport.buildIsolatedQualityRequest(manuscript,sceneContext,compiledContext.content(),normalized)
                        :scenePlotQualitySupport.buildQualityRequest(manuscript,story,sceneContext,previousContext,characterContext,normalized));
                String accepted=quality.acceptedText();
                int acceptedCount=countHanCharacters(accepted);
                if(id!=null) candidateStore.qualityResult(id,toEditorHtml(accepted),acceptedCount);
                boolean outOfRange=acceptedCount<min||acceptedCount>max;
                if(!outOfRange && !(validationBudget.localQualityOnly())) {
                    if(compiledContext!=null&&compiledContext.isolated()) scenePlotQualitySupport.recordIsolatedPlotQuality(owner,manuscript,sceneContext,compiledContext,accepted);
                    else scenePlotQualitySupport.recordPlotQuality(owner,manuscript,story,sceneContext,outlinePlanning,previousContext,characterContext,accepted);
                }
                return new GenerationResult(toEditorHtml(accepted),metadata,List.copyOf(ids),min,max,acceptedCount,outOfRange,outOfRange?"QUALITY_LENGTH_OUT_OF_RANGE":null);
            }
            previous=new GenerationResult(toEditorHtml(normalized),metadata,List.copyOf(ids),min,max,count,true,"SCENE_LENGTH_OUT_OF_RANGE");
            previousDraft=normalized;
        }
        return previous;
    }

    public FrozenInput prepare(Manuscript manuscript,UUID sceneId,Map<String,String> existingSections,GenerationMode mode,
            com.ainovel.app.quality.language.LanguageStandard.Selection languageSelection,com.ainovel.app.world.WorldBindingService.Frozen world) {
        var scene=resolveSceneContext(manuscript.getOutline(),sceneId);var story=manuscript.getOutline().getStory();
        var compiled=world==null?sceneDraftContextCompiler.compile(manuscript,sceneId,existingSections):
                sceneDraftContextCompiler.compile(manuscript,sceneId,existingSections,SceneDraftContextCompiler.DEFAULT_TOKEN_BUDGET,world);
        String characters=compiled.characterContext();
        String previous=compiled.recentSceneContext();
        var references=freezeReferences(ownerOf(manuscript),story,manuscript,scene,compiled);
        var prompt=sceneGenerationPromptBuilder.build(ownerOf(manuscript),story,scene,characters,previous,null,0,1,scene.lengthRange().minHan(),scene.lengthRange().maxHan(),mode,compiled,promptReferences(references));
        if(languageSelection.generation())prompt=com.ainovel.app.quality.language.LanguageStandard.augment(prompt,languageSelection.version(),true);
        return new FrozenInput(scene,compiled,characters,previous,outlinePlanning(manuscript.getOutline()),references,prompt,mode,languageSelection,manuscript.getVersion(),manuscript.getCurrentBranchId(),planningHash(manuscript));
    }
    public FrozenInput prepare(Manuscript manuscript,UUID sceneId,GenerationMode mode,com.ainovel.app.world.WorldBindingService.Frozen world) {
        return prepare(manuscript,sceneId,contents.readAll(manuscript),mode,languageSelection(manuscript),world);
    }
    public record FrozenInput(SceneGenerationContext scene,CompiledSceneDraftContext compiled,String characters,String previous,
            String planning,List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> references,com.ainovel.app.prompt.AssembledPrompt prompt,
            GenerationMode mode,com.ainovel.app.quality.language.LanguageStandard.Selection language,Long sourceVersion,UUID branchId,String planningHash) {
        public FrozenInput(SceneGenerationContext scene,CompiledSceneDraftContext compiled,String characters,String previous,String planning,
                List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> references,com.ainovel.app.prompt.AssembledPrompt prompt,GenerationMode mode,
                com.ainovel.app.quality.language.LanguageStandard.Selection language){this(scene,compiled,characters,previous,planning,references,prompt,mode,language,null,null,null);}
    }

    public String planningHash(Manuscript manuscript) {
        var outline=manuscript.getOutline();var story=outline.getStory();
        var characters=characterCardRepository.findByStory(story).stream()
                .sorted(java.util.Comparator.comparing(c->Objects.toString(c.getId(),""))).toList();
        return com.ainovel.app.quality.language.LanguageProjection.hash(objectMapper.valueToTree(java.util.Arrays.asList(
                outline.getRevision(),outline.getTitle(),outline.getContentJson(),story.getTitle(),story.getSynopsis(),story.getGenre(),story.getTone(),characters.stream().map(c->java.util.Arrays.asList(c.getId(),c.getName(),c.getSynopsis(),c.getDetails(),c.getRelationships())).toList())).toString());
    }

    public boolean hasFrozenInput() {
        var operation=com.ainovel.app.aioperation.AiOperationExecutionContext.operationId();
        var draft=operation==null?null:candidateStore.draft(operation,1);
        return draft!=null&&draft.getRequestJson()!=null;
    }
    public String frozenPlanningHash(Manuscript manuscript) {
        var operation=com.ainovel.app.aioperation.AiOperationExecutionContext.operationId();
        var draft=operation==null?null:candidateStore.draft(operation,1);
        if(draft!=null&&draft.getRequestJson()!=null) {
            try {var input=objectMapper.readTree(draft.getRequestJson());if(input.hasNonNull("planningHash"))return input.path("planningHash").asText();}
            catch(Exception e){throw new com.ainovel.app.ai.AiResultUncertainException(e);}
        }
        var quote=com.ainovel.app.modelconfig.AiSelectionContext.current();
        if(quote!=null&&quote.writingInput()!=null&&quote.writingInput().frozen().hasNonNull("planningHash"))
            return quote.writingInput().frozen().path("planningHash").asText();
        return planningHash(manuscript);
    }

    public void requireBaseline(Manuscript manuscript,com.fasterxml.jackson.databind.JsonNode input) {
        if(input.hasNonNull("sourceVersion")&&(input.path("sourceVersion").asLong()!=manuscript.getVersion()
                ||!Objects.equals(input.path("branchId").isNull()?null:input.path("branchId").asText(),Objects.toString(manuscript.getCurrentBranchId(),null))))
            throw com.ainovel.app.worldcontext.WorldContextPreferences.conflict("SCENE_CANDIDATE_VERSION_CONFLICT");
        if(input.hasNonNull("planningHash")&&!input.path("planningHash").asText().equals(planningHash(manuscript)))
            throw com.ainovel.app.worldcontext.WorldContextPreferences.conflict("WORLD_CONTEXT_CHANGED");
    }

    private SlopQualityResult evaluateGenerationQuality(User user, boolean replacesLegacy, SlopQualityRequest request) {
        return replacesLegacy
                ? slopQualityGate.evaluateLocal(user, request) : slopQualityGate.evaluate(user, request);
    }
    private List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> freezeReferences(User owner,Story story,Manuscript manuscript,SceneGenerationContext scene,CompiledSceneDraftContext context){
        if(context!=null&&context.isolated())return List.of();
        String query=String.join(" ",Objects.toString(scene.chapterTitle(),""),Objects.toString(scene.chapterSummary(),""),Objects.toString(scene.sceneTitle(),""),Objects.toString(scene.sceneSummary(),""));
        return referenceSelection.select(owner,story.getId(),manuscript.getId(),scene.sceneId(),query);
    }
    private List<com.ainovel.app.prompt.PromptReference> promptReferences(List<com.ainovel.app.material.evidence.EvidenceDtos.Hit> references){return references.stream().map(h->new com.ainovel.app.prompt.PromptReference("material",h.materialId(),h.title(),h.text(),h.rank(),0)).toList();}

    /**
     * Produces a fast/crafted pair for blind evaluation without changing manuscript sections,
     * versions, or plot-quality records. All billable calls share the sample reference.
     */
    public EvaluationPair generateEvaluationPair(Manuscript manuscript, UUID sceneId, UUID evaluationSampleId) {
        Map<String, String> sections = contents.readAll(manuscript);
        CompiledSceneDraftContext compiledContext = sceneDraftContextCompiler.compile(manuscript, sceneId, sections);
        AiUsageContext baseContext = new AiUsageContext("G2_EVALUATION", String.valueOf(evaluationSampleId), "pair");
        String fastText = generateEvaluationCandidate(manuscript, sceneId, sections, GenerationMode.FAST,
                baseContext.forOperation("fast"), compiledContext);
        String craftedText = generateEvaluationCandidate(manuscript, sceneId, sections, GenerationMode.CRAFTED,
                baseContext.forOperation("crafted"), compiledContext);
        return new EvaluationPair(fastText, craftedText);
    }

    public String generateEvaluationCandidate(Manuscript manuscript, UUID sceneId, UUID evaluationSampleId,
                                              GenerationMode mode) {
        Map<String, String> sections = contents.readAll(manuscript);
        CompiledSceneDraftContext compiledContext = sceneDraftContextCompiler.compile(manuscript, sceneId, sections);
        AiUsageContext context = new AiUsageContext("G2_EVALUATION", String.valueOf(evaluationSampleId),
                "pair:" + mode.name().toLowerCase(java.util.Locale.ROOT));
        return generateEvaluationCandidate(manuscript, sceneId, sections, mode, context, compiledContext);
    }

    private String generateEvaluationCandidate(Manuscript manuscript,
                                               UUID sceneId,
                                               Map<String, String> existingSections,
                                               GenerationMode mode,
                                               AiUsageContext usageContext,
                                               CompiledSceneDraftContext compiledContext) {
        SceneGenerationContext sceneContext = resolveSceneContext(manuscript.getOutline(), sceneId);
        Story story = manuscript.getOutline().getStory();
        User owner = ownerOf(manuscript);
        String characterContext = compiledContext.characterContext();
        String previousContext = compiledContext.recentSceneContext();
        String previousDraft = null;
        int previousCount = 0;

        int maxAttempts=compiledContext!=null && compiledContext.isolated()?2:MAX_GENERATION_ATTEMPTS;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            var prompt = sceneGenerationPromptBuilder.build(
                    owner, story, sceneContext, characterContext, previousContext,
                    previousDraft, previousCount, attempt, sceneContext.lengthRange().minHan(), sceneContext.lengthRange().maxHan(), mode, compiledContext
            );
            String raw = aiService.chat(owner, new AiChatRequest(prompt.messages(), null, null).forFeature("scene."+mode.name().toLowerCase(java.util.Locale.ROOT)+(attempt==1?".draft":".length")),
                    usageContext.forOperation("draft-" + attempt)).content();
            String normalized = normalizeGeneratedText(raw);
            int hanCount = countHanCharacters(normalized);
            if (hanCount >= sceneContext.lengthRange().minHan() && hanCount <= sceneContext.lengthRange().maxHan()) {
                SlopQualityResult qualityResult = slopQualityGate.evaluate(
                        owner,
                        compiledContext != null && compiledContext.isolated()
                        ? scenePlotQualitySupport.buildIsolatedQualityRequest(manuscript,sceneContext,compiledContext.content(),normalized)
                        : scenePlotQualitySupport.buildQualityRequest(
                                manuscript, story, sceneContext, previousContext, characterContext, normalized,
                                "g2_evaluation"
                        ),
                        usageContext.forOperation("quality-"+attempt)
                );
                int acceptedCount = countHanCharacters(qualityResult.acceptedText());
                if (acceptedCount < sceneContext.lengthRange().minHan() || acceptedCount > sceneContext.lengthRange().maxHan()) {
                    previousDraft = qualityResult.acceptedText();
                    previousCount = acceptedCount;
                    continue;
                }
                return qualityResult.acceptedText();
            }
            previousDraft = normalized;
            previousCount = hanCount;
        }
        throw new RuntimeException("盲测候选生成失败：重试 " + MAX_GENERATION_ATTEMPTS
                + " 次后仍未达到 " + sceneContext.lengthRange().minHan() + "-" + sceneContext.lengthRange().maxHan() + " 汉字");
    }

    private String promptHash(List<AiChatRequest.Message> messages) {
        StringBuilder canonical = new StringBuilder(SCENE_DRAFT_SYSTEM_RULES_VERSION);
        if (messages != null) {
            for (AiChatRequest.Message message : messages) {
                if (message == null) {
                    continue;
                }
                canonical.append('\n')
                        .append(message.role() == null ? "" : message.role().trim())
                        .append('\n')
                        .append(message.content() == null ? "" : message.content());
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String outlinePlanning(Outline outline) {
        if (outline == null || outline.getContentJson() == null || outline.getContentJson().isBlank()) {
            return "暂无结构规划。";
        }
        try {
            Map<String, Object> root = objectMapper.readValue(outline.getContentJson(), new TypeReference<>() {});
            Object planning = root.get("planning");
            return planning == null ? "暂无结构规划。" : objectMapper.writeValueAsString(planning);
        } catch (Exception ex) {
            throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORED_DATA_INVALID");
        }
    }

    private SceneGenerationContext resolveSceneContext(Outline outline, UUID sceneId) {
        if (outline == null) {
            throw new BusinessException("大纲不存在，无法生成正文");
        }
        List<OutlineSaveRequest.ChapterPayload> chapters;
        try {
            Map<String, Object> root = objectMapper.readValue(
                    Optional.ofNullable(outline.getContentJson()).filter(v -> !v.isBlank()).orElse("{\"chapters\":[]}"),
                    new TypeReference<>() {}
            );
            chapters = objectMapper.convertValue(
                    root.getOrDefault("chapters", List.of()),
                    new TypeReference<List<OutlineSaveRequest.ChapterPayload>>() {}
            );
        } catch (Exception ex) {
            throw new RuntimeException("大纲内容异常，无法生成正文", ex);
        }

        for (OutlineSaveRequest.ChapterPayload chapter : chapters) {
            List<OutlineSaveRequest.ScenePayload> scenes = chapter.scenes() == null ? List.of() : chapter.scenes();
            for (int i = 0; i < scenes.size(); i++) {
                OutlineSaveRequest.ScenePayload scene = scenes.get(i);
                if (scene.id() != null && scene.id().equals(sceneId)) {
                    List<UUID> previousSceneIds = new ArrayList<>();
                    List<String> siblingTitles = new ArrayList<>();
                    for (int j = 0; j < scenes.size(); j++) {
                        OutlineSaveRequest.ScenePayload sibling = scenes.get(j);
                        siblingTitles.add(safeText(sibling.title(), "第" + (j + 1) + "节"));
                        if (j < i && sibling.id() != null) {
                            previousSceneIds.add(sibling.id());
                        }
                    }
                    return new SceneGenerationContext(
                            scene.id(),
                            safeText(chapter.title(), "未命名章节"),
                            safeText(chapter.summary(), ""),
                            chapter.order() == null ? 0 : chapter.order(),
                            safeText(scene.title(), "未命名场景"),
                            safeText(scene.summary(), ""),
                            scene.order() == null ? i + 1 : scene.order(),
                            previousSceneIds,
                            siblingTitles,
                            com.ainovel.app.story.model.SceneLengthRange.from(scene.planning())
                    );
                }
            }
        }
        throw new BusinessException("场景不存在，无法生成正文");
    }

    private String normalizeGeneratedText(String raw) { return normalizeGeneratedText(raw,objectMapper); }

    static String normalizeGeneratedText(String raw,ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("SCENE_RESPONSE_INVALID");
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        if (text.startsWith("{")) {
            try {
                Map<String,Object> root = objectMapper.readerFor(new TypeReference<Map<String,Object>>() {})
                        .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(text);
                var bodies = new java.util.HashSet<String>();
                for (var key : List.of("content", "text", "manuscript")) {
                    if (!root.containsKey(key)) continue;
                    if (!(root.get(key) instanceof String body) || body.isBlank()) throw new IllegalArgumentException();
                    bodies.add(body);
                }
                if (bodies.size() != 1) throw new IllegalArgumentException();
                text = bodies.iterator().next();
            } catch (Exception invalid) {
                throw new IllegalArgumentException("SCENE_RESPONSE_INVALID");
            }
        }
        text = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n");
        }
        return text;
    }

    static int countHanCharacters(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    static String toEditorHtml(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return "<p></p>";
        }
        String[] blocks = normalized.split("\\n\\s*\\n");
        StringBuilder html = new StringBuilder();
        for (String block : blocks) {
            String paragraph = escapeHtml(block.trim()).replace("\n", "<br />");
            if (paragraph.isBlank()) continue;
            html.append("<p>").append(paragraph).append("</p>");
        }
        if (html.length() == 0) {
            return "<p></p>";
        }
        return html.toString();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeText(String text, String fallback) {
        if (text == null) return fallback;
        String normalized = text.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private User ownerOf(Manuscript manuscript) {
        return manuscript.getOutline().getStory().getUser();
    }

}
