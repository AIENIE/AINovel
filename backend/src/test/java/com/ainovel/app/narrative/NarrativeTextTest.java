package com.ainovel.app.narrative;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static com.ainovel.app.narrative.NarrativeDtos.*;
import static com.ainovel.app.narrative.NarrativeText.*;
import static org.junit.jupiter.api.Assertions.*;

class NarrativeTextTest {
    @Test void preservesChineseSupplementaryCharactersAndDecodesEntities() {
        var source = blocks("<p>甲😀𠮷 &amp; 乙</p><p>第二段<br>下一行</p>");
        assertEquals(List.of(new Block("b1", "甲😀𠮷 & 乙"), new Block("b2", "第二段\n下一行")), source);
        Evidence evidence = resolve(source, new Evidence("b1", "😀𠮷", 999, 999));
        assertEquals(1, evidence.start()); assertEquals(3, evidence.end());
    }
    @Test void formattingDoesNotInvalidateSemanticText() {
        assertEquals(textHash(blocks("<p>甲乙</p>")), textHash(blocks("<p><strong>甲</strong><em>乙</em></p>")));
        assertNotEquals(textHash(blocks("<p>甲乙</p>")), textHash(blocks("<p>甲丙</p>")));
    }
    @Test void neverRendersInstructionsOrScriptsAsEvidence() {
        assertEquals(List.of(new Block("b1", "正文")), blocks("<html><head><style>evil</style></head><body><script>alert(1)</script><p>正文</p></body></html>"));
    }
    @Test void refusesInventedAmbiguousAndMissingEvidence() {
        var source = List.of(new Block("b1", "他来了。他来了。"));
        assertEquals("NARRATIVE_EVIDENCE_AMBIGUOUS", assertThrows(ApiStatusException.class,
                () -> resolve(source, new Evidence("b1", "他来了", null, null))).getMessage());
        assertThrows(ApiStatusException.class, () -> resolve(source, new Evidence("b9", "他来了", null, null)));
        assertThrows(ApiStatusException.class, () -> resolve(source, new Evidence("b1", "桥断了", null, null)));
        assertThrows(ApiStatusException.class, () -> resolve(source, new Evidence("b1", "", null, null)));
    }
    @Test void aMovedEarlierSceneChangesDisclosureOrderButLaterAppendDoesNot() throws Exception {
        String chapter = UUID.randomUUID().toString(), a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        ObjectMapper json = new ObjectMapper();
        String prefix = "{\"chapters\":[{\"id\":\"" + chapter + "\",\"scenes\":[";
        String sa = "{\"id\":\"" + a + "\"}", sb = "{\"id\":\"" + b + "\"}";
        var before = positions(json.readTree(prefix + sa + "," + sb + "]}]}"));
        var moved = positions(json.readTree(prefix + sb + "," + sa + "]}]}"));
        var onlyFirst = positions(json.readTree(prefix + sa + "]}]}"));
        assertNotEquals(before.get(UUID.fromString(a)).orderHash(), moved.get(UUID.fromString(a)).orderHash());
        assertEquals(before.get(UUID.fromString(a)).orderHash(), onlyFirst.get(UUID.fromString(a)).orderHash());
    }
}
