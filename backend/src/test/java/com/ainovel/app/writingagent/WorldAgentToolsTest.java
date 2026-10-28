package com.ainovel.app.writingagent;

import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.world.WorldDraftDtos.*;
import com.ainovel.app.worldcontext.WorldContextDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class WorldAgentToolsTest {
    private final ObjectMapper json=new ObjectMapper();
    private final WorldAgentTools tools=new WorldAgentTools(json,null,null);
    private static final UUID WORLD=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIRST=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SECOND=UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID RELATION=UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test void searchesChineseAliasesWithoutMergingSameNamedEntriesAndUsesStableOrdering() {
        var world=freeze(List.of(entry(SECOND,"PLACE","旧城","","第二个旧城",List.of("白塔")),entry(FIRST,"PLACE","旧城","","第一个旧城",List.of("白塔"))),List.of(),Map.of());
        var result=tools.invoke(world,"world_search",json.valueToTree(Map.of("query","白塔")),null);
        assertThat(result.output().path("results")).hasSize(2);
        assertThat(result.output().path("results").get(0).path("sourceId").asText()).isEqualTo("entry:"+FIRST);
        assertThat(result.output().path("results").get(1).path("sourceId").asText()).isEqualTo("entry:"+SECOND);
        assertThat(result.state().receipts()).allMatch(r->!r.fullBlock());
        assertThat(result.state().tokens()).isPositive();
    }
    @Test void excludedSourcesAndRelationsWithExcludedEndpointsAreNotDiscoverable() {
        var world=freeze(List.of(entry(FIRST,"PLACE","白塔","","塔内秘密",List.of()),entry(SECOND,"PLACE","盐港","","港口",List.of())),List.of(new Relation(RELATION,FIRST,SECOND,"航线","白塔至盐港")),Map.of("entry:"+FIRST,Choice.EXCLUDE));
        var search=tools.invoke(world,"world.search",json.valueToTree(Map.of("query","白塔")),null);
        assertThat(search.output().path("results")).isEmpty();
        assertThat(tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","entry:"+FIRST)),null).output().path("error").asText()).isEqualTo("WORLD_SOURCE_UNAVAILABLE");
        assertThat(tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","relation:"+RELATION)),null).output().path("error").asText()).isEqualTo("WORLD_SOURCE_UNAVAILABLE");
        assertThat(tools.invoke(world,"world_related",json.valueToTree(Map.of("sourceId","entry:"+SECOND)),null).output().path("results")).isEmpty();
    }
    @Test void explicitRequiredRelationsRequireBothEndpointsAndCompleteReads() {
        var world=freeze(List.of(entry(FIRST,"PLACE","白塔","","塔",List.of()),entry(SECOND,"PLACE","盐港","","港",List.of())),List.of(new Relation(RELATION,FIRST,SECOND,"航线","航程两天")),Map.of("relation:"+RELATION,Choice.INCLUDE));
        assertThat(world.requiredSourceIds()).containsExactly("entry:"+FIRST,"entry:"+SECOND,"relation:"+RELATION);
        var blocked=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","relation:"+RELATION)),null);
        assertThat(blocked.output().path("error").asText()).isEqualTo("RELATION_ENDPOINTS_NOT_READ");
        var a=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","entry:"+FIRST)),null);
        var b=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","entry:"+SECOND)),a.state());
        assertThat(tools.requiredComplete(world,b.state())).isFalse();
        var relation=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","relation:"+RELATION)),b.state());
        assertThat(tools.requiredComplete(world,relation.state())).isTrue();
    }
    @Test void longSourcesHaveStableCompleteBlocksAndSearchIsNotAFullRead() {
        String content="第一段的完整内容。\n"+"盐港的贸易规则。".repeat(280)+"\n最后一段。";
        var world=freeze(List.of(entry(FIRST,"RULE","贸易规则","摘要",content,List.of("港则"))),List.of(),Map.of("entry:"+FIRST,Choice.INCLUDE));
        var source=world.sources().stream().filter(s->s.id().equals("entry:"+FIRST)).findFirst().orElseThrow();
        assertThat(source.blocks()).hasSizeGreaterThan(1);
        assertThat(source.blocks().stream().map(WorldAgentTools.Block::text).reduce("",String::concat)).endsWith(content);
        var searched=tools.invoke(world,"world_search",json.valueToTree(Map.of("query","港则")),null);
        assertThat(tools.requiredComplete(world,searched.state())).isFalse();
        var state=searched.state();
        for(var block:source.blocks()) {
            var read=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId",source.id(),"blockId",block.id())),state);
            assertThat(read.output().has("error")).isFalse();
            assertThat(read.output().path("text").asText()).isEqualTo(block.text());state=read.state();
        }
        assertThat(tools.requiredComplete(world,state)).isTrue();
    }
    @Test void capacityRejectsAWholeBlockAndRepeatedReadsReplayWithoutChargingAgain() {
        var world=freeze(List.of(entry(FIRST,"RULE","贸易规则","","完整规则".repeat(180),List.of())),List.of(),Map.of());
        var request=json.valueToTree(Map.of("sourceId","entry:"+FIRST));
        var first=tools.invoke(world,"world_read",request,null);
        var replay=tools.invoke(world,"world_read",request,first.state());
        assertThat(replay.state()).isEqualTo(first.state());assertThat(replay.output()).isEqualTo(first.output());
        var almostFull=new WorldAgentTools.ReadState(3499,List.of(),Map.of());
        var blocked=tools.invoke(world,"world_read",request,almostFull);
        assertThat(blocked.output().path("error").asText()).isEqualTo("WORLD_AGENT_READ_BUDGET_EXCEEDED");
        assertThat(blocked.state()).isEqualTo(almostFull);
        assertThat(blocked.output().has("text")).isFalse();
    }
    @Test void mandatoryMissingChangedAndOversizedSourcesBlockBeforeAnyMaterialIsReturned() {
        var binding=binding(List.of(entry(FIRST,"RULE","规则","","内容",List.of())),List.of());
        var missing=tools.freeze(binding,new Preferences(WORLD,2,VERSION,Map.of("entry:missing",Choice.INCLUDE),Map.of()),false);
        assertThat(missing.issues()).contains("MISSING_REQUIRED:entry:missing");
        assertThat(tools.invoke(missing,"world_search",json.valueToTree(Map.of("query","规则")),null).output().path("error").asText()).isEqualTo("WORLD_AGENT_BASELINE_INVALID");
        var changed=tools.freeze(binding,new Preferences(WORLD,3,UUID.randomUUID(),Map.of("entry:"+FIRST,Choice.INCLUDE),Map.of("entry:"+FIRST,"old")),false);
        assertThat(changed.issues()).contains("REVIEW_REQUIRED:entry:"+FIRST);
        var huge=freeze(List.of(entry(FIRST,"RULE","大规则","","正文".repeat(12000),List.of())),List.of(),Map.of("entry:"+FIRST,Choice.INCLUDE));
        assertThat(huge.issues()).contains("REQUIRED_CONTEXT_TOO_LARGE");
    }
    @Test void overviewAutoIsAvailableButNotForcedAndH2NeverFreezesSecretWorldMaterial() {
        var normal=freeze(List.of(entry(FIRST,"RULE","秘密规则","","秘密正文",List.of("秘密别名"))),List.of(),Map.of());
        assertThat(normal.sources()).anyMatch(s->s.id().equals("overview"));assertThat(normal.requiredSourceIds()).isEmpty();
        var isolated=tools.freeze(binding(List.of(entry(FIRST,"RULE","秘密规则","","秘密正文",List.of("秘密别名"))),List.of()),Preferences.defaults(WORLD),true);
        assertThat(isolated.sources()).isEmpty();assertThat(tools.available(isolated)).isFalse();
        var read=tools.invoke(isolated,"world_search",json.valueToTree(Map.of("query","秘密")),null);
        assertThat(read.output().toString()).doesNotContain("秘密");assertThat(read.state().receipts()).isEmpty();
        assertThat(tools.invoke(tools.freeze(null,Preferences.defaults(null),false),"world_read",json.createObjectNode(),null).output().path("error").asText()).isEqualTo("WORLD_NOT_BOUND");
    }
    @Test void eventRelationExpansionDoesNotForceLinkedEntriesOrInventCalendarDates() {
        UUID event=UUID.randomUUID(),era=UUID.randomUUID();
        var timeline=new Timeline(1,"潮纪",List.of(new Era(era,"大退潮","未确定终点",new DateRange(new DatePoint(-2,null,null),null),0)),List.of(new Event(event,"封港","封港摘要","完整封港事件",List.of("封潮"),List.of(),null,List.of(FIRST),List.of(era),0)));
        var old=binding(List.of(entry(FIRST,"PLACE","盐港","","港口",List.of())),List.of());var c=old.content();
        var content=new Content(c.name(),c.tagline(),c.themes(),c.creativeIntent(),c.notes(),c.entries(),c.relations(),c.modules(),c.legacyModulesJson(),c.overview(),timeline);
        var bound=new WorldBindingService.Resolved(WORLD,VERSION,"STORY",WORLD,2,"潮汐",1,"PUBLISHED","snapshot",null,content);
        var world=tools.freeze(bound,Preferences.defaults(WORLD),false);
        var related=tools.invoke(world,"world_related",json.valueToTree(Map.of("sourceId","entry:"+FIRST)),null);
        assertThat(related.output().path("results").get(0).path("sourceId").asText()).isEqualTo("event:"+event);
        var read=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","event:"+event)),null);
        assertThat(read.output().path("text").asText()).contains("潮纪","日期未知","大退潮","-2年 — 未知","完整封港事件");
        assertThat(read.output().has("error")).isFalse();
    }
    @Test void frozenProjectionRoundTripsAndNeverObservesLaterDraftChanges() throws Exception {
        var world=freeze(List.of(entry(FIRST,"PLACE","盐港","","原版港口",List.of())),List.of(),Map.of());
        var retained=json.readValue(json.writeValueAsString(world),WorldAgentTools.FrozenWorld.class);
        assertThat(retained).isEqualTo(world);
        assertThat(retained.binding().content()).isNull();
        assertThat(tools.invoke(retained,"world_read",json.valueToTree(Map.of("sourceId","entry:"+FIRST)),null).output().path("text").asText()).contains("原版港口");
    }
    @Test void optionalReadsCannotUseCapacityReservedForAllRemainingRequiredBlocks() {
        var entries=List.of(entry(FIRST,"RULE","返航规则","","返航规程。".repeat(480),List.of()),
                entry(SECOND,"LOCATION","普通码头","","普通港口说明。".repeat(110),List.of()));
        var optional=tools.invoke(freeze(entries,List.of(),Map.of()),"world_read",json.valueToTree(Map.of("sourceId","entry:"+SECOND)),null);
        assertThat(optional.output().has("error")).isFalse();

        var world=freeze(entries,List.of(),Map.of("entry:"+FIRST,Choice.INCLUDE));
        assertThat(world.issues()).isEmpty();
        var required=world.sources().stream().filter(source->source.id().equals("entry:"+FIRST)).findFirst().orElseThrow();
        assertThat(required.blocks()).hasSizeGreaterThan(1);
        var first=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId",required.id(),"blockId",required.blocks().getFirst().id())),null);
        assertThat(first.output().has("error")).isFalse();
        var blocked=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","entry:"+SECOND)),first.state());
        assertThat(blocked.output().path("error").asText()).isEqualTo("WORLD_AGENT_REQUIRED_READ_CAPACITY");
        assertThat(blocked.state()).isEqualTo(first.state());
        assertThat(blocked.output().toString()).doesNotContain("普通港口说明","普通码头");

        var state=blocked.state();
        for(var block:required.blocks().subList(1,required.blocks().size())) {
            var read=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId",required.id(),"blockId",block.id())),state);
            assertThat(read.output().has("error")).isFalse();state=read.state();
        }
        assertThat(tools.requiredComplete(world,state)).isTrue();
        assertThat(state.tokens()).isLessThanOrEqualTo(WorldAgentTools.TOKEN_BUDGET);
        assertThat(state.receipts()).noneMatch(receipt->receipt.sourceId().equals("entry:"+SECOND));
    }
    @Test void relationDirectoriesAndReadsWithholdMaterialUntilBothEndpointsAreFullyRead() {
        String description="航程三日须用红帆，未核对两端前不能采用这条航线。";
        var world=freeze(List.of(entry(FIRST,"LOCATION","白塔","","白塔的完整航海资料。".repeat(130),List.of()),entry(SECOND,"LOCATION","盐港","","港口资料",List.of())),List.of(new Relation(RELATION,FIRST,SECOND,"航线",description)),Map.of());
        var search=tools.invoke(world,"world_search",json.valueToTree(Map.of("query","航程三日须用红帆")),null);
        var related=tools.invoke(world,"world_related",json.valueToTree(Map.of("sourceId","entry:"+FIRST)),null);
        for(var output:List.of(search.output(),related.output())) {
            var relation=result(output,"relation:"+RELATION);
            assertThat(relation.path("reason").asText()).isEqualTo("RELATION_ENDPOINTS_NOT_READ");
            assertThat(relation.has("title")).isFalse();assertThat(relation.has("snippet")).isFalse();assertThat(relation.has("text")).isFalse();
            assertThat(output.toString()).doesNotContain("白塔 → 盐港",description);
        }
        var source=world.sources().stream().filter(item->item.id().equals("entry:"+FIRST)).findFirst().orElseThrow();
        assertThat(source.blocks()).hasSizeGreaterThan(1);
        var partial=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId",source.id())),null);
        var other=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","entry:"+SECOND)),partial.state());
        var blocked=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","relation:"+RELATION)),other.state());
        assertThat(blocked.output().path("error").asText()).isEqualTo("RELATION_ENDPOINTS_NOT_READ");
        assertThat(blocked.state()).isEqualTo(other.state());assertThat(blocked.output().toString()).doesNotContain(description,"白塔 → 盐港");
        var state=blocked.state();
        for(var block:source.blocks().subList(1,source.blocks().size())) {
            var read=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId",source.id(),"blockId",block.id())),state);
            assertThat(read.output().has("error")).isFalse();state=read.state();
        }
        var admitted=tools.invoke(world,"world_read",json.valueToTree(Map.of("sourceId","relation:"+RELATION)),state);
        assertThat(admitted.output().has("error")).isFalse();assertThat(admitted.output().path("text").asText()).contains(description);
    }
    @Test void normalizationExpansionKeepsSnippetOffsetsInsideTheOriginalImmutableText() {
        String body="㍿".repeat(250)+"目标记号🌊";
        var world=freeze(List.of(entry(FIRST,"HISTORY","异文碑刻","",body,List.of())),List.of(),Map.of());
        var search=tools.invoke(world,"world_search",json.valueToTree(Map.of("query","目标记号")),null);
        assertThat(search.output().has("error")).isFalse();
        var hit=result(search.output(),"entry:"+FIRST);
        String original=world.sources().stream().filter(source->source.id().equals("entry:"+FIRST)).findFirst().orElseThrow().blocks().stream().map(WorldAgentTools.Block::text).reduce("",String::concat);
        int start=hit.path("start").asInt(),end=hit.path("end").asInt();
        assertThat(start).isBetween(0,original.length()-1);assertThat(end).isBetween(start+1,original.length());
        assertThat(Character.isLowSurrogate(original.charAt(start))).isFalse();assertThat(Character.isHighSurrogate(original.charAt(end-1))).isFalse();
        assertThat(hit.path("snippet").asText()).isEqualTo(original.substring(start,end));
        assertThat(search.state().receipts()).anySatisfy(receipt->{assertThat(receipt.start()).isEqualTo(start);assertThat(receipt.end()).isEqualTo(end);assertThat(receipt.text()).isEqualTo(original.substring(start,end));});
    }
    @Test void readAndReplayReceiptsKeepTheirExactVersionWhenStableEntryIdsExistInNewVersions() throws Exception {
        var old=freeze(List.of(entry(FIRST,"RULE","航行规则","","旧版两天返航。",List.of())),List.of(),Map.of());
        var request=json.valueToTree(Map.of("sourceId","entry:"+FIRST));var first=tools.invoke(old,"world_read",request,null);
        UUID nextVersion=UUID.fromString("00000000-0000-0000-0000-000000000006");
        var c=old.binding();var content=binding(List.of(entry(FIRST,"RULE","航行规则","","新版三天返航。",List.of())),List.of()).content();
        var binding=new WorldBindingService.Resolved(WORLD,nextVersion,c.sourceType(),c.sourceId(),c.bindingRevision()+1,c.name(),2,"PUBLISHED","next-snapshot",null,content);
        var next=tools.freeze(binding,Preferences.defaults(WORLD),false);
        var second=tools.invoke(next,"world_read",request,null);
        assertThat(first.output().path("worldVersionId").asText()).isEqualTo(VERSION.toString());
        assertThat(second.output().path("worldVersionId").asText()).isEqualTo(nextVersion.toString());
        assertThat(first.output().path("text").asText()).contains("旧版两天返航。").doesNotContain("新版三天返航。");
        assertThat(second.output().path("text").asText()).contains("新版三天返航。");
        assertThat(first.output().path("contentHash").asText()).isNotEqualTo(second.output().path("contentHash").asText());
        var retained=json.readValue(json.writeValueAsString(first.state()),WorldAgentTools.ReadState.class);
        var replay=tools.invoke(old,"world_read",request,retained);
        assertThat(replay.output()).isEqualTo(first.output());assertThat(replay.state()).isEqualTo(retained);
        assertThat(replay.state().receipts().getFirst().contentHash()).isEqualTo(first.output().path("contentHash").asText());
        assertThat(tools.invoke(old,"world_search",json.valueToTree(Map.of("query","航行规则")),null).output().path("worldVersionId").asText()).isEqualTo(VERSION.toString());
    }
    private static JsonNode result(JsonNode output,String id){for(var item:output.path("results"))if(item.path("sourceId").asText().equals(id))return item;throw new AssertionError("Expected source "+id);}
    private WorldAgentTools.FrozenWorld freeze(List<Entry> entries,List<Relation> relations,Map<String,Choice> choices){return tools.freeze(binding(entries,relations),new Preferences(WORLD,0,VERSION,choices,Map.of()),false);}
    private WorldBindingService.Resolved binding(List<Entry> entries,List<Relation> relations){return new WorldBindingService.Resolved(WORLD,VERSION,"STORY",WORLD,1,"潮汐",1,"PUBLISHED","snapshot",null,new Content("潮汐","群岛",List.of("记忆"),"创作意图","作者备注",entries,relations,Map.of(),null,"短概览"));}
    private static Entry entry(UUID id,String type,String title,String summary,String content,List<String> aliases){return new Entry(id,type,title,summary,content,aliases,List.of(),null,null);}
}
