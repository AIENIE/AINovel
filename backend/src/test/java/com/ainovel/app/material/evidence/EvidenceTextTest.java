package com.ainovel.app.material.evidence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.*;

class EvidenceTextTest {
    @Test
    void positionsPreserveEmojiWhitespaceAndParagraphs() {
        String original = "首行😀\r\n\t 条件：尚未获知。".repeat(130);
        var chunks = EvidenceText.split(original);
        assertTrue(chunks.size() > 1);
        for (var chunk : chunks)
            assertEquals(chunk.text(), EvidenceText.slice(original, chunk.start(), chunk.end()));
        assertEquals(original.codePointCount(0, original.length()), chunks.getLast().end());
        assertEquals(chunks.getFirst().end() - 120, chunks.get(1).start());
    }

    @Test
    void formatNormalizationDoesNotEraseNegationOrPunctuation() {
        assertEquals(EvidenceText.normalize("  否定\r\n\t条件  "), EvidenceText.normalize("否定\n 条件"));
        assertNotEquals(EvidenceText.normalize("已获知"), EvidenceText.normalize("尚未获知"));
        assertNotEquals(EvidenceText.normalize("如果下雨，桥不能通行。"), EvidenceText.normalize("下雨，桥能通行。"));
    }

    @Test
    void unsupportedConclusionRequiresExactEvidence() throws Exception {
        var json = new ObjectMapper();
        String source = "😀只有日落后才准许通行。";
        var valid =
                json.readTree(
                        "{\"findings\":[{\"decision\":\"SUPPORTED\",\"evidence\":[{\"sourceId\":\"x\",\"quote\":\"只有日落后\",\"start\":1,\"end\":6}]}]}");
        EvidenceReportValidator.validate(valid, Map.of("x", source));
        assertThrows(
                IllegalArgumentException.class,
                () -> EvidenceReportValidator.validate(valid, Map.of("x", "日出后准许通行。")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EvidenceReportValidator.validate(
                                json.readTree(
                                        "{\"findings\":[{\"decision\":\"CONTRADICTED\",\"evidence\":[]}]}"),
                                Map.of()));
        EvidenceReportValidator.validate(
                json.readTree("{\"findings\":[{\"decision\":\"INSUFFICIENT\",\"evidence\":[]}]}"),
                Map.of());
    }

    @Test
    void rankFusionDoesNotCompareUnrelatedScoreScales() {
        UUID source = UUID.randomUUID();
        var a = new EvidenceDtos.Hit("a", source, "r", 1, "a", "a", 0, 1, List.of("exact"), 999);
        var b = new EvidenceDtos.Hit("b", source, "r", 1, "b", "b", 0, 1, List.of("semantic"), .9);
        var fused = MaterialEvidenceService.fuse(List.of(List.of(a, b), List.of(b)));
        assertEquals("b", fused.getFirst().chunkId());
        assertTrue(fused.getFirst().rank() < 1);
        assertEquals(2, fused.size());
    }
}
