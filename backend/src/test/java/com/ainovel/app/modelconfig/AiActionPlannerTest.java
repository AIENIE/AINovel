package com.ainovel.app.modelconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiActionPlannerTest {
    private final ObjectMapper json=new ObjectMapper();
    private final AiActionPlanner planner=new AiActionPlanner(mock(JdbcTemplate.class),json);
    @Test void reviewedPlotAdoptionNeverQuotesOrInvokesSlopAndWholeDraftRepairIsRetired() {
        assertThat(planner.features("/v2/manuscripts/id/plot-quality-runs/run/apply-revision",json.createObjectNode())).isEmpty();
        assertThat(planner.candidate("POST","/v2/manuscripts/id/plot-quality-runs/run/apply-revision")).isFalse();
        for(String route:java.util.List.of("/v1/manuscripts/id/scenes/id/generate/operations?mode=fast", "/v1/g2-evaluations/id/samples"))
            assertThat(planner.features(route,json.createObjectNode())).doesNotContain("slop.revise");
        assertThat(AiFeatures.require("slop.revise").retired()).isTrue();
    }
    @Test void derivesStepsFromActualRouteAndBodyRatherThanClientFeatureFields() throws Exception {
        assertThat(planner.features("/v1/ai/chat",json.readTree("{\"featureKey\":\"free-feature\"}"))).containsExactly("generic.chat");
        assertThat(planner.features("/v1/manuscripts/id/scenes/id/generate/operations?mode=crafted",json.createObjectNode()))
                .startsWith("scene.crafted.draft","scene.crafted.length").contains("language.diagnose","slop.judge","plot.diagnose");
        assertThat(planner.features("/v1/manuscripts/id/scenes/id/assistant/turns",json.readTree("{\"intent\":\"AUTHOR_QA\"}")))
                .containsExactly("assistant.author_qa.plan","assistant.author_qa.final");
        assertThat(planner.features("/v1/story-cards/id/assistant/turns",json.readTree("{\"intent\":\"PROPOSE\"}")))
                .containsExactly("story_assistant.propose.plan","story_assistant.propose.final");
        assertThat(planner.features("/v1/worlds/id/modules/geography/fields/terrain/refine",json.createObjectNode())).containsExactly("world.geography.terrain.refine");
    }
    @Test void onlyAiMutationsAreCandidatesAndAutomaticGuidedRunIncludesLaterStages() {
        assertThat(planner.candidate("POST","/v1/story-cards/id/assistant/turns")).isTrue();
        assertThat(planner.candidate("GET","/v1/ai/chat")).isFalse();
        assertThat(planner.candidate("POST","/v1/admin/model-catalog/sync")).isFalse();
        assertThat(planner.features("/v1/creation-workflows/id/auto-run",json.createObjectNode())).contains("guided.premise","guided.world","guided.characters","guided.outline","guided.json_repair").doesNotContain("guided.outline_develop","guided.outline_rewrite");
        assertThat(planner.features("/v2/manuscripts/id/quality-runs/language/report/issues/issue/suggestions/operations",json.createObjectNode())).containsExactly("language.revise","language.review");
        assertThat(planner.features("/v2/manuscripts/id/scenes/id/citations",json.createObjectNode())).isEmpty();
    }
    @Test void automaticCreationQuotesAllItsChildJobsButManualDraftCreationDoesNot() throws Exception {
        assertThat(planner.candidate("POST","/v1/creation-workflows")).isTrue();
        assertThat(planner.features("/v1/creation-workflows",json.readTree("{\"autoRun\":true}"))).contains("guided.premise","guided.world","guided.characters","guided.outline","guided.json_repair").doesNotContain("guided.outline_develop","guided.outline_rewrite");
        assertThat(planner.features("/v1/creation-workflows",json.readTree("{\"autoRun\":false}"))).isEmpty();
    }
    @Test void outlineRewriteHasItsOwnConfiguredStepAndInvalidIntentIsAClientError() throws Exception {
        assertThat(planner.features("/v1/creation-workflows/id/steps/outline/directions/a/develop",json.readTree("{\"action\":\"rewrite\"}")))
                .containsExactly("guided.outline_rewrite","guided.json_repair");
        assertThatThrownBy(()->planner.features("/v1/story-cards/id/assistant/turns",json.readTree("{\"intent\":\"unknown\"}")))
                .isInstanceOf(com.ainovel.app.common.ApiStatusException.class).hasMessage("AI_ACTION_INVALID");
    }

    @Test void automaticRunDoesNotQuoteCompletedStagesOrManualOutlineRewrites() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var planner=new AiActionPlanner(jdbc,json);var user=new com.ainovel.app.user.User();user.setId(java.util.UUID.randomUUID());
        var id=java.util.UUID.randomUUID();var row=new java.util.LinkedHashMap<String,Object>();
        row.put("id",AiModelConfigurationService.bytes(id));row.put("current_step","OUTLINE");row.put("steps_json","{\"OUTLINE\":{\"candidates\":[{}],\"outlinePhase\":\"DIRECTION_SELECTION\"}}");
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("select r.*"),org.mockito.ArgumentMatchers.any(byte[].class),org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenAnswer(i->new java.util.ArrayList<>(java.util.List.of(new java.util.LinkedHashMap<>(row))));
        var result=planner.plan(user,"/v1/creation-workflows/"+id+"/auto-run",json.createObjectNode());
        assertThat(result.steps()).extracting(AiActionPlanner.Step::featureKey).containsExactly("guided.outline_expand","guided.json_repair");
        row.put("steps_json","{\"OUTLINE\":{\"candidates\":[{}],\"outlinePhase\":\"OUTLINE_PREVIEW\"}}");
        assertThat(planner.plan(user,"/v1/creation-workflows/"+id+"/auto-run",json.createObjectNode()).steps()).isEmpty();
    }

}
