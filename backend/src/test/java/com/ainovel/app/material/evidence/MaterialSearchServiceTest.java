package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ainovel.app.material.VectorMatch;
import com.ainovel.app.user.User;

import org.junit.jupiter.api.Test;

import java.util.*;

class MaterialSearchServiceTest {
    Hit hit(String id) {
        return new Hit(
                id,
                UUID.randomUUID(),
                "revision",
                1,
                id,
                id,
                0,
                id.length(),
                List.of("retrieval"),
                0);
    }

    @Test
    void threeRecallPathsAreFusedOnceAndRevokedCandidatesAreRemovedBeforeRerank() {
        var sources = mock(MaterialEvidenceService.class);
        var semantic = mock(MaterialSemanticService.class);
        var nativeClient = mock(MaterialNativeClient.class);
        var user = new User();
        user.setId(UUID.randomUUID());
        UUID story = UUID.randomUUID();
        var query = new Search("雨桥", story, "fact", "bound", 3);
        var a = hit("a");
        var b = hit("b");
        var c = hit("c");
        var full = new ArrayList<Hit>();
        full.add(b);
        for (int i = 0; i < 38; i++) full.add(hit("filler-" + i));
        full.add(a);
        when(sources.basicRecall(eq(user), any()))
                .thenReturn(
                        new MaterialEvidenceService.BasicRecall(
                                "fact", "bound", List.of(a, b), full));
        when(sources.settings(user, story))
                .thenReturn(
                        new Settings(
                                1,
                                "qwen-standard-1024-cp-v1",
                                false,
                                false,
                                false,
                                List.of(a.materialId())));
        when(sources.visibleMaterialIds(user, story, "bound")).thenReturn(List.of(a.materialId()));
        when(semantic.enabled()).thenReturn(true);
        when(semantic.search(eq(user), anyString(), anyString(), eq("雨桥"), anyList()))
                .thenReturn(
                        List.of(
                                new VectorMatch(
                                        c.chunkId(), .9, c.materialId(), c.title(), c.text(), 0)));
        var all = new HashMap<String, Hit>();
        for (var h : full) all.put(h.chunkId(), h);
        all.put(c.chunkId(), c);
        when(sources.visibleHits(eq(user), eq(query), anyList()))
                .thenAnswer(
                        inv -> {
                            List<String> ids = inv.getArgument(2);
                            return ids.stream().map(all::get).filter(Objects::nonNull).toList();
                        });
        var result = new MaterialSearchService(sources, semantic, nativeClient).search(user, query);
        assertEquals(List.of("b", "a", "c"), result.items().stream().map(Hit::chunkId).toList());
        verifyNoInteractions(nativeClient);
        // Withdrawal is rechecked even after semantic retrieval has already returned a candidate.
        all.remove("a");
        result = new MaterialSearchService(sources, semantic, nativeClient).search(user, query);
        assertFalse(result.items().stream().anyMatch(h -> h.chunkId().equals("a")));
    }
}
