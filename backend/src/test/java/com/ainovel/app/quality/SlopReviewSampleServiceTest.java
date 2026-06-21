package com.ainovel.app.quality;

import com.ainovel.app.admin.dto.SlopReviewSampleCreateRequest;
import com.ainovel.app.admin.dto.SlopReviewSampleDto;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.model.SlopReviewSample;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.quality.repo.SlopReviewSampleRepository;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlopReviewSampleServiceTest {
    private SlopReviewSampleRepository sampleRepository;
    private SlopQualityRunRepository runRepository;
    private ManuscriptRepository manuscriptRepository;
    private SlopReviewSampleService service;

    @BeforeEach
    void setUp() {
        sampleRepository = mock(SlopReviewSampleRepository.class);
        runRepository = mock(SlopQualityRunRepository.class);
        manuscriptRepository = mock(ManuscriptRepository.class);
        service = new SlopReviewSampleService(sampleRepository, runRepository, manuscriptRepository, new LocalSlopHeuristics());
    }

    @Test
    void createManualSampleShouldEvaluateObservedSignals() {
        SlopReviewSample saved = withId(new SlopReviewSample());
        when(sampleRepository.save(any(SlopReviewSample.class))).thenAnswer(invocation -> {
            SlopReviewSample sample = invocation.getArgument(0);
            saved.copyMutableFieldsFrom(sample);
            return saved;
        });

        SlopReviewSampleDto dto = service.createManual(new SlopReviewSampleCreateRequest(
                "P1-admin-density",
                "她的嘴角微微上扬，空气仿佛凝固。时间像是停了下来，她心中涌起一股说不出的感觉。命运的齿轮在这一刻转动。",
                "都市悬疑",
                "冷峻",
                "",
                "",
                "E2",
                true,
                "高密度模板样本"
        ), "admin");

        assertEquals("MANUAL", dto.sourceType());
        assertEquals("E2", dto.observedEvidenceLevel());
        assertTrue(dto.observedRequiresAiReview());
        assertTrue(dto.matchesExpected());
        assertEquals("PENDING", dto.status());
        assertEquals("高密度模板样本", dto.reviewerNote());
        verify(sampleRepository).save(any(SlopReviewSample.class));
    }

    @Test
    void createFromRunShouldReuseExistingSampleForSameRun() {
        UUID runId = UUID.randomUUID();
        SlopReviewSample existing = withId(new SlopReviewSample());
        existing.setSourceType("SLOP_RUN");
        existing.setSourceRunId(runId);
        existing.setSampleId("RUN-" + runId);
        existing.setText("已有样本");
        existing.setExpectedEvidenceLevel("E1");
        existing.setExpectedRequiresAiReview(false);
        existing.setObservedEvidenceLevel("E1");
        existing.setObservedRequiresAiReview(false);
        existing.setObservedRiskScore(34);
        existing.setObservedMaxSeverity(SlopSeverity.LOW.name());
        existing.setMatchesExpected(true);
        existing.setStatus(SlopReviewSampleStatus.PENDING);

        when(sampleRepository.findBySourceTypeAndSourceRunId("SLOP_RUN", runId)).thenReturn(Optional.of(existing));

        SlopReviewSampleDto dto = service.createFromRun(runId, "admin");

        assertEquals(existing.getId(), dto.id());
        assertEquals("已有样本", dto.text());
        assertFalse(dto.observedRequiresAiReview());
    }

    @Test
    void createFromRunShouldExtractSceneTextAndPrefillExpectedFields() {
        UUID runId = UUID.randomUUID();
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        SlopQualityRun run = new SlopQualityRun();
        run.setId(runId);
        run.setStoryId(UUID.randomUUID());
        run.setManuscriptId(manuscriptId);
        run.setSceneId(sceneId);
        run.setEvidenceLevel("E4");
        run.setOverallRiskScore(88);
        run.setMaxSeverity(SlopSeverity.HIGH);

        Story story = new Story();
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        Outline outline = new Outline();
        outline.setStory(story);
        Manuscript manuscript = new Manuscript();
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"<p>以下是修改版：</p><p>她推开门。</p>\"}");

        SlopReviewSample saved = withId(new SlopReviewSample());
        when(sampleRepository.findBySourceTypeAndSourceRunId("SLOP_RUN", runId)).thenReturn(Optional.empty());
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(manuscriptRepository.findWithStoryById(manuscriptId)).thenReturn(Optional.of(manuscript));
        when(sampleRepository.save(any(SlopReviewSample.class))).thenAnswer(invocation -> {
            SlopReviewSample sample = invocation.getArgument(0);
            saved.copyMutableFieldsFrom(sample);
            return saved;
        });

        SlopReviewSampleDto dto = service.createFromRun(runId, "admin");

        assertEquals("SLOP_RUN", dto.sourceType());
        assertEquals(runId, dto.sourceRunId());
        assertEquals("E4", dto.expectedEvidenceLevel());
        assertTrue(dto.expectedRequiresAiReview());
        assertTrue(dto.text().contains("以下是修改版"));
        assertFalse(dto.text().contains("<p>"));
    }

    private SlopReviewSample withId(SlopReviewSample sample) {
        sample.setId(UUID.randomUUID());
        return sample;
    }
}
