package com.ainovel.app.quality;
import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlopDiagnosticServiceTest {
    final ObjectMapper json=new ObjectMapper(); final AiService ai=mock(AiService.class);
    final SlopQualityRunRepository repository=mock(SlopQualityRunRepository.class);
    final SlopDiagnosticService service=new SlopDiagnosticService(ai,json,new LocalSlopHeuristics(),mock(CharacterCardRepository.class),mock(StyleContextProvider.class),repository,new JsonColumnCodec(json));
    SlopDiagnosticServiceTest() { org.springframework.test.util.ReflectionTestUtils.setField(service,"admission",SlopInputAdmissionTestSupport.create()); when(repository.save(any())).thenAnswer(i->{SlopQualityRun r=i.getArgument(0);r.setId(UUID.randomUUID());return r;}); }
    private SlopQualityRun analyze(String text) throws Exception {
        QualityTestSupport.install(service);
        var user = new User(); user.setId(UUID.randomUUID());
        var story = new com.ainovel.app.story.model.Story(); story.setId(UUID.randomUUID()); story.setUser(user);
        var scene = UUID.randomUUID();
        var outline = new com.ainovel.app.story.model.Outline(); outline.setStory(story);
        outline.setContentJson(json.writeValueAsString(Map.of("chapters", List.of(Map.of("scenes", List.of(Map.of("id", scene)))))));
        var manuscript = new com.ainovel.app.manuscript.model.Manuscript(); manuscript.setId(UUID.randomUUID()); manuscript.setOutline(outline);
        manuscript.setSectionsJson(json.writeValueAsString(Map.of(scene.toString(), org.springframework.web.util.HtmlUtils.htmlEscape(text))));
        var versions = mock(com.ainovel.app.v2.V2VersionPersistenceService.class);
        when(versions.createVersion(any(), any(), any())).thenReturn(Map.of("id", UUID.randomUUID()));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "versions", versions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "manuscripts", mock(com.ainovel.app.manuscript.repo.ManuscriptRepository.class));
        return service.analyzeScene(user, manuscript, scene);
    }
    @Test void persistsFullInputConfirmedEvidenceAndSeparateLocalSignals() throws Exception {
        String text=new java.util.Random(42).ints(7200,97,123).mapToObj(i->String.valueOf((char)i)).collect(java.util.stream.Collectors.joining())+"\n\n当然可以，动作还在。";
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant","""
            {"complete":true,"evidence_items":[
              {"judgment":"DISMISSED","kind":"OUTPUT_ARTIFACT","quote":"当然可以，","risk_score":88,"risk_explanation":"角色对白"},
              {"judgment":"CONFIRMED","kind":"LANGUAGE","category":"AWKWARD","quote":"动作还在","char_start":1,"char_end":5,"risk_score":42,"risk_explanation":"抽象动作无明确含义","repair_hint":"表达具体意图"}]}
            """,null,0));
        var run=analyze(text);
        assertEquals(42,run.getOverallRiskScore(),run.getSummary()); assertEquals(SlopSeverity.MEDIUM,run.getMaxSeverity());
        var d=json.readValue(run.getReviewContextJson(),SlopEvidence.ReviewData.class);
        assertEquals(88,d.localRisk); assertEquals(2,d.findings.size()); assertTrue(d.findings.get(1).eligible());
        assertEquals("RELOCATED",d.findings.get(1).location().trust()); assertEquals(text,d.projection.text());
        var captured=ArgumentCaptor.forClass(AiChatRequest.class); verify(ai).chat(any(),captured.capture());
        assertEquals(text,json.readTree(captured.getValue().messages().get(1).content()).path("text").asText());
        assertNotNull(SlopQualityMapper.toDto(run).review()); assertFalse(run.isRevised());
    }
    @Test void oversizedInputIsUncheckedNotLowRiskAndNoExternalCall() throws Exception {
        var run=analyze("当然可以，"+"雨".repeat(22000));
        assertEquals(SlopQualityStatus.DEGRADED,run.getStatus()); assertEquals("unchecked",run.getRiskLabel());
        assertEquals("正文未检查：输入超限",run.getSummary()); assertEquals("INPUT_LIMIT",SlopQualityMapper.toDto(run).review().coverage());
        verifyNoInteractions(ai);
    }
    @Test void historicalReportsNeverAcquireInventedSourceSnapshots() {
        var old=new SlopQualityRun(); old.setStatus(SlopQualityStatus.ACCEPTED); old.setMaxSeverity(SlopSeverity.LOW);
        assertNull(SlopQualityMapper.toDto(old).review());
    }
}
