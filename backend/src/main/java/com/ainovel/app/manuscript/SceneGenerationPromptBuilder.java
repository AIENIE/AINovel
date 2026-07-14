package com.ainovel.app.manuscript;

import com.ainovel.app.material.MaterialRetrievalService;
import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
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
        String retryInstruction = "";
        if (attempt > 1) {
            String direction = previousCount < minSectionHan ? "扩写" : "压缩";
            retryInstruction = """

                    这是第 %d 次重试。上一版字数为 %d 汉字，请在保持剧情一致的前提下进行%s，并严格输出 %d-%d 汉字。
                    上一版草稿（可重写，不要直接复制）：
                    %s
                    """.formatted(attempt, previousCount, direction, minSectionHan, maxSectionHan, truncate(previousDraft, 1200));
        }
        SceneGenerationPromptInput input = new SceneGenerationPromptInput(
                safeText(story == null ? null : story.getTitle(), "未命名故事"),
                safeText(story == null ? null : story.getGenre(), "未指定"),
                safeText(story == null ? null : story.getTone(), "沉浸、连贯"),
                safeText(story == null ? null : story.getSynopsis(), ""),
                scene.chapterTitle(),
                scene.chapterSummary(),
                scene.chapterOrder(),
                scene.sceneTitle(),
                scene.sceneSummary(),
                scene.sceneOrder(),
                characterContext,
                previousContext,
                materialReferences(owner, story, scene),
                recentAvoidExpressions(previousContext),
                minSectionHan,
                maxSectionHan,
                retryInstruction,
                128000
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
