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
}
