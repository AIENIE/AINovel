package com.ainovel.app.quality;
import com.ainovel.app.quality.language.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlopEvidenceTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void replaysAllEightResearchOffsetsAgainstFullOriginalTexts() throws Exception {
        var rows=json.readTree(getClass().getResourceAsStream("/slop-research-location-regressions.json"));
        assertEquals(8,rows.size());
        for(var row:rows) {
            var p=LanguageProjection.of(org.springframework.web.util.HtmlUtils.htmlEscape(row.path("text").asText()));
            var l=SlopEvidence.locate(p,row.path("start").asInt(),row.path("end").asInt(),row.path("quote").asText());
            assertEquals("RELOCATED",l.trust(),row.path("fixture").asText());
            assertEquals(row.path("actualStart").asInt(),l.start()); assertEquals(row.path("actualEnd").asInt(),l.end());
            assertEquals(row.path("quote").asText(),p.text().substring(l.start(),l.end()));
        }
    }
    @Test void neverGuessesAmbiguousMissingOrUnmappableRangesAndKeepsUtf16EntitiesAndNewlines() {
        var p=LanguageProjection.of("<p>😀她说&amp;字。</p><p>她说</p><p>她说</p>");
        assertEquals("AMBIGUOUS",SlopEvidence.locate(p,-1,200,"她说").trust());
        assertNull(SlopEvidence.locate(p,0,2,"不存在").start());
        assertNull(SlopEvidence.locate(p,1,2,"\uDE00").start());
        var entity=SlopEvidence.locate(p,999,1000,"&字"); assertTrue(entity.replaceable());
        assertTrue(p.replace(entity.start(),entity.end(),"&字","与字").contains("与字"));
        var newline=SlopEvidence.locate(p,999,1000,"字。\n她说"); assertFalse(newline.replaceable());
        var markup=LanguageProjection.of("<p>她<b>说</b>。</p>"); assertFalse(SlopEvidence.locate(markup,0,2,"她说").replaceable());
    }
    @Test void fractionalCoordinatesCannotDisambiguateRepeatedQuotes() {
        var p=LanguageProjection.of("<p>她说。她说。</p>");
        var parsed=SlopEvidence.parse("""
            {"complete":true,"evidence_items":[{"judgment":"CONFIRMED","kind":"LANGUAGE","category":"AWKWARD",
            "char_start":0.5,"char_end":2.5,"quote":"她说","risk_explanation":"测试无效坐标","risk_score":80}]}
            """,p,json);
        assertEquals("AMBIGUOUS",parsed.findings().getFirst().location().trust());
        assertFalse(parsed.findings().getFirst().eligible());assertEquals(0,parsed.risk());
    }
    @Test void missingVerdictOrEvidenceBecomesObservationAndStyleCannotCreateCandidates() {
        var p=LanguageProjection.of("<p>她说当然可以。动作还在。</p>");
        var parsed=SlopEvidence.parse("""
            {"complete":true,"evidence_items":[
              {"judgment":"DISMISSED","kind":"OUTPUT_ARTIFACT","quote":"当然可以","risk_score":88,"risk_explanation":"角色对白"},
              {"kind":"LANGUAGE","category":"AWKWARD","quote":"动作还在","risk_score":90,"risk_explanation":"缺少判定"},
              {"judgment":"CONFIRMED","kind":"STYLE","quote":"她说","risk_score":80,"risk_explanation":"可选表达"},
              {"judgment":"CONFIRMED","kind":"LANGUAGE","category":"AWKWARD","quote":"不存在","risk_score":100,"risk_explanation":"错引"}]}
            """,p,json);
        assertEquals(0,parsed.risk()); assertTrue(parsed.findings().stream().noneMatch(SlopEvidence.Finding::eligible));
        assertEquals(SlopEvidence.Kind.OBSERVATION,parsed.findings().get(1).kind());
    }
}
