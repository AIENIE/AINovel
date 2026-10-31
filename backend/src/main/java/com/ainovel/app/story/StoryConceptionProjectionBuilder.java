package com.ainovel.app.story;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.story.dto.CharacterRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.ainovel.app.story.dto.StoryUpdateRequest;
import com.ainovel.app.story.model.SceneType;
import org.springframework.stereotype.Service;
import java.util.*;

/** Validate generated content and project it without adding narrative material. */
@Service
public class StoryConceptionProjectionBuilder {
    public Projection build(StoryCreateRequest request, Map<String, Object> generated) {
        var story = new StoryUpdateRequest(optional(generated.get("title"), request.title()),
                text(generated.get("synopsis")), optional(generated.get("genre"), request.genre()),
                optional(generated.get("tone"), request.tone()), null, request.worldId());
        var characters = objects(generated.get("characters"), true).stream().map(item -> new CharacterRequest(
                text(item.get("name")), optional(item.get("synopsis")), optional(item.get("details")),
                optional(item.get("relationships")))).toList();
        var skeleton = object(generated.get("skeleton"));
        var plot = new LinkedHashMap<String,Object>();
        plot.put("corePromise", text(skeleton.get("corePromise")));
        plot.put("centralQuestion", text(skeleton.get("centralQuestion")));
        plot.put("hiddenTruth", text(skeleton.get("hiddenTruth")));
        plot.put("readerMisdirect", text(skeleton.get("readerExpectation")));
        plot.put("stakes", optional(skeleton.get("stakes")));
        var twists = objects(generated.get("twistOptions"), true);
        for (var twist : twists) {
            for (var key : List.of("id", "label", "summary", "revealBeat", "revealTiming", "earlyRevealRisk", "payoff")) text(twist.get(key));
            strings(twist.get("setupPoints")); strings(twist.get("misdirectionPoints"));
        }
        plot.put("twistOptions", twists);
        var seeds = objects(generated.get("foreshadowSeeds"), false);
        var foreshadows = new ArrayList<Map<String,Object>>();
        for (var seed : seeds) {
            foreshadows.add(Map.of("id", text(seed.get("entryKey")), "clue", text(seed.get("setup")),
                    "disguise", optional(seed.get("coverLayer")), "payoff", text(seed.get("payoff")),
                    "revealTiming", optional(seed.get("revealTiming"))));
        }
        plot.put("foreshadowPlans", foreshadows);
        var meme = object(generated.get("memeStrategy"));
        plot.put("memeStrategy", Map.of("reference", optional(meme.get("sourceDomain")),
                "purpose", optional(meme.get("useCase")), "usage", optional(meme.get("naturalVersion")),
                "caution", optional(meme.get("immersionRisk"))));
        var outline = object(generated.get("outlineSuggestion"));
        text(outline.get("title"));
        var chapters = objects(outline.get("chapters"), true);
        var beats = new ArrayList<Map<String,Object>>();
        for (int i=0; i<chapters.size(); i++) {
            var chapter = chapters.get(i);
            beats.add(Map.of("id", "beat-"+(i+1), "label", text(chapter.get("title")), "summary", text(chapter.get("summary"))));
            for (var scene : objects(chapter.get("scenes"), true)) {
                text(scene.get("title")); text(scene.get("summary"));
                var planning = object(scene.get("planning"));
                if (SceneType.parse(planning.get("sceneType")).isEmpty()) throw invalid();
            }
        }
        plot.put("beats", beats);
        // These collections are optional creative suggestions; their absence supplies no narrative.
        for (var key : List.of("lorebookSeeds", "graphSeeds")) {
            plot.put(key, generated.containsKey(key) ? objects(generated.get(key), false) : List.of());
        }
        generated.put("plotPlanning", plot);
        generated.put("outlineSeed", outline);
        return new Projection(story, characters, plot, outline);
    }

    private static String text(Object value) {
        if (!(value instanceof String s) || s.isBlank()) throw invalid();
        return s;
    }
    private static String optional(Object value) { return optional(value, ""); }
    private static String optional(Object value, String authorValue) {
        if (value == null) return authorValue;
        if (!(value instanceof String s)) throw invalid();
        return s.isBlank() && authorValue != null ? authorValue : s;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) throw invalid();
        return (Map<String,Object>) map;
    }
    private static List<Map<String,Object>> objects(Object value, boolean nonempty) {
        if (!(value instanceof List<?> list) || nonempty && list.isEmpty()) throw invalid();
        return list.stream().map(StoryConceptionProjectionBuilder::object).toList();
    }
    private static void strings(Object value) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) throw invalid();
    }
    private static BusinessException invalid() { return new BusinessException("AI_INITIALIZATION_INVALID_RESULT"); }
    public record Projection(StoryUpdateRequest storyUpdate, List<CharacterRequest> characterRequests,
            Map<String,Object> plotPlanning, Map<String,Object> outlineSeed) {}
}
