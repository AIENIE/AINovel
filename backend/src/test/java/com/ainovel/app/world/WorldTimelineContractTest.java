package com.ainovel.app.world;

import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldTimelineContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private final WorldEntryStore store=new WorldEntryStore(null,null,json);
    private static final UUID ERA=UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT=UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ENTRY=UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test void emptyTimelinePreservesBothLegacySnapshotShapesAndHashes() throws Exception {
        for(String overview:List.of("",",\"overview\":\"潮汐群岛总览\"")) {
            String old="{\"name\":\"群岛\",\"tagline\":\"\",\"themes\":[],\"creativeIntent\":\"\",\"notes\":\"\",\"entries\":[],\"relations\":[],\"modules\":{},\"legacyModulesJson\":\"{}\""+overview+"}";
            var content=store.parse(old);assertEquals(old,store.write(content));
            String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(old.getBytes(StandardCharsets.UTF_8)));
            assertEquals(expected,store.hash(content));
            var empty=new Content(content.name(),content.tagline(),content.themes(),content.creativeIntent(),content.notes(),content.entries(),content.relations(),content.modules(),content.legacyModulesJson(),content.overview(),new Timeline(0,null,null,null));
            assertNull(empty.timeline());assertEquals(old,store.write(empty));assertEquals(expected,store.hash(empty));
        }
    }

    @Test void datesPreservePrecisionUnknownEndsAndAuthorDefinedMonths() {
        assertNull(WorldTimeline.normalizeDate(new DateRange(null,null)));
        assertEquals("日期未知",WorldTimeline.renderDate(null));
        var year=new DatePoint(0,null,null);var month=new DatePoint(0,13,null);var day=new DatePoint(0,13,40);
        assertEquals("0年",WorldTimeline.renderDate(new DateRange(year,year)));
        assertEquals("0年13月40日",WorldTimeline.renderDate(new DateRange(day,day)));
        assertEquals("未知 — 0年13月",WorldTimeline.renderDate(new DateRange(null,month)));
        assertEquals("0年 — 未知",WorldTimeline.renderDate(new DateRange(year,null)));
        assertEquals(new DateRange(day,year),WorldTimeline.normalizeDate(new DateRange(day,year)));
        assertEquals(new DateRange(month,day),WorldTimeline.normalizeDate(new DateRange(month,day)));
        assertNotNull(WorldTimeline.normalizeDate(new DateRange(new DatePoint(Integer.MIN_VALUE,null,null),new DatePoint(Integer.MAX_VALUE,null,null))));
        for(var point:List.of(new DatePoint(null,null,null),new DatePoint(null,1,null),new DatePoint(1,null,1),new DatePoint(1,0,null),new DatePoint(1,1,-1)))
            assertThrows(ApiStatusException.class,()->WorldTimeline.normalizeDate(new DateRange(point,null)));
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalizeDate(new DateRange(new DatePoint(2,null,null),new DatePoint(1,100,null))));
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalizeDate(new DateRange(new DatePoint(1,3,2),new DatePoint(1,3,1))));
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalizeDate(new DateRange(new DatePoint(1,3,null),new DatePoint(1,2,100))));
    }

    @Test void dateJsonRejectsCoercionAndKeepsIntegerBoundariesExact() throws Exception {
        assertNull(json.readValue("null",DatePoint.class));
        assertEquals(new DatePoint(0,13,40),json.readValue("{\"year\":0,\"month\":13,\"day\":40}",DatePoint.class));
        assertEquals(new DatePoint(-5,null,null),json.readValue("{\"year\":-5}",DatePoint.class));
        assertEquals(new DatePoint(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE),json.readValue("{\"year\":-2147483648,\"month\":2147483647,\"day\":2147483647}",DatePoint.class));
        assertEquals(new DatePoint(Integer.MAX_VALUE,null,null),json.readValue("{\"year\":2147483647}",DatePoint.class));
        for(String field:List.of("year","month","day"))for(String invalid:List.of("1.9","1.0","1e1","\"1.9\"","\"1\"","\"\"","true","[]","{}","2147483648","-2147483649")) {
            String input="{\"year\":"+(field.equals("year")?invalid:"0")+",\"month\":"+(field.equals("month")?invalid:"1")+",\"day\":"+(field.equals("day")?invalid:"1")+"}";
            assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->json.readValue(input,DatePoint.class),field+"="+invalid);
        }
    }

    @Test void fractionalWorldDatesReturnBadRequestBeforeSaving() throws Exception {
        var versions=org.mockito.Mockito.mock(WorldVersionService.class);
        var mapper=new ObjectMapper().findAndRegisterModules();
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new WorldDraftController(versions))
            .setControllerAdvice(new com.ainovel.app.common.GlobalExceptionHandler())
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).build();
        for(String coordinate:List.of("1.9","\"1.9\"")) {
            String request="{\"entries\":[],\"relations\":[],\"expectedRevision\":0,\"timeline\":{\"schemaVersion\":1,\"eras\":[{\"id\":\""+ERA+"\",\"name\":\"初潮\",\"date\":{\"start\":{\"year\":"+coordinate+"}}}],\"events\":[]}}";
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/worlds/"+UUID.randomUUID()+"/draft")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(request))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("INVALID_REQUEST_BODY"));
        }
        org.mockito.Mockito.verifyNoInteractions(versions);
    }

    @Test void stableIdsCanonicalizeStorageButExplicitOrderChangesHashAndDiff() {
        UUID secondId=UUID.fromString("20000000-0000-0000-0000-000000000002");
        var first=event(EVENT,2,List.of(),List.of());var second=event(secondId,1,List.of(),List.of());
        var a=content(new Timeline(1,"潮汐纪年",List.of(),List.of(first,second)));
        var b=content(new Timeline(1,"潮汐纪年",List.of(),List.of(second,first)));
        assertEquals(store.write(a),store.write(b));assertEquals(store.hash(a),store.hash(b));
        assertTrue(WorldVersionService.compare(null,null,null,a,b).changes().isEmpty());
        var changed=content(new Timeline(1,"潮汐纪年",List.of(),List.of(event(EVENT,3,List.of(),List.of()),second)));
        assertNotEquals(store.hash(a),store.hash(changed));
        var diff=WorldVersionService.compare(null,null,null,a,changed);
        assertEquals(1,diff.changes().size());assertEquals("EVENT",diff.changes().getFirst().kind());assertEquals(EVENT.toString(),diff.changes().getFirst().id());
    }

    @Test void clearingKnownReferencesDoesNotHideInvalidNewReferences() {
        var original=new Timeline(1,"",List.of(era()),List.of(event(EVENT,0,List.of(ENTRY),List.of(ERA))));
        assertEquals(List.of(ENTRY),WorldTimeline.normalize(original,Set.of(ENTRY)).events().getFirst().entryIds());
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalize(original,Set.of()));
        var removedEra=new Timeline(1,"",List.of(),original.events());
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalize(removedEra));
        var cleaned=WorldTimeline.normalize(WorldTimeline.cleanupReferences(removedEra,Set.of(ENTRY),Set.of(ERA)),Set.of());
        assertTrue(cleaned.events().getFirst().entryIds().isEmpty());assertTrue(cleaned.events().getFirst().eraIds().isEmpty());
        UUID invented=UUID.randomUUID();
        var invalid=new Timeline(1,"",List.of(),List.of(event(EVENT,0,List.of(invented),List.of())));
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalize(WorldTimeline.cleanupReferences(invalid,Set.of(ENTRY),Set.of(ERA)),Set.of()));
        var nullReference=new Timeline(1,"",List.of(),List.of(event(EVENT,0,Arrays.asList((UUID)null),List.of())));
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalize(WorldTimeline.cleanupReferences(nullReference,Set.of(),Set.of()),Set.of()));
        assertThrows(ApiStatusException.class,()->WorldTimeline.normalize(new Timeline(1,"",List.of(era()),List.of(event(ERA,0,List.of(),List.of())))));
    }

    @Test void omittedTimelineAndExplicitEmptyObjectHaveDifferentSaveIntent() throws Exception {
        assertNull(json.readValue("{\"entries\":[],\"relations\":[],\"expectedRevision\":0}",Save.class).timeline());
        assertNull(json.readValue("{\"entries\":[],\"relations\":[],\"expectedRevision\":0,\"timeline\":null}",Save.class).timeline());
        var clear=json.readValue("{\"entries\":[],\"relations\":[],\"expectedRevision\":0,\"timeline\":{}}",Save.class).timeline();
        assertNotNull(clear);assertNull(WorldTimeline.normalize(clear));
    }

    @Test void calendarEraAndEventDiffsIncludeCompleteBeforeAndAfterContent() {
        var before=content(new Timeline(1,"旧纪年",List.of(era()),List.of(event(EVENT,0,List.of(),List.of(ERA)))));
        var after=content(new Timeline(1,"新纪年",List.of(),List.of()));
        var changes=WorldVersionService.compare(null,null,null,before,after).changes();
        assertEquals(List.of("TIMELINE","ERA","EVENT"),changes.stream().map(Change::kind).toList());
        assertEquals(era(),changes.get(1).before());assertNull(changes.get(1).after());
        assertEquals(before.timeline().events().getFirst(),changes.get(2).before());assertNull(changes.get(2).after());
    }

    @Test void newAndLegacyEntryRequestsCarryTheSavedTimelineWithoutReinterpretingDates() {
        var settings=org.mockito.Mockito.mock(com.ainovel.app.settings.SettingsService.class);
        var user=new com.ainovel.app.user.User();
        var templates=new com.ainovel.app.settings.dto.WorldPromptTemplatesResponse(Map.of("geography","保持纪年"),Map.of(),"精修 {text}",WorldPromptRenderer.DEFAULTS);
        org.mockito.Mockito.when(settings.getWorldPromptTemplates(user)).thenReturn(templates);
        var admission=new com.ainovel.app.ai.AiAdmissionGuard(null,json,64,20000,100000,20,2,32);
        var renderer=new WorldPromptRenderer(settings,store,admission);
        var event=new Event(EVENT,"首次退潮","海底显露","群岛开始迁移",List.of(),List.of(),
            new DateRange(new DatePoint(-8,null,null),null),List.of(),List.of(ERA),0);
        var timeline=new Timeline(1,"潮汐纪年",List.of(era()),List.of(event));
        var content=new Content("群岛","",List.of(),"","只描述已确认历史",List.of(),List.of(),Map.of(),"{}",null,timeline);
        for(var entry:List.of(new Entry(ENTRY,"CUSTOM","通行规则","","",List.of(),List.of(),null,null),
                new Entry(ENTRY,"GEOGRAPHY","地形地貌","","",List.of(),List.of(),"geography","terrain"))) {
            for(boolean refine:List.of(false,true)) {
                var request=renderer.entry(user,content,entry,"保持事实",refine);
                String actual=request.messages().stream().map(com.ainovel.app.ai.dto.AiChatRequest.Message::content).collect(java.util.stream.Collectors.joining("\n"));
                assertTrue(actual.contains(store.write(timeline)));assertTrue(actual.contains("只描述已确认历史"));
                assertTrue(actual.contains("\"year\":-8,\"month\":null,\"day\":null"));assertTrue(actual.contains("\"end\":null"));
            }
        }
    }

    private static Era era() { return new Era(ERA,"初潮时代","群岛成形",null,0); }
    private static Event event(UUID id,int order,List<UUID> entries,List<UUID> eras) {
        return new Event(id,"首次退潮","海底显露","群岛开始迁移",List.of(),List.of(),null,entries,eras,order);
    }
    private static Content content(Timeline timeline) {
        return new Content("群岛","",List.of(),"","",List.of(),List.of(),Map.of(),"{}",null,timeline);
    }
}
