package com.ainovel.app.manuscript;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.attribution.SceneGenerationAttributionService;
import com.ainovel.app.manuscript.attribution.SceneGenerationManifest;
import com.ainovel.app.manuscript.attribution.SceneGenerationSnapshotStore;
import com.ainovel.app.manuscript.attribution.SceneContentEditedEvent;
import com.ainovel.app.manuscript.context.SceneDraftContextManifest;
import com.ainovel.app.manuscript.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.ainovel.app.common.ApiStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ManuscriptService {
    @Autowired private com.ainovel.app.world.WorldBindingService worldBindings;
    @Autowired private com.ainovel.app.worldcontext.WorldContextPreferences worldPreferences;
    @Autowired
    private com.ainovel.app.quality.language.LanguageQualityService languageQuality;
    @Autowired
    private com.ainovel.app.material.evidence.MaterialEvidenceService materialEvidence;
    @Autowired private com.ainovel.app.manuscript.repo.ManuscriptCreationReceiptRepository creationReceipts;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.ManuscriptContentService contents;
    @Autowired private com.ainovel.app.narrative.NarrativeContextService narrativeContext;
    @Autowired private com.ainovel.app.narrative.NarrativeGenerationCandidateStore retainedCandidates;
    @Autowired private com.ainovel.app.narrative.NarrativeGenerationCandidateRepository candidates;
    private static final String SCENE_DRAFT_PROMPT_VERSION = "scene-draft-v2";
    @Autowired
    private ManuscriptRepository manuscriptRepository;
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private JsonColumnCodec jsonColumnCodec;
    @Autowired
    private SceneGenerationService sceneGenerationService;
    @Autowired
    private SceneGenerationSnapshotStore sceneGenerationSnapshotStore;
    @Autowired
    private SceneGenerationAttributionService sceneGenerationAttributionService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public List<ManuscriptDto> listByOutline(UUID outlineId) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return manuscriptRepository.findByOutline(outline).stream().map(this::toDto).toList();
    }

    @Transactional
    public ManuscriptDto create(UUID outlineId, ManuscriptCreateRequest request) {
        return create(outlineId, request, null);
    }

    @Transactional
    public ManuscriptDto create(UUID outlineId, ManuscriptCreateRequest request, String requestKey) {
        String key = null;
        if (requestKey != null) {
            try {
                key = UUID.fromString(requestKey).toString();
                if (!key.equalsIgnoreCase(requestKey)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new ApiStatusException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
            }
        }
        // Serialize competing creation intents for this outline before checking the receipt.
        Outline outline = (key == null ? outlineRepository.findByIdWithStoryUser(outlineId) : outlineRepository.findByIdForUpdate(outlineId))
                .orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        UUID userId = outline.getStory().getUser().getId();
        String hash = key == null ? null : creationHash(request);
        if (key != null) {
            var receipt = creationReceipts.findByUserIdAndOutlineIdAndRequestKey(userId, outlineId, key);
            if (receipt.isPresent()) {
                if (!hash.equals(receipt.get().getRequestHash())) throw new ApiStatusException(HttpStatus.CONFLICT, "CREATION_REQUEST_CONFLICT");
                if (receipt.get().getManuscriptId() == null) throw new ApiStatusException(HttpStatus.GONE, "CREATED_MANUSCRIPT_DELETED");
                try { return objectMapper.readValue(receipt.get().getResponseJson(), ManuscriptDto.class); }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("INVALID_CREATION_RECEIPT", ex); }
            }
        }
        Manuscript manuscript = new Manuscript();
        manuscript.setOutline(outline);
        manuscript.setTitle(request.title());
        com.ainovel.app.world.WorldBindingService.preserveLegacy(null, request.worldId());
        contents.initialize(manuscript);
        manuscript.setCharacterLogsJson(writeJson(new ArrayList<>()));
        manuscriptRepository.saveAndFlush(manuscript);
        ManuscriptDto result = toDto(manuscript);
        if (key != null) {
            try {
                creationReceipts.saveAndFlush(new com.ainovel.app.manuscript.model.ManuscriptCreationReceipt(
                        userId, outlineId, key, hash, manuscript.getId(), objectMapper.writeValueAsString(result)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("INVALID_CREATION_RECEIPT", ex); }
        }
        return result;
    }

    private String creationHash(ManuscriptCreateRequest request) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(request);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("CREATION_HASH_FAILED", ex);
        }
    }

    @Transactional(readOnly = true)
    public ManuscriptDto get(UUID id) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(id).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return toDto(manuscript);
    }

    @Transactional
    public void delete(UUID id) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(id).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        manuscriptRepository.delete(manuscript);
    }

    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId) {
        return generateForScene(manuscriptId, sceneId, GenerationMode.FAST);
    }

    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId, GenerationMode mode) {
        return generateForScene(manuscriptId,sceneId,mode,null);
    }
    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId, GenerationMode mode,
            com.ainovel.app.quality.language.LanguageStandard.Selection frozenLanguage) {
        var result=generateSceneOutcome(manuscriptId,sceneId,mode,frozenLanguage);
        if("NEEDS_REVIEW".equals(result.outcome())) throw new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"SCENE_LENGTH_OUT_OF_RANGE");
        return result.manuscript();
    }

    public record SceneOutcome(String outcome,UUID manuscriptId,UUID sceneId,List<UUID> candidateIds,
            Integer minHan,Integer maxHan,Integer actualHan,int revisionCount,String reviewReason,ManuscriptDto manuscript) {}

    public SceneOutcome generateSceneOutcome(UUID manuscriptId,UUID sceneId,GenerationMode mode,
            com.ainovel.app.quality.language.LanguageStandard.Selection frozenLanguage) {
        return generateSceneOutcome(manuscriptId,sceneId,mode,frozenLanguage,null,false);
    }
    public SceneOutcome generateSceneOutcome(UUID manuscriptId,UUID sceneId,GenerationMode mode,
            com.ainovel.app.quality.language.LanguageStandard.Selection frozenLanguage,
            com.ainovel.app.world.WorldBindingService.Frozen admittedWorld,boolean requireHistoricalInput) {
        GenerationSnapshot snapshot = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                    .orElseThrow(() -> new BusinessException("稿件不存在"));
            User owner = ownerOf(manuscript);
            accessGuard.assertOwner(owner);
            worldBindings.lockCurrent(manuscript);
            var quote=com.ainovel.app.modelconfig.AiSelectionContext.current();
            if(quote!=null&&quote.writingInput()!=null&&"SCENE".equals(quote.writingInput().purpose())) {
                if(!sceneGenerationService.hasFrozenInput())sceneGenerationService.requireBaseline(manuscript,quote.writingInput().frozen());
            }
            sceneGenerationSnapshotStore.ensureGenerationBaseline(manuscript, owner);
            Manuscript current = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow();
            return new GenerationSnapshot(current, ownerOf(current), current.getVersion(),current.getCurrentBranchId(),
                    sceneGenerationService.frozenPlanningHash(current),contents.readAll(current));
        }));
        var languageSelection=frozenLanguage==null?sceneGenerationService.languageSelection(snapshot.manuscript()):frozenLanguage;
        SceneGenerationService.GenerationResult generation = sceneGenerationService.generateSceneSection(
                snapshot.manuscript(),
                sceneId,
                snapshot.sections(),
                mode, languageSelection, admittedWorld, requireHistoricalInput
        );
        if(generation.needsReview()) {
            com.ainovel.app.aioperation.SceneGenerationProgress.report("待作者确认",2);
            return com.ainovel.app.aioperation.AiOperationExecutionContext.complete(() -> sceneOutcome(manuscriptId,sceneId,generation,null));
        }
        return com.ainovel.app.aioperation.AiOperationExecutionContext.complete(() ->
            Objects.requireNonNull(transactionTemplate.execute(status -> {
                var current=ownedManuscript(manuscriptId);var binding=worldBindings.lockCurrent(current);
                var manifest=generation.metadata()==null?null:generation.metadata().contextManifest();
                boolean outdated=manifest!=null&&manifest.isolationStamp()==null&&
                    (!worldBindings.same(manifest.worldBinding(),binding)||manifest.boundWorldId()!=null&&manifest.worldBinding()==null);
                if(manifest!=null&&manifest.worldContext()!=null)
                    outdated|=worldPreferences.read(com.ainovel.app.worldcontext.WorldContextPreferences.scope(current,sceneId),binding).revision()!=manifest.worldContext().selectionRevision();
                boolean inputChanged=current.getVersion()!=snapshot.version()||!Objects.equals(current.getCurrentBranchId(),snapshot.branchId())
                    ||!Objects.equals(snapshot.planningHash(),sceneGenerationService.planningHash(current));
                if(inputChanged)return sceneOutcome(manuscriptId,sceneId,new SceneGenerationService.GenerationResult(generation.html(),generation.metadata(),generation.candidateIds(),
                    generation.minHan(),generation.maxHan(),generation.actualHan(),true,"SCENE_INPUT_CHANGED"),null);
                if(outdated)return sceneOutcome(manuscriptId,sceneId,new SceneGenerationService.GenerationResult(generation.html(),generation.metadata(),generation.candidateIds(),
                    generation.minHan(),generation.maxHan(),generation.actualHan(),true,"WORLD_BINDING_CHANGED"),null);
                return sceneOutcome(manuscriptId,sceneId,generation,
                    saveGenerated(manuscriptId,sceneId,snapshot.version(),snapshot.branchId(),mode,generation,languageSelection,true));
            })));
    }

    private SceneOutcome sceneOutcome(UUID manuscriptId,UUID sceneId,SceneGenerationService.GenerationResult generation,ManuscriptDto saved) {
        return new SceneOutcome(saved==null?"NEEDS_REVIEW":"SAVED",manuscriptId,sceneId,generation.candidateIds(),generation.minHan(),generation.maxHan(),
                generation.actualHan(),generation.metadata()==null?0:Math.max(0,generation.metadata().attemptCount()-1),generation.reviewReason(),saved);
    }

    private ManuscriptDto saveGenerated(UUID manuscriptId,UUID sceneId,long expectedVersion,UUID expectedBranch,GenerationMode mode,
            SceneGenerationService.GenerationResult generation,com.ainovel.app.quality.language.LanguageStandard.Selection languageSelection,boolean automatic) {
        Manuscript manuscript=ownedManuscript(manuscriptId);
        requireVersion(manuscript,expectedVersion);
        if(!Objects.equals(manuscript.getCurrentBranchId(),expectedBranch)) throw new ApiStatusException(HttpStatus.CONFLICT,"MANUSCRIPT_BRANCH_CONFLICT");
        var context=generation.metadata()==null?null:generation.metadata().contextManifest();
        if(context!=null&&context.isolationStamp()!=null) narrativeContext.requireStamp(ownerOf(manuscript),context.isolationStamp());
        contents.writeScene(manuscript,sceneId,generation.html());
        manuscriptRepository.saveAndFlush(manuscript);
        Map<String,Object> manifest=new LinkedHashMap<>(buildGenerationManifest(sceneId,mode,generation.metadata()));
        manifest.putAll(languageSelection.provenance());
        manifest.put("lengthOutOfRange",generation.needsReview());
        if(generation.actualHan()!=null) {manifest.put("actualHan",generation.actualHan());manifest.put("minHan",generation.minHan());manifest.put("maxHan",generation.maxHan());}
        UUID versionId=sceneGenerationSnapshotStore.createGenerationSnapshot(manuscript,ownerOf(manuscript),sceneId,manifest);
        if(automatic) languageQuality.afterGeneration(ownerOf(manuscript),manuscript,sceneId,versionId,languageSelection);
        if(generation.metadata()!=null) materialEvidence.recordReferences(ownerOf(manuscript),manuscriptId,sceneId,manuscript.getCurrentBranchId(),versionId,generation.metadata().references());
        var run=sceneGenerationAttributionService.recordGeneration(manuscript,sceneId,versionId,mode,manifest,generation.html());
        if(generation.metadata()!=null&&generation.metadata().selection()!=null)
            sceneGenerationAttributionService.recordModelSelection(run.id(),generation.metadata().selection());
        eventPublisher.publishEvent(new com.ainovel.app.narrative.NarrativeSourceChanged(manuscriptId,null));
        if(automatic) eventPublisher.publishEvent(new com.ainovel.app.material.evidence.MaterialGenerationCompleted(ownerOf(manuscript).getId(),manuscriptId,sceneId,versionId));
        if(!generation.candidateIds().isEmpty()) {
            UUID id=generation.candidateIds().get(generation.candidateIds().size()-1);
            candidates.findById(id).ifPresent(c->{c.setStatus("APPLIED");c.setAdoptedVersion(manuscript.getVersion());});
        }
        return toDto(manuscript,new SceneGenerationRunSummaryDto(run.id(),run.generationVersionId(),run.status(),run.createdAt()));
    }

    private Manuscript ownedManuscript(UUID id) {
        var manuscript=manuscriptRepository.findWithStoryById(id).orElseThrow(()->new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return manuscript;
    }

    @Transactional(readOnly=true)
    public List<com.ainovel.app.narrative.SceneCandidateSummary> sceneCandidates(UUID manuscriptId,UUID sceneId,UUID branchId) {
        var manuscript=ownedManuscript(manuscriptId);
        if(!Objects.equals(manuscript.getCurrentBranchId(),branchId)) throw new ApiStatusException(HttpStatus.CONFLICT,"MANUSCRIPT_BRANCH_CONFLICT");
        return candidates.findSummaries(manuscriptId,branchId,sceneId);
    }

    private com.ainovel.app.narrative.NarrativeGenerationCandidate requireCandidate(UUID manuscriptId,UUID sceneId,UUID branchId,UUID candidateId) {
        var manuscript=ownedManuscript(manuscriptId);
        var candidate=candidates.findById(candidateId).orElseThrow(()->new BusinessException("候选不存在"));
        if(!candidate.getManuscriptId().equals(manuscriptId)||!candidate.getSceneId().equals(sceneId)||!candidate.getBranchId().equals(branchId))
            throw new ApiStatusException(HttpStatus.NOT_FOUND,"SCENE_CANDIDATE_NOT_FOUND");
        if(!Objects.equals(manuscript.getCurrentBranchId(),branchId)) throw new ApiStatusException(HttpStatus.CONFLICT,"MANUSCRIPT_BRANCH_CONFLICT");
        return candidate;
    }

    @Transactional(readOnly=true)
    public CandidateDetail sceneCandidate(UUID manuscriptId,UUID sceneId,UUID branchId,UUID candidateId) {
        var c=requireCandidate(manuscriptId,sceneId,branchId,candidateId);
        com.ainovel.app.worldcontext.WorldContextDtos.Selection world=null;
        if(c.getMetadataJson()!=null)try {
            var metadata=objectMapper.readTree(c.getMetadataJson()).path("generation").path("contextManifest").path("worldContext");
            if(!metadata.isMissingNode()&&!metadata.isNull())world=objectMapper.treeToValue(metadata,com.ainovel.app.worldcontext.WorldContextDtos.Selection.class);
        }catch(Exception e){throw new ApiStatusException(HttpStatus.CONFLICT,"SCENE_CANDIDATE_SOURCE_UNAVAILABLE");}
        return new CandidateDetail(c.getId(),c.getContent(),c.getStampJson(),c.getQualityStatus(),c.getSourceKind(),world);
    }
    public record CandidateDetail(UUID id,String content,String sourceFingerprint,String qualityStatus,String sourceKind,com.ainovel.app.worldcontext.WorldContextDtos.Selection worldContext) {}
    public record AdoptCandidateRequest(@jakarta.validation.constraints.NotNull Long expectedVersion,@jakarta.validation.constraints.NotNull UUID branchId) {}

    @Transactional
    public ManuscriptDto adoptSceneCandidate(UUID manuscriptId,UUID sceneId,UUID candidateId,AdoptCandidateRequest request) {
        var c=requireCandidate(manuscriptId,sceneId,request.branchId(),candidateId);
        var manuscript=ownedManuscript(manuscriptId);
        if("APPLIED".equals(c.getStatus())&&Objects.equals(c.getAdoptedVersion(),manuscript.getVersion())) return toDto(manuscript);
        requireVersion(manuscript,request.expectedVersion());
        if(!Objects.equals(c.getManuscriptVersion(),request.expectedVersion())) throw new ApiStatusException(HttpStatus.CONFLICT,"SCENE_CANDIDATE_VERSION_CONFLICT");
        if(!"GENERATED".equals(c.getSourceKind())||c.getMetadataJson()==null) throw new ApiStatusException(HttpStatus.CONFLICT,"SCENE_CANDIDATE_MANUAL_SAVE_REQUIRED");
        com.ainovel.app.narrative.NarrativeGenerationCandidateStore.Source source;
        try {source=objectMapper.readValue(c.getMetadataJson(),com.ainovel.app.narrative.NarrativeGenerationCandidateStore.Source.class);}
        catch(Exception e){throw new ApiStatusException(HttpStatus.CONFLICT,"SCENE_CANDIDATE_SOURCE_UNAVAILABLE");}
        boolean outside=c.getActualHan()<c.getMinHan()||c.getActualHan()>c.getMaxHan();
        var generation=new SceneGenerationService.GenerationResult(c.getContent(),source.generation(),List.of(c.getId()),c.getMinHan(),c.getMaxHan(),c.getActualHan(),outside,null);
        return saveGenerated(manuscriptId,sceneId,request.expectedVersion(),request.branchId(),source.mode(),generation,source.language(),false);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID manuscriptId, UUID sceneId, SectionUpdateRequest request) {
        return toDto(saveScene(manuscriptId, sceneId, request));
    }

    @Transactional
    public SceneContentDto updateScene(UUID manuscriptId, UUID sceneId, SectionUpdateRequest request) {
        return sceneDto(saveScene(manuscriptId, sceneId, request), sceneId);
    }

    @Transactional(readOnly = true)
    public SceneContentDto getScene(UUID manuscriptId, UUID sceneId) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return sceneDto(manuscript, sceneId);
    }

    @Transactional(readOnly = true)
    public List<ManuscriptSummaryDto> summaries(UUID outlineId) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return manuscriptRepository.findSummariesByOutlineId(outlineId);
    }

    private SceneContentDto sceneDto(Manuscript manuscript, UUID sceneId) {
        return new SceneContentDto(manuscript.getId(), manuscript.getCurrentBranchId(), sceneId,
                contents.readScene(manuscript, sceneId), manuscript.getVersion(), manuscript.getUpdatedAt());
    }

    private Manuscript saveScene(UUID manuscriptId, UUID sceneId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        requireVersion(manuscript, request.expectedVersion());
        if (request.expectedBranchId() != null && !request.expectedBranchId().equals(manuscript.getCurrentBranchId()))
            throw new ApiStatusException(HttpStatus.CONFLICT, "MANUSCRIPT_BRANCH_CHANGED");
        if (request.content() == null) throw new ApiStatusException(HttpStatus.BAD_REQUEST, "SCENE_CONTENT_REQUIRED");
        String previous = contents.readScene(manuscript, sceneId);
        if (Objects.equals(previous, request.content())) return manuscript;
        contents.writeScene(manuscript, sceneId, request.content());
        manuscriptRepository.saveAndFlush(manuscript);
        eventPublisher.publishEvent(new SceneContentEditedEvent(manuscriptId, sceneId, request.content()));
        return manuscript;
    }

    public List<SceneGenerationRunDto> listSceneGenerationRuns(UUID manuscriptId, UUID sceneId, int limit) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                .orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return sceneGenerationAttributionService.listRuns(manuscriptId, sceneId, limit);
    }

    @Transactional
    public SceneGenerationRunDto patchSceneGenerationFeedback(
            UUID manuscriptId,
            UUID sceneId,
            UUID runId,
            SceneGenerationFeedbackPatchRequest request
    ) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                .orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return sceneGenerationAttributionService.patchFeedback(manuscriptId, sceneId, runId, request);
    }

    public ManuscriptDto generateForScene(UUID sceneId) {
        UUID manuscriptId = transactionTemplate.execute(status -> manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("请先创建稿件")).getId());
        return generateForScene(manuscriptId, sceneId, GenerationMode.FAST);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID sectionId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("请先创建稿件"));
        return updateSection(manuscript.getId(), sectionId, request);
    }

    public List<CharacterChangeLogDto> listCharacterLogs(UUID manuscriptId) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow();
        accessGuard.assertOwner(ownerOf(manuscript));
        return mapLogs(readLogs(manuscript.getCharacterLogsJson()));
    }

    public List<CharacterChangeLogDto> listCharacterLogs(UUID manuscriptId, UUID characterId) {
        return listCharacterLogs(manuscriptId).stream()
                .filter(l -> l.characterId().equals(characterId))
                .toList();
    }

    public String generateDialogue(RefineRequest request) {
        return "【记忆对话】" + request.text();
    }

    private ManuscriptDto toDto(Manuscript manuscript) {
        return toDto(manuscript, null);
    }

    private ManuscriptDto toDto(Manuscript manuscript, SceneGenerationRunSummaryDto lastGenerationRun) {
        return new ManuscriptDto(
                manuscript.getId(),
                manuscript.getOutline().getId(),
                manuscript.getTitle(),
                manuscript.getWorldId(),
                contents.readAll(manuscript),
                lastGenerationRun,
                manuscript.getVersion(),
                manuscript.getUpdatedAt(),
                manuscript.getCurrentBranchId()
        );
    }

    private void requireVersion(Manuscript manuscript, Long expectedVersion) {
        if (expectedVersion == null || manuscript.getVersion() != expectedVersion) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "MANUSCRIPT_VERSION_CONFLICT");
        }
    }

    private record GenerationSnapshot(Manuscript manuscript, User owner, long version,UUID branchId,String planningHash,
                                      Map<String, String> sections) { }

    private Map<String, Object> buildGenerationManifest(
            UUID sceneId,
            GenerationMode mode,
            SceneGenerationService.GenerationMetadata generationMetadata
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("mode", (mode == null ? GenerationMode.FAST : mode).name().toLowerCase());
        manifest.put("sceneId", sceneId);
        manifest.put("generator", "SceneGenerationService");
        manifest.put("requestedAt", Instant.now());
        if (generationMetadata != null) {
            manifest.put("modelKey", generationMetadata.modelKey());
            manifest.put("promptVersion", generationMetadata.promptVersion());
            manifest.put("promptHash", generationMetadata.promptHash());
            manifest.put("attemptCount", generationMetadata.attemptCount());
            manifest.put("languageStandardVersion", generationMetadata.languageStandardVersion());
            SceneDraftContextManifest context = generationMetadata.contextManifest();
            if (context != null) {
                manifest.put("contextHash", context.contextHash());
                manifest.put("tokenBudget", context.tokenBudget());
                manifest.put("tokenUsed", context.tokenUsed());
                if (context.isolationStamp()!=null) {
                    var s=context.isolationStamp();
                    manifest.put("branchId",s.branchId());manifest.put("manuscriptVersion",s.manuscriptVersion());
                    manifest.put("canonRevision",s.canonRevision());manifest.put("contextRevision",s.contextRevision());
                    manifest.put("settingsRevision",s.settingsRevision());manifest.put("orderHash",s.orderHash());
                }
                manifest.put("sources", context.sources().stream().map(this::contextSourceMetadata).toList());
            }
        }
        return SceneGenerationManifest.sanitize(manifest);
    }

    private Map<String, Object> contextSourceMetadata(SceneDraftContextManifest.Source source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", source.sourceType());
        metadata.put("sourceId", source.sourceId());
        metadata.put("label", source.label());
        metadata.put("reason", source.reason());
        metadata.put("estimatedTokens", source.estimatedTokens());
        metadata.put("truncated", source.truncated());
        return metadata;
    }


    private List<Map<String, Object>> readLogs(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new ArrayList<>());
    }

    private List<CharacterChangeLogDto> mapLogs(List<Map<String, Object>> logs) {
        List<CharacterChangeLogDto> result = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            try {
                UUID id = UUID.fromString(String.valueOf(log.get("id")));
                UUID characterId = UUID.fromString(String.valueOf(log.get("characterId")));
                String summary = String.valueOf(log.get("summary"));
                Instant createdAt = Instant.parse(String.valueOf(log.get("createdAt")));
                result.add(new CharacterChangeLogDto(id, characterId, summary, createdAt));
            } catch (Exception invalid) {
                throw new ApiStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_DATA_INVALID");
            }
        }
        return result;
    }

    private String writeJson(Object obj) {
        return jsonColumnCodec.writeRequired(obj);
    }

    private User ownerOf(Manuscript manuscript) {
        return manuscript.getOutline().getStory().getUser();
    }

    private boolean isCurrentUserOwner(User user) {
        try {
            accessGuard.assertOwner(user);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}
