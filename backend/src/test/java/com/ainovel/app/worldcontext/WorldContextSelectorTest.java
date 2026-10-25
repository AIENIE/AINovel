package com.ainovel.app.worldcontext;

import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.world.WorldDraftDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldContextSelectorTest {
    private final WorldContextSelector selector=new WorldContextSelector(new ObjectMapper());
    private final UUID world=UUID.randomUUID(),version=UUID.randomUUID(),place=UUID.randomUUID(),rule=UUID.randomUUID(),other=UUID.randomUUID(),relation=UUID.randomUUID();
    private WorldBindingService.Resolved binding(String overview,String ruleText) {
        var c=new Content("群岛","退潮后通行",List.of(),"","秘密备注",
                List.of(new Entry(place,"LOCATION","北堤盐匣库","仓库","只储存干盐",List.of("盐库"),List.of("北堤"),null,null),
                        new Entry(rule,"RULE","航路登记","限制",ruleText,List.of(),List.of("通行"),null,null),
                        new Entry(other,"CUSTOM","星空仪","","测量星轨",List.of(),List.of(),null,null)),
                List.of(new Relation(relation,place,rule,"约束","出入库受登记约束")),Map.of(),null,overview);
        return new WorldBindingService.Resolved(world,version,"STORY",UUID.randomUUID(),1,"群岛",1,"PUBLISHED","snapshot",null,c);
    }
    @Test void aliasesAndOneHopAreStableAndDoNotReadUnrelatedEntries() {
        var b=binding("岛民依潮汐生活。","必须登记后通行");var p=Preferences.defaults(world);
        var first=selector.select(b,p,"她走向盐库",3500,false);var second=selector.select(b,p,"她走向盐库",3500,false);
        assertEquals(first,second);assertTrue(first.included().stream().anyMatch(s->s.id().equals("entry:"+place)&&s.reason().startsWith("NAME_MATCH")));
        assertTrue(first.included().stream().anyMatch(s->s.id().equals("entry:"+rule)&&s.reason().equals("RELATED_ONE_HOP")));
        assertTrue(first.excluded().stream().anyMatch(s->s.id().equals("entry:"+other)));
    }
    @Test void longOverviewAndRequiredSourcesBlockInsteadOfTruncating() {
        var b=binding("完整概览".repeat(4000),"完整规则");var result=selector.select(b,Preferences.defaults(world),"盐库",3500,false);
        assertTrue(result.issues().contains("REQUIRED_CONTEXT_TOO_LARGE:overview"));
        assertEquals(b.content().overview(),result.excluded().stream().filter(s->s.id().equals("overview")).findFirst().orElseThrow().content());
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->WorldContextSelector.requireUsable(result));
        var p=new Preferences(world,1,version,Map.of("overview",Choice.EXCLUDE),Map.of());
        assertTrue(selector.select(b,p,"盐库",3500,false).issues().isEmpty());
    }
    @Test void requiredRelationCannotBypassExcludedEndpointOrIsolation() {
        var b=binding("秘密概览","秘密规则");var p=new Preferences(world,2,version,Map.of("relation:"+relation,Choice.INCLUDE,"entry:"+place,Choice.EXCLUDE),Map.of());
        var result=selector.select(b,p,"盐库",3500,false);
        assertTrue(result.issues().stream().anyMatch(s->s.startsWith("RELATION_ENDPOINT_CONFLICT")));
        assertFalse(result.included().stream().anyMatch(s->s.id().equals("relation:"+relation)));
        var isolated=selector.select(b,p,"盐库",3500,true);
        assertEquals("",isolated.content());assertTrue(isolated.included().isEmpty());assertTrue(isolated.issues().isEmpty());
        assertTrue(isolated.excluded().stream().allMatch(s->s.reason().equals("H2_UNCLASSIFIED")));
    }
    @Test void changedAndDeletedRequiredEntriesNeedReview() {
        var b=binding("","旧规则");var p=new Preferences(world,4,UUID.randomUUID(),Map.of("entry:"+rule,Choice.INCLUDE),selector.sourceHashes(b));
        assertTrue(selector.select(binding("","新规则"),p,"",3500,false).issues().contains("REVIEW_REQUIRED:entry:"+rule));
        var missing=new Preferences(world,1,version,Map.of("entry:"+UUID.randomUUID(),Choice.INCLUDE),Map.of());
        assertTrue(selector.select(b,missing,"",3500,false).issues().getFirst().startsWith("MISSING_REQUIRED"));
    }
    @Test void absentWorldIsExplicitAndUnusedChoicesCannotIntroduceMaterial() {
        var result=selector.select(null,Preferences.defaults(null),"场景",3500,false);
        assertNull(result.binding());assertEquals("",result.content());assertEquals(0,result.tokenUsed());
    }
    @Test void sameNameEntriesRemainDistinctAndRulesPrecedeOrdinaryMatches() {
        var base=binding("","");var first=UUID.fromString("00000000-0000-0000-0000-000000000001");var second=UUID.fromString("00000000-0000-0000-0000-000000000002");
        var c=new Content("群岛","",List.of(),"","",List.of(new Entry(first,"LOCATION","北堤","","通行",List.of(),List.of(),null,null),new Entry(second,"RULE","北堤","","通行",List.of(),List.of(),null,null)),List.of(),Map.of(),null,"");
        var b=new WorldBindingService.Resolved(world,version,"STORY",base.sourceId(),1,"群岛",1,"PUBLISHED","x",null,c);
        var selected=selector.select(b,Preferences.defaults(world),"北堤",3500,false).included().stream().filter(s->s.id().startsWith("entry:")).toList();
        assertEquals(List.of("entry:"+second,"entry:"+first),selected.stream().map(Source::id).toList());
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->selector.select(new WorldBindingService.Resolved(world,null,"STORY",base.sourceId(),1,"群岛",0,null,null,"WORLD_BINDING_UNRESOLVED",null),Preferences.defaults(world),"",3500,false));
    }
    private WorldBindingService.Resolved withTimeline(String body) {
        var base=binding("","");var c=base.content();var era=UUID.fromString("00000000-0000-0000-0000-000000000020");
        var date=new DateRange(new DatePoint(-20,null,null),new DatePoint(-10,null,null));
        var timeline=new Timeline(1,"群岛纪年",List.of(new Era(era,"潮封时代","海路被封闭",date,0)),
            List.of(new Event(relation,"归潮仪式","仪式摘要",body,List.of("返潮礼"),List.of("祭礼"),null,List.of(place),List.of(era),0)));
        var content=new Content(c.name(),c.tagline(),c.themes(),c.creativeIntent(),c.notes(),c.entries(),c.relations(),c.modules(),c.legacyModulesJson(),c.overview(),timeline);
        return new WorldBindingService.Resolved(world,version,"STORY",base.sourceId(),1,"群岛",1,"PUBLISHED","snapshot",null,content);
    }
    @Test void eventsMatchAliasesAndRenderOnlyTheBoundSnapshotWithSoftAssociations() {
        var b=withTimeline("完整事件正文");var prefs=new Preferences(world,3,version,Map.of("event:"+relation,Choice.INCLUDE,"entry:"+place,Choice.EXCLUDE),Map.of());
        var result=selector.select(b,prefs,"返潮礼",3500,false);
        var event=result.included().stream().filter(s->s.id().equals("event:"+relation)).findFirst().orElseThrow();
        assertTrue(event.content().contains("群岛纪年"));assertTrue(event.content().contains("潮封时代"));assertTrue(event.content().contains("-20年 — -10年"));
        assertTrue(event.content().contains("日期未知"));assertTrue(event.content().contains("北堤盐匣库"));assertTrue(event.content().contains("完整事件正文"));
        assertTrue(result.issues().isEmpty());assertTrue(result.included().stream().noneMatch(s->s.id().equals("entry:"+place)));
        assertTrue(result.included().stream().noneMatch(s->s.type().equals("ERA")));
        var automatic=selector.select(b,Preferences.defaults(world),"返潮礼",3500,false);
        assertTrue(automatic.included().stream().anyMatch(s->s.type().equals("EVENT")&&s.reason().startsWith("NAME_MATCH")));
        assertTrue(automatic.included().stream().anyMatch(s->s.id().equals("entry:"+place)&&s.reason().equals("RELATED_ONE_HOP")));
    }
    @Test void relatedEventsUseOneHopAndWholeSourcesRespectCapacityAndH2() {
        var b=withTimeline("独立事件正文".repeat(5000));var p=Preferences.defaults(world);
        var found=selector.select(b,p,"盐库",3500,false);var excluded=found.excluded().stream().filter(s->s.type().equals("EVENT")).findFirst().orElseThrow();
        assertEquals("CAPACITY_EXCLUDED",excluded.reason());assertTrue(excluded.content().endsWith("独立事件正文".repeat(5000)));
        var required=new Preferences(world,1,version,Map.of("event:"+relation,Choice.INCLUDE),Map.of());
        assertTrue(selector.select(b,required,"",3500,false).issues().contains("REQUIRED_CONTEXT_TOO_LARGE:event:"+relation));
        var h2=selector.select(b,required,"返潮礼",3500,true);assertEquals("",h2.content());assertTrue(h2.included().isEmpty());assertTrue(h2.issues().isEmpty());
        assertTrue(h2.excluded().stream().allMatch(s->s.reason().equals("H2_UNCLASSIFIED")));
        var before=selector.sourceHashes(withTimeline("旧事件"));var changed=new Preferences(world,1,UUID.randomUUID(),Map.of("event:"+relation,Choice.INCLUDE),before);
        assertTrue(selector.select(withTimeline("新事件"),changed,"",3500,false).issues().contains("REVIEW_REQUIRED:event:"+relation));
    }
}
