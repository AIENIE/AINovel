package com.ainovel.app.manuscript;

import com.ainovel.app.material.MaterialRetrievalService;
import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
import com.ainovel.app.manuscript.context.CompiledSceneDraftContext;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.prompt.PromptAssemblyService;
import com.ainovel.app.prompt.PromptReference;
import com.ainovel.app.prompt.SceneGenerationPromptInput;
import com.ainovel.app.quality.SlopPatternSamplingService;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SceneGenerationPromptBuilder {
    @Autowired
    private PromptAssemblyService promptAssemblyService;
    @Autowired
    private MaterialRetrievalService materialRetrievalService;
    @Autowired
    private SlopPatternSamplingService slopPatternSamplingService;

    public AssembledPrompt build(User owner,
                                 Story story,
                                 SceneGenerationContext scene,
                                 String characterContext,
                                 String previousContext,
                                 String previousDraft,
                                 int previousCount,
                                 int attempt,
                                 int minSectionHan,
                                 int maxSectionHan) {
        return build(owner, story, scene, characterContext, previousContext,
                previousDraft, previousCount, attempt, minSectionHan, maxSectionHan,
                GenerationMode.FAST);
    }

    public AssembledPrompt build(User owner,
                                 Story story,
                                 SceneGenerationContext scene,
                                 String characterContext,
                                 String previousContext,
                                 String previousDraft,
                                 int previousCount,
                                 int attempt,
                                 int minSectionHan,
                                 int maxSectionHan,
                                 GenerationMode mode) {
        return build(owner, story, scene, characterContext, previousContext, previousDraft, previousCount,
                attempt, minSectionHan, maxSectionHan, mode, null);
    }

    public AssembledPrompt build(User owner,
                                 Story story,
                                 SceneGenerationContext scene,
                                 String characterContext,
                                 String previousContext,
                                 String previousDraft,
                                 int previousCount,
                                 int attempt,
                                 int minSectionHan,
                                 int maxSectionHan,
                                 GenerationMode mode,
                                 CompiledSceneDraftContext compiledContext) {
        return build(owner,story,scene,characterContext,previousContext,previousDraft,previousCount,attempt,minSectionHan,maxSectionHan,mode,compiledContext,null);
    }
    public AssembledPrompt build(User owner,Story story,SceneGenerationContext scene,String characterContext,String previousContext,
            String previousDraft,int previousCount,int attempt,int minSectionHan,int maxSectionHan,GenerationMode mode,
            CompiledSceneDraftContext compiledContext,List<PromptReference> frozenReferences){
        String retryInstruction = "";
        boolean isolated=compiledContext != null && compiledContext.isolated();
        if (attempt > 1) {
            String direction = previousCount < minSectionHan ? "扩写" : "压缩";
            retryInstruction = """

                    这是第 %d 次重试。上一版字数为 %d 汉字，请在保持剧情一致的前提下进行%s，并严格输出 %d-%d 汉字。
                    上一版草稿（可重写，不要直接复制）：
                    %s
                    """.formatted(attempt, previousCount, direction, minSectionHan, maxSectionHan, isolated ? previousDraft : truncate(previousDraft, 1200));
        }
        SceneGenerationPromptInput input = new SceneGenerationPromptInput(
                isolated ? "当前作品" : safeText(story == null ? null : story.getTitle(), "未命名故事"),
                isolated ? "未指定" : safeText(story == null ? null : story.getGenre(), "未指定"),
                isolated ? "沉浸、连贯" : safeText(story == null ? null : story.getTone(), "沉浸、连贯"),
                isolated ? "" : safeText(story == null ? null : story.getSynopsis(), ""),
                isolated ? "当前章节" : scene.chapterTitle(),
                isolated ? "" : scene.chapterSummary(),
                scene.chapterOrder(),
                isolated ? "当前场景" : scene.sceneTitle(),
                isolated ? "" : scene.sceneSummary(),
                scene.sceneOrder(),
                compiledContext == null ? characterContext : "使用下方 SCENE_DRAFT_CONTEXT 中已采用的人物资料。",
                isolated ? "前文与知情边界仅见下方受控上下文。" : previousContext,
                isolated ? List.of() : frozenReferences==null?materialReferences(owner, story, scene):frozenReferences,
                recentAvoidExpressions(isolated ? compiledContext.content() : previousContext),
                minSectionHan,
                maxSectionHan,
                retryInstruction,
                128000,
                compiledContext == null ? "" : compiledContext.content(),
                compiledContext == null ? "" : compiledContext.compilerVersion(),
                compiledContext == null ? "" : compiledContext.contextHash()
        );

        if (mode == GenerationMode.CRAFTED) {
            UUID sceneId = scene.sceneId();
            List<String> negativePatterns = slopPatternSamplingService.sample(sceneId);
            return promptAssemblyService.assembleWithCreativeConstraints(input, negativePatterns, scene.sceneOrder());
        }
        return promptAssemblyService.assembleSceneDraft(input);
    }

    private List<PromptReference> materialReferences(User owner, Story story, SceneGenerationContext scene) {
        String query = String.join(" ",
                safeText(story == null ? null : story.getTitle(), ""),
                safeText(story == null ? null : story.getGenre(), ""),
                scene.chapterTitle(),
                scene.chapterSummary(),
                scene.sceneTitle(),
                scene.sceneSummary()
        ).trim();
        if (query.isBlank()) {
            return List.of();
        }
        List<MaterialSearchResultDto> results = materialRetrievalService.search(owner, new MaterialSearchRequest(query, 8));
        List<PromptReference> references = new ArrayList<>();
        for (MaterialSearchResultDto result : results) {
            references.add(new PromptReference(
                    "material",
                    result.materialId(),
                    result.title(),
                    result.snippet(),
                    result.score(),
                    result.chunkSeq() == null ? 0 : result.chunkSeq()
            ));
        }
        return references;
    }

    private List<String> recentAvoidExpressions(String previousContext) {
        List<String> candidates = List.of(
                "嘴角微微上扬",
                "空气仿佛凝固",
                "时间像是停了下来",
                "眼神变得坚定",
                "心中涌起一股",
                "说不出的感觉",
                "命运的齿轮",
                "一切都在此刻改变",
                "仿佛整个世界"
        );
        List<String> avoid = new ArrayList<>();
        for (String candidate : candidates) {
            if (previousContext.contains(candidate)) {
                avoid.add(candidate);
            }
        }
        return avoid.size() > 8 ? avoid.subList(0, 8) : avoid;
    }

    private String safeText(String text, String fallback) {
        if (text == null) return fallback;
        String normalized = text.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }
}
