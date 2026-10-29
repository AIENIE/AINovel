package com.ainovel.app.story;

import com.ainovel.app.story.dto.StoryCreateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class StoryConceptionProjectionBuilderTest {
    @Test
    void validProjectionUsesOnlyGeneratedContentAndAuthorMetadata() throws Exception {
        Map<String,Object> result = new ObjectMapper().readValue("""
                {"synopsis":"真实生成的梗概","characters":[{"name":"许安","details":"原始细节"}],
                 "skeleton":{"corePromise":"承诺","centralQuestion":"问题","hiddenTruth":"真相","readerExpectation":"误判"},
                 "twistOptions":[{"id":"a","label":"第一方案","summary":"反转","setupPoints":[],"misdirectionPoints":[],
                   "revealBeat":"揭示","revealTiming":"末章","earlyRevealRisk":"风险","payoff":"回收"}],
                 "foreshadowSeeds":[],"memeStrategy":{},"outlineSuggestion":{"title":"实际大纲","chapters":[
                   {"title":"第一章","summary":"章摘要","scenes":[{"title":"门前","summary":"场景摘要","planning":{"sceneType":"action"}}]}]}}
                """, new TypeReference<>() {});
        var projection = new StoryConceptionProjectionBuilder().build(
                new StoryCreateRequest("作者标题","作者输入","类型","基调",null,Map.of()), result);
        assertEquals("作者标题", projection.storyUpdate().title());
        assertEquals(1, projection.characterRequests().size());
        assertEquals("许安", projection.characterRequests().getFirst().name());
        assertEquals("原始细节", projection.characterRequests().getFirst().details());
        assertEquals("", projection.plotPlanning().get("stakes"));
        assertFalse(projection.plotPlanning().containsKey("confidence"));
        assertEquals(result.get("outlineSuggestion"), projection.outlineSeed());
        assertFalse(result.containsKey("fallbackFields"));
    }
}
