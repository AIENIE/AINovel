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
import com.ainovel.app.world.WorldService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class CoreAiOperationHandler implements AiOperationHandler {
    public static final String TYPE = "AINOVEL_LONG_TASK";

    private final StoryService stories;
    private final OutlineService outlines;
    private final WorldService worlds;
    private final ManuscriptService manuscripts;
    private final ResourceAccessGuard accessGuard;
    private final SlopDiagnosticService slop;
    private final SlopDriftService drift;
    private final PlotQualityService plot;

    public CoreAiOperationHandler(StoryService stories, OutlineService outlines, WorldService worlds,
                                  ManuscriptService manuscripts, ResourceAccessGuard accessGuard,
                                  SlopDiagnosticService slop, SlopDriftService drift, PlotQualityService plot) {
        this.stories = stories;
        this.outlines = outlines;
        this.worlds = worlds;
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
            case "WORLD_PUBLISH" -> {
                var preview = worlds.preview(payload.primaryId());
                int total = Math.max(2, preview.modulesToGenerate().size() + 2);
                execution.progress().step("准备世界观发布", 0, total);
                worlds.publish(payload.primaryId());
                int completed = 1;
                for (String module : preview.modulesToGenerate()) {
                    execution.progress().step("生成世界模块：" + module, completed, total);
                    worlds.generateModule(payload.primaryId(), module);
                    completed++;
                }
                execution.progress().step("完成世界观发布", total - 1, total);
                yield worlds.get(payload.primaryId());
            }
            case "WORLD_MODULE" -> {
                execution.progress().step("生成世界模块：" + payload.mode(), 0, 2);
                Object result = worlds.generateModule(payload.primaryId(), payload.mode());
                execution.progress().step("保存世界模块", 1, 2);
                yield result;
            }
            case "SCENE_GENERATION" -> {
                execution.progress().step("生成场景正文", 0, 5);
                Object result = manuscripts.generateForScene(payload.primaryId(), payload.secondaryId(),
                        GenerationMode.valueOf(payload.mode()));
                execution.progress().step("保存正文与质量结果", 4, 5);
                yield result;
            }
            case "SLOP_DIAGNOSIS" -> {
                Manuscript manuscript = accessGuard.requireOwnedManuscript(payload.primaryId(), execution.user());
                execution.progress().step("执行文本 Slop 诊断", 0, 2);
                var run = slop.analyzeScene(execution.user(), manuscript, payload.secondaryId());
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

    public record CorePayload(String action, UUID primaryId, UUID secondaryId, String mode, JsonNode request) {}
}
