package com.ainovel.app.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

class WorldOverviewContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private final WorldEntryStore store=new WorldEntryStore(null,null,json);
    @Test void emptyOverviewPreservesLegacySnapshotHashAndBytes() throws Exception {
        String old="{\"name\":\"群岛\",\"tagline\":\"\",\"themes\":[],\"creativeIntent\":\"\",\"notes\":\"\",\"entries\":[],\"relations\":[],\"modules\":{},\"legacyModulesJson\":\"{}\"}";
        var parsed=store.parse(old);assertEquals(old,store.write(parsed));
        var empty=new Content(parsed.name(),"",List.of(),"","",List.of(),List.of(),Map.of(),"{}","");
        assertEquals(store.hash(parsed),store.hash(empty));
        assertNotEquals(store.hash(parsed),store.hash(new Content(parsed.name(),"",List.of(),"","",List.of(),List.of(),Map.of(),"{}","潮汐群岛总览")));
    }
    @Test void templateVariablesAreValidatedAndMaterialIsNotRenderedRecursively() {
        assertEquals("世界{unknown}：保持限制",WorldPromptRenderer.render("世界{worldName}：{instruction}",Map.of("worldName","{unknown}","instruction","保持限制")));
        assertThrows(RuntimeException.class,()->WorldPromptRenderer.render("{notSupported}",Map.of()));
        assertThrows(RuntimeException.class,()->WorldPromptRenderer.render("{{fn:history worldId}}",Map.of()));
    }
    @Test void oversizedOverviewStopsBeforeAnyAiCall() {
        var admission=new com.ainovel.app.ai.AiAdmissionGuard(null,json,64,20000,100000,20,2,32);
        var renderer=new WorldPromptRenderer(null,store,admission);
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->renderer.request("world rules",List.of("x".repeat(100001))));
    }

}
