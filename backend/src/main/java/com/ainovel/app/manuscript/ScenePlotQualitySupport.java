package com.ainovel.app.manuscript;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.PlotQualityRequest;
import com.ainovel.app.quality.PlotQualityService;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScenePlotQualitySupport {
    public SlopQualityRequest buildIsolatedQualityRequest(Manuscript manuscript, SceneGenerationContext scene,
                                                         String context, String candidate) {
        return new SlopQualityRequest(manuscript.getOutline().getStory().getId(),manuscript.getId(),scene.sceneId(),
                "当前作品","未指定","沉浸、连贯","当前章节","当前场景","",context,"","",candidate,"h2_generation_gate");
    }

    public void recordIsolatedPlotQuality(User owner, Manuscript manuscript, SceneGenerationContext scene, com.ainovel.app.manuscript.context.CompiledSceneDraftContext context, String text) {
        try {
            plotQualityService.analyze(owner,new PlotQualityRequest(manuscript.getOutline().getStory().getId(),manuscript.getId(),scene.sceneId(),
                    "当前作品","未指定","沉浸、连贯","当前章节",scene.chapterOrder(),"当前场景",scene.sceneOrder(),"","",context.content(),"",text,context.manifest().isolationStamp(),context.content()));
        } catch (RuntimeException ignored) { }
    }
    @Autowired
    private PlotQualityService plotQualityService;
    @Autowired
    private StyleContextProvider styleContextProvider;

    public SlopQualityRequest buildQualityRequest(Manuscript manuscript,
                                                  Story story,
                                                  SceneGenerationContext scene,
                                                  String previousContext,
                                                  String characterContext,
                                                  String candidateText) {
        return buildQualityRequest(manuscript, story, scene, previousContext, characterContext, candidateText,
                "generation_gate");
    }

    public SlopQualityRequest buildQualityRequest(Manuscript manuscript,
                                                  Story story,
                                                  SceneGenerationContext scene,
                                                  String previousContext,
                                                  String characterContext,
                                                  String candidateText,
                                                  String analysisMode) {
        return new SlopQualityRequest(
                story.getId(),
                manuscript.getId(),
                scene.sceneId(),
                safeText(story.getTitle(), "未命名故事"),
                safeText(story.getGenre(), "未指定"),
                safeText(story.getTone(), "沉浸、连贯"),
                scene.chapterTitle(),
                scene.sceneTitle(),
                scene.sceneSummary(),
                previousContext,
                characterContext,
                styleContextProvider.buildSlopContext(story),
                candidateText,
                analysisMode
        );
    }

    public void recordPlotQuality(User owner,
                                  Manuscript manuscript,
                                  Story story,
                                  SceneGenerationContext scene,
                                  String outlinePlanning,
                                  String previousContext,
                                  String characterContext,
                                  String acceptedText) {
        try {
            plotQualityService.analyze(owner, new PlotQualityRequest(
                    story.getId(),
                    manuscript.getId(),
                    scene.sceneId(),
                    safeText(story.getTitle(), "未命名故事"),
                    safeText(story.getGenre(), "未指定"),
                    safeText(story.getTone(), "沉浸、连贯"),
                    scene.chapterTitle(),
                    scene.chapterOrder(),
                    scene.sceneTitle(),
                    scene.sceneOrder(),
                    scene.sceneSummary(),
                    outlinePlanning,
                    previousContext,
                    characterContext,
                    acceptedText
            ));
        } catch (RuntimeException ignored) {
        }
    }

    private String safeText(String text, String fallback) {
        if (text == null) return fallback;
        String normalized = text.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
