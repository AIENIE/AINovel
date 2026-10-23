package com.ainovel.app.aioperation;

import com.ainovel.app.manuscript.GenerationMode;
import com.ainovel.app.manuscript.ManuscriptService;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.PlotQualityService;
import com.ainovel.app.quality.SlopDiagnosticService;
import com.ainovel.app.quality.SlopDriftService;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.OutlineService;
import com.ainovel.app.story.StoryService;
import com.ainovel.app.story.dto.OutlineChapterGenerateRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class CoreAiOperationHandler implements AiOperationHandler {
    public static final String TYPE = "AINOVEL_LONG_TASK";

    private final StoryService stories;
    private final OutlineService outlines;
    private final WorldTaskService worldTasks;
    private final ManuscriptService manuscripts;
    private final ResourceAccessGuard accessGuard;
    private final SlopDiagnosticService slop;
    private final SlopDriftService drift;
    private final PlotQualityService plot;

    public CoreAiOperationHandler(StoryService stories, OutlineService outlines, WorldTaskService worldTasks,
                                  ManuscriptService manuscripts, ResourceAccessGuard accessGuard,
                                  SlopDiagnosticService slop, SlopDriftService drift, PlotQualityService plot) {
        this.stories = stories;
        this.outlines = outlines;
        this.worldTasks = worldTasks;
        this.manuscripts = manuscripts;
        this.accessGuard = accessGuard;
        this.slop = slop;
        this.drift = drift;
        this.plot = plot;
    }

    @Override public String type() { return TYPE; }

    @Override
    public Object execute(AiOperationExecution execution) throws Exception {
        CorePayload payload = execution.objectMapper().readValue(execution.payloadJson(), CorePayload.class);
        return switch (payload.action()) {
            case "CONCEPTION" -> {
                execution.progress().step("生成故事构思与初始角色", 0, 3);
                StoryCreateRequest request = execution.objectMapper().treeToValue(payload.request(), StoryCreateRequest.class);
                Object result = stories.conception(execution.user(), request);
                execution.progress().step("保存故事资产", 2, 3);
                yield result;
            }
            case "OUTLINE_CHAPTER" -> {
                execution.progress().step("生成章节大纲", 0, 3);
                OutlineChapterGenerateRequest request = execution.objectMapper().treeToValue(
                        payload.request(), OutlineChapterGenerateRequest.class);
                Object result = outlines.addGeneratedChapter(payload.primaryId(), request);
                execution.progress().step("保存章节与场景", 2, 3);
                yield result;
            }
            case "WORLD_PUBLISH", "WORLD_MODULE", "WORLD_ENTRY" -> worldTasks.execute(execution, payload);
            case "SCENE_GENERATION" -> {
                if(payload.sceneGenerationVersion()==null) throw new com.ainovel.app.common.ApiStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,"SCENE_LEGACY_RECOVERY_REQUIRED");
                if(payload.request()==null || payload.request().isNull()) throw new com.ainovel.app.ai.AiResultUncertainException(
                        new IllegalStateException("LANGUAGE_PROFILE_MISSING_FROM_HISTORICAL_TASK"));
                com.ainovel.app.quality.language.LanguageStandard.Selection language;
                com.ainovel.app.world.WorldBindingService.Frozen world=null;
                try {
                    if(payload.sceneGenerationVersion()>=3) {
                        var input=execution.objectMapper().treeToValue(payload.request(),com.ainovel.app.manuscript.SceneGenerationService.Admission.class);
                        language=input.language();world=input.world();
                        if(world==null||language==null)throw new IllegalStateException("WORLD_TASK_BASELINE_REQUIRED");
                    } else language=execution.objectMapper().treeToValue(payload.request(),com.ainovel.app.quality.language.LanguageStandard.Selection.class);
                }
                catch(Exception unavailable) { throw new com.ainovel.app.ai.AiResultUncertainException(unavailable); }
                try(var stage=SceneGenerationProgress.install((label,completed)->execution.progress().step(label,completed,3))) {
                    yield manuscripts.generateSceneOutcome(payload.primaryId(),payload.secondaryId(),GenerationMode.valueOf(payload.mode()),language,world,payload.sceneGenerationVersion()<3);
                }
            }
            case "SLOP_DIAGNOSIS" -> {
                Manuscript manuscript = accessGuard.requireOwnedManuscript(payload.primaryId(), execution.user());
                execution.progress().step("执行文本 Slop 诊断", 0, 2);
                var frozen=payload.request()==null?null:execution.objectMapper().treeToValue(payload.request(),com.ainovel.app.quality.SlopEvidence.ReviewData.class);
                var run = slop.analyzeFrozen(execution.user(), manuscript, payload.secondaryId(),frozen);
                yield Map.of("resourceId", run.getId());
            }
            case "SLOP_DRIFT" -> {
                Manuscript manuscript = accessGuard.requireOwnedManuscript(payload.primaryId(), execution.user());
                execution.progress().step("分析长篇文本漂移", 0, 2);
                var run = drift.analyze(execution.user(), manuscript);
                yield Map.of("resourceId", run.getId());
            }
            case "PLOT_DIAGNOSIS" -> {
                Manuscript manuscript = accessGuard.requireOwnedManuscript(payload.primaryId(), execution.user());
                execution.progress().step("执行剧情质量诊断", 0, 2);
                var run = plot.analyzeScene(execution.user(), manuscript, payload.secondaryId());
                yield Map.of("resourceId", run.getId());
            }
            case "PLOT_REVISION" -> {
                Manuscript manuscript = accessGuard.requireOwnedManuscript(payload.primaryId(), execution.user());
                execution.progress().step("生成剧情修订候选", 0, 2);
                var run = plot.generateRevisionCandidate(execution.user(), manuscript, payload.secondaryId());
                yield Map.of("resourceId", run.getId());
            }
            default -> throw new IllegalArgumentException("Unsupported core AI action: " + payload.action());
        };
    }

    public record CorePayload(String action,UUID primaryId,UUID secondaryId,String mode,JsonNode request,Integer sceneGenerationVersion) {
        public CorePayload(String action,UUID primaryId,UUID secondaryId,String mode,JsonNode request) {this(action,primaryId,secondaryId,mode,request,null);}
    }
}
