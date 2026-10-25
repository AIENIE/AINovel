package com.ainovel.app.modelconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ainovel.app.worldassistant.*;
import com.ainovel.app.settings.dto.WorldPromptTemplatesResponse;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiActionPlannerTest {
    private final ObjectMapper json=new ObjectMapper();
    private final AiActionPlanner planner=new AiActionPlanner(mock(JdbcTemplate.class),json);
    @Test void publicationIsFreeAndEntryAiHasSeparateFeatures() {
        assertThat(planner.features("/v1/worlds/id/publish",json.createObjectNode())).isEmpty();
        assertThat(planner.features("/v1/worlds/id/entries/entry/generate/operations",json.createObjectNode())).containsExactly("world.entry.generate");
        assertThat(planner.features("/v1/worlds/id/entries/entry/refine",json.createObjectNode())).containsExactly("world.entry.refine");
        assertThat(planner.features("/v1/worlds/id/publish/operations",json.createObjectNode())).hasSize(9).doesNotContain("world.entry.generate");
    }
    @Test void worldDiscussionHasAtMostTwoStepsAndOverviewAndEntryRefineAreSingleCalls() throws Exception {
        for(String intent:java.util.List.of("discuss","propose","refine"))assertThat(planner.features("/v1/worlds/id/assistant/turns",json.readTree("{\"intent\":\""+intent.toUpperCase()+"\"}"))).containsExactly("world_assistant."+intent+".plan","world_assistant."+intent+".final");
        assertThat(planner.features("/v1/worlds/id/entries/entry/refine/operations",json.createObjectNode())).containsExactly("world.entry.refine");
        assertThat(planner.features("/v1/worlds/id/overview/integrate/operations",json.createObjectNode())).containsExactly("world.overview.integrate");
        assertThat(planner.candidate("POST","/v1/worlds/id/assistant/turns")).isTrue();
    }
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
    @Test void automaticCreationQuotesOnlyUntilWorldPublicationAndManualDraftCreationDoesNot() throws Exception {
        assertThat(planner.candidate("POST","/v1/creation-workflows")).isTrue();
        assertThat(planner.features("/v1/creation-workflows",json.readTree("{\"autoRun\":true}"))).containsExactly("guided.premise","guided.world","guided.json_repair");
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

    @Test void worldQuoteIncludesProtocolTemplatesTopicAndCandidateWithoutChangingOtherHashes() {
        var jdbc=mock(JdbcTemplate.class);var planner=new AiActionPlanner(jdbc,json);var assistant=mock(WorldAssistantService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(planner,"worldAssistant",assistant);
        var user=new com.ainovel.app.user.User();user.setId(UUID.randomUUID());var world=UUID.randomUUID();var session=UUID.randomUUID();
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("select w.*"),org.mockito.ArgumentMatchers.any(byte[].class),org.mockito.ArgumentMatchers.any(byte[].class)))
            .thenAnswer(i->new ArrayList<>(List.of(new LinkedHashMap<String,Object>(Map.of("name","固定世界")))));
        String path="/v1/worlds/"+world+"/assistant/turns";var body=json.createObjectNode().put("intent","PROPOSE");
        var content=new com.ainovel.app.world.WorldDraftDtos.Content("固定世界","",List.of(),"","",List.of(),List.of(),Map.of(),null);
        var snapshot=new WorldAssistantContext.Snapshot(world,1,"hash",content,List.of(),List.of());
        var base=new WorldAssistantService.Frozen(world,session,"PROPOSE","整理",null,null,null,snapshot,List.of(),new WorldPromptTemplatesResponse(Map.of(),Map.of(),"",Map.of()),null,WorldAssistantService.PROTOCOL);
        org.mockito.Mockito.when(assistant.validateQuote(org.mockito.ArgumentMatchers.eq(user),org.mockito.ArgumentMatchers.eq(world),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq(body),org.mockito.ArgumentMatchers.eq(false))).thenReturn(base);
        var original=planner.plan(user,path,body).businessHash();var values=new ArrayList<WorldAssistantService.Frozen>();
        values.add(new WorldAssistantService.Frozen(world,session,"PROPOSE","整理",null,null,null,snapshot,List.of(),base.templates(),null,"world-assistant-v1"));
        values.add(new WorldAssistantService.Frozen(world,session,"PROPOSE","整理",null,null,null,snapshot,List.of(),new WorldPromptTemplatesResponse(Map.of(),Map.of(),"",Map.of("propose","新模板")),null,WorldAssistantService.PROTOCOL));
        values.add(new WorldAssistantService.Frozen(world,session,"PROPOSE","整理",null,null,null,snapshot,List.of(new WorldAssistantService.History(UUID.randomUUID(),"话题","未确认设想")),base.templates(),null,WorldAssistantService.PROTOCOL));
        var candidate=new WorldAssistantService.ProposalData();candidate.candidate=json.createObjectNode().put("title","作者编辑的候选");
        values.add(new WorldAssistantService.Frozen(world,session,"PROPOSE","整理",UUID.randomUUID(),candidate,null,snapshot,List.of(),base.templates(),null,WorldAssistantService.PROTOCOL));
        for(var changed:values) {
            org.mockito.Mockito.when(assistant.validateQuote(user,world,null,body,false)).thenReturn(changed);
            assertThat(planner.plan(user,path,body).businessHash()).isNotEqualTo(original);
        }
        // Without assistant input, preserve the exact historic concatenation and its hash.
        var generic=planner.plan(user,"/v1/ai/chat",json.createObjectNode());
        assertThat(generic.businessHash()).isEqualTo(com.ainovel.app.quality.language.LanguageProjection.hash("[][generic.chat]nullnull"));
    }

}
