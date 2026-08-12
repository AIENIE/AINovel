package com.ainovel.app.manuscript.attribution;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.GenerationMode;
import com.ainovel.app.manuscript.attribution.model.SceneGenerationRun;
import com.ainovel.app.manuscript.attribution.repo.SceneGenerationRunRepository;
import com.ainovel.app.manuscript.dto.SceneGenerationFeedbackPatchRequest;
import com.ainovel.app.manuscript.dto.SceneGenerationRunDto;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2ManuscriptVersion;
import com.ainovel.app.v2.repo.V2ManuscriptVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneGenerationAttributionServiceTest {

    @Test
    void generationPersistsCanonicalMetadataAndSupersedesPreviousLiveRun() {
        SceneGenerationRunRepository runRepository = mock(SceneGenerationRunRepository.class);
        SceneGenerationAttributionService service = new SceneGenerationAttributionService(
                runRepository,
                mock(V2ManuscriptVersionRepository.class),
                new ObjectMapper()
        );
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID generationVersionId = UUID.randomUUID();
        Manuscript manuscript = manuscriptWithOwner(manuscriptId);
        SceneGenerationRun previous = run(
                manuscript,
                sceneId,
                UUID.randomUUID(),
                SceneGenerationRunStatus.EDITED
        );
        when(runRepository.findByManuscriptIdAndSceneIdAndStatusInOrderByCreatedAtDesc(
                manuscriptId,
                sceneId,
                List.of(SceneGenerationRunStatus.GENERATED, SceneGenerationRunStatus.EDITED)
        )).thenReturn(List.of(previous));
        when(runRepository.saveAndFlush(any(SceneGenerationRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SceneGenerationRunDto result = service.recordGeneration(
                manuscript,
                sceneId,
                generationVersionId,
                GenerationMode.CRAFTED,
                Map.of(
                        "modelKey", "ignored-model",
                        "promptVersion", "ignored-prompt-version",
                        "promptHash", "d".repeat(64),
                        "attemptCount", 2,
                        "contextHash", "e".repeat(64),
                        "tokenBudget", 4_000,
                        "tokenUsed", 3_100,
                        "sources", List.of(Map.of(
                                "sourceType", "previous_scene",
                                "sourceId", "scene-previous",
                                "label", "上一场",
                                "reason", "连续性",
                                "estimatedTokens", 300,
                                "truncated", false,
                                "content", "must not persist"
                        )),
                        "prompt", "must not persist"
                ),
                "正文😀"
        );

        assertEquals(SceneGenerationRunStatus.GENERATED, result.status());
        assertEquals("crafted", result.mode());
        assertEquals("deepseek-v4-flash", result.modelKey());
        assertEquals("scene-draft-v2", result.promptVersion());
        assertEquals(2, result.attemptCount());
        assertEquals(previous.getId(), result.previousRunId());
        assertEquals("scene-draft-v2", result.contextManifest().get("promptVersion"));
        assertEquals(4_000, result.contextManifest().get("tokenBudget"));
        assertFalse(result.contextManifest().containsKey("prompt"));
        assertEquals(SceneGenerationRunStatus.SUPERSEDED, previous.getStatus());
        verify(runRepository).saveAllAndFlush(List.of(previous));
    }

    @Test
    void queryDetectsContentHashDriftAndRecomputesEqualLengthRewriteByCodePoint() {
        SceneGenerationRunRepository runRepository = mock(SceneGenerationRunRepository.class);
        V2ManuscriptVersionRepository versionRepository = mock(V2ManuscriptVersionRepository.class);
        SceneGenerationAttributionService service = new SceneGenerationAttributionService(
                runRepository,
                versionRepository,
                new ObjectMapper()
        );
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"甲🚀乙\"}");
        SceneGenerationRun run = run(manuscript, sceneId, versionId, SceneGenerationRunStatus.GENERATED);
        V2ManuscriptVersion version = new V2ManuscriptVersion();
        version.setSectionsJson("{\"" + sceneId + "\":\"甲😀乙\"}");

        when(runRepository.findByManuscriptIdAndSceneIdAndStatusInOrderByCreatedAtDesc(
                manuscriptId,
                sceneId,
                List.of(SceneGenerationRunStatus.GENERATED, SceneGenerationRunStatus.EDITED)
        )).thenReturn(List.of(run));
        when(runRepository.findByManuscriptIdAndSceneIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(manuscriptId),
                org.mockito.ArgumentMatchers.eq(sceneId),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(List.of(run));
        when(versionRepository.findByManuscriptIdAndId(manuscriptId, versionId)).thenReturn(Optional.of(version));
        when(runRepository.saveAndFlush(run)).thenReturn(run);

        SceneGenerationRunDto result = service.listRuns(manuscriptId, sceneId, 10).getFirst();

        assertEquals(SceneGenerationRunStatus.EDITED, result.status());
        assertEquals(Integer.valueOf(1), result.addedCharacters());
        assertEquals(Integer.valueOf(1), result.deletedCharacters());
        assertEquals(Double.valueOf(2.0d / 3.0d), result.retentionRate());
        assertFalse(result.recalculationPending());
    }

    @Test
    void queryKeepsPendingWithNullMetricsWhenRecalculationFails() {
        SceneGenerationRunRepository runRepository = mock(SceneGenerationRunRepository.class);
        V2ManuscriptVersionRepository versionRepository = mock(V2ManuscriptVersionRepository.class);
        SceneGenerationAttributionService service = new SceneGenerationAttributionService(
                runRepository,
                versionRepository,
                new ObjectMapper()
        );
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID missingVersionId = UUID.randomUUID();
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"edited\"}");
        SceneGenerationRun run = run(
                manuscript,
                sceneId,
                missingVersionId,
                SceneGenerationRunStatus.GENERATED
        );
        when(runRepository.findByManuscriptIdAndSceneIdAndStatusInOrderByCreatedAtDesc(
                manuscriptId,
                sceneId,
                List.of(SceneGenerationRunStatus.GENERATED, SceneGenerationRunStatus.EDITED)
        )).thenReturn(List.of(run));
        when(runRepository.findByManuscriptIdAndSceneIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(manuscriptId),
                org.mockito.ArgumentMatchers.eq(sceneId),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(List.of(run));
        when(versionRepository.findByManuscriptIdAndId(manuscriptId, missingVersionId))
                .thenReturn(Optional.empty());
        when(runRepository.saveAndFlush(run)).thenReturn(run);

        SceneGenerationRunDto result = service.listRuns(manuscriptId, sceneId, 10).getFirst();

        assertEquals(true, result.recalculationPending());
        assertEquals(null, result.addedCharacters());
        assertEquals(null, result.deletedCharacters());
        assertEquals(null, result.retentionRate());
    }

    @Test
    void rollbackOnlyRevertsAffectedLiveRunAndRestoresTargetGeneration() {
        SceneGenerationRunRepository runRepository = mock(SceneGenerationRunRepository.class);
        V2ManuscriptVersionRepository versionRepository = mock(V2ManuscriptVersionRepository.class);
        SceneGenerationAttributionService service = new SceneGenerationAttributionService(
                runRepository,
                versionRepository,
                new ObjectMapper()
        );
        UUID manuscriptId = UUID.randomUUID();
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        UUID affectedSceneId = UUID.randomUUID();
        UUID unaffectedSceneId = UUID.randomUUID();
        UUID targetVersionId = UUID.randomUUID();
        SceneGenerationRun affected = run(
                manuscript,
                affectedSceneId,
                UUID.randomUUID(),
                SceneGenerationRunStatus.GENERATED
        );
        SceneGenerationRun unaffected = run(
                manuscript,
                unaffectedSceneId,
                UUID.randomUUID(),
                SceneGenerationRunStatus.GENERATED
        );
        SceneGenerationRun target = run(
                manuscript,
                affectedSceneId,
                targetVersionId,
                SceneGenerationRunStatus.SUPERSEDED
        );
        V2ManuscriptVersion targetVersion = new V2ManuscriptVersion();
        targetVersion.setSectionsJson("{\"" + affectedSceneId + "\":\"restored\"}");
        when(runRepository.findByManuscriptIdAndGenerationVersionId(manuscriptId, targetVersionId))
                .thenReturn(Optional.of(target));
        when(runRepository.findByManuscriptIdAndStatusInOrderByCreatedAtDesc(
                manuscriptId,
                List.of(SceneGenerationRunStatus.GENERATED, SceneGenerationRunStatus.EDITED)
        )).thenReturn(List.of(affected, unaffected));
        when(versionRepository.findByManuscriptIdAndId(manuscriptId, targetVersionId))
                .thenReturn(Optional.of(targetVersion));

        service.markReverted(new ManuscriptRollbackEvent(
                manuscriptId,
                targetVersionId,
                Set.of(affectedSceneId)
        ));

        assertEquals(SceneGenerationRunStatus.REVERTED, affected.getStatus());
        assertEquals(SceneGenerationRunStatus.GENERATED, unaffected.getStatus());
        assertEquals(SceneGenerationRunStatus.GENERATED, target.getStatus());
        assertEquals(0, target.getAddedCharacters());
        assertEquals(0, target.getDeletedCharacters());
        assertEquals(1.0d, target.getRetentionRate());
        verify(runRepository).saveAll(any());
    }

    @Test
    void feedbackUsesFixedTaxonomyAndCountsNoteByUnicodeCodePoint() {
        SceneGenerationRunRepository runRepository = mock(SceneGenerationRunRepository.class);
        SceneGenerationAttributionService service = new SceneGenerationAttributionService(
                runRepository,
                mock(V2ManuscriptVersionRepository.class),
                new ObjectMapper()
        );
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        SceneGenerationRun run = run(manuscript, sceneId, UUID.randomUUID(), SceneGenerationRunStatus.EDITED);
        run.setId(runId);
        when(runRepository.findByIdAndManuscriptIdAndSceneId(runId, manuscriptId, sceneId))
                .thenReturn(Optional.of(run));
        when(runRepository.saveAndFlush(run)).thenReturn(run);

        SceneGenerationRunDto result = service.patchFeedback(
                manuscriptId,
                sceneId,
                runId,
                new SceneGenerationFeedbackPatchRequest(
                        List.of("PLOT_CAUSALITY", "AI_CLICHE", "OTHER"),
                        "😀".repeat(500),
                        true
                )
        );

        assertEquals(List.of("PLOT_CAUSALITY", "AI_CLICHE", "OTHER"), result.feedbackTags());
        assertEquals("😀".repeat(500), result.feedbackNote());
        assertEquals(true, result.preferenceConfirmed());
        assertThrows(BusinessException.class, () -> service.patchFeedback(
                manuscriptId,
                sceneId,
                runId,
                new SceneGenerationFeedbackPatchRequest(List.of("UNRECOGNIZED"), null, null)
        ));
        assertThrows(BusinessException.class, () -> service.patchFeedback(
                manuscriptId,
                sceneId,
                runId,
                new SceneGenerationFeedbackPatchRequest(null, "😀".repeat(501), null)
        ));

        run.setTagsJson("[]");
        run.setNote(null);
        run.setPreferenceConfirmed(false);
        assertThrows(BusinessException.class, () -> service.patchFeedback(
                manuscriptId,
                sceneId,
                runId,
                new SceneGenerationFeedbackPatchRequest(List.of(), "", true)
        ));
    }

    private SceneGenerationRun run(
            Manuscript manuscript,
            UUID sceneId,
            UUID versionId,
            SceneGenerationRunStatus status
    ) {
        SceneGenerationRun run = new SceneGenerationRun();
        run.setId(UUID.randomUUID());
        run.setManuscript(manuscript);
        run.setSceneId(sceneId);
        run.setGenerationVersionId(versionId);
        run.setStatus(status);
        run.setAttributionStatus(SceneAttributionStatus.READY);
        run.setMode("FAST");
        run.setModelKey("deepseek-v4-flash");
        run.setPromptVersion("scene-draft-v2");
        run.setAttemptCount(1);
        run.setContextHash("a".repeat(64));
        run.setPromptHash("b".repeat(64));
        run.setContextManifestJson("{\"promptVersion\":\"scene-draft-v2\",\"tokenBudget\":100,\"tokenUsed\":50,\"sources\":[]}");
        run.setGeneratedContentHash("c".repeat(64));
        run.setGeneratedCharacters(3);
        run.setCurrentCharacters(3);
        run.setRetainedCharacters(3);
        run.setAddedCharacters(0);
        run.setDeletedCharacters(0);
        run.setRetentionRate(1.0d);
        User createdBy = new User();
        createdBy.setId(UUID.randomUUID());
        run.setCreatedBy(createdBy);
        run.setTagsJson("[]");
        return run;
    }

    private Manuscript manuscriptWithOwner(UUID manuscriptId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        com.ainovel.app.story.model.Story story = new com.ainovel.app.story.model.Story();
        story.setUser(user);
        com.ainovel.app.story.model.Outline outline = new com.ainovel.app.story.model.Outline();
        outline.setStory(story);
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{}");
        return manuscript;
    }
}
