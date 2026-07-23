package com.ainovel.app.workflow;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiUsageDto;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.user.User;
import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuidedCreationGenerationServiceTest {
    @Test
    void addsStableBillingContextAndServerCandidateIds() {
        AiService aiService = mock(AiService.class);
        User user = user();
        CreationWorkflowRun run = run(user, 6);
        AsyncJob job = job(GuidedCreationStep.PREMISE);
        when(aiService.chat(eq(user), any(AiChatRequest.class), any(AiUsageContext.class)))
                .thenReturn(response(premiseJson(3), 7, 91));

        GuidedCreationGenerationService.GenerationResult result = service(aiService).generate(run, job, (String) null);

        ArgumentCaptor<AiUsageContext> context = ArgumentCaptor.forClass(AiUsageContext.class);
        verify(aiService).chat(eq(user), any(AiChatRequest.class), context.capture());
        assertEquals("G1_WORKFLOW", context.getValue().referenceType());
        assertEquals(job.getId().toString(), context.getValue().referenceId());
        assertEquals("premise", context.getValue().operation());
        assertEquals(7L, result.chargedCredits());
        assertEquals(91L, result.remainingCredits());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.stepData().get("candidates");
        assertEquals(3, candidates.size());
        assertNotEquals(candidates.get(0).get("candidateId"), candidates.get(1).get("candidateId"));
        assertEquals(candidates.get(1).get("candidateId"), result.stepData().get("recommendedCandidateId"));
    }

    @Test
    void rejectsAnythingOtherThanExactlyThreeCandidates() {
        AiService aiService = mock(AiService.class);
        CreationWorkflowRun run = run(user(), 6);
        AsyncJob job = job(GuidedCreationStep.PREMISE);
        when(aiService.chat(any(), any(), any())).thenReturn(
                response(premiseJson(2), 1, 99), response(premiseJson(2), 1, 98));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(aiService).generate(run, job, (String) null));

        assertTrue(error.getMessage().contains("恰好 3 个候选"));
    }

    @Test
    void rejectsChaptersInInitialOutlineDirections() {
        AiService aiService = mock(AiService.class);
        CreationWorkflowRun run = run(user(), 6);
        AsyncJob job = job(GuidedCreationStep.OUTLINE);
        String candidate = "{\"title\":\"钟楼路线\",\"summary\":\"追查停摆真相\","
                + "\"coreConflict\":\"时间与记忆冲突\",\"protagonistDrive\":\"找回过去\","
                + "\"stakes\":\"城市消失\",\"chapters\":[" + chapterJson() + "]}";
        String json = "{\"recommendedIndex\":0,\"candidates\":[" + candidate + "," + candidate + "," + candidate + "]}";
        when(aiService.chat(any(), any(), any())).thenReturn(response(json, 1, 99), response(json, 1, 98));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(aiService).generate(run, job, (String) null));

        assertTrue(error.getMessage().contains("不能包含章节"));
    }

    @Test
    void expandsOnlyTheSelectedDirectionIntoACompleteOutline() {
        AiService aiService = mock(AiService.class);
        CreationWorkflowRun run = run(user(), 6);
        run.setStepsJson("{\"OUTLINE\":{\"outlinePhase\":\"DIRECTION_SELECTION\",\"candidates\":["
                + "{\"candidateId\":\"direction-a\",\"title\":\"钟楼路线\",\"summary\":\"追查停摆真相\"}]}}");
        AsyncJob job = job(GuidedCreationStep.OUTLINE);
        String outline = "{\"title\":\"钟楼路线完整大纲\",\"chapters\":["
                + String.join(",", java.util.Collections.nCopies(6, chapterJson())) + "]}";
        when(aiService.chat(any(), any(), any())).thenReturn(response(outline, 4, 86));

        GuidedCreationGenerationService.GenerationResult result = service(aiService).generate(
                run, job, Map.of(
                        "operation", GuidedCreationOperation.OUTLINE_EXPAND.name(),
                        "directionId", "direction-a",
                        "editedPayload", Map.of(),
                        "instruction", ""));

        assertEquals(6, ((List<?>) result.stepData().get("chapters")).size());
        assertEquals("g1.quick-book.outline-expansion.v1", result.stepData().get("promptVersion"));
        assertEquals(4L, result.chargedCredits());
    }

    @Test
    void repairsMalformedJsonOnceWithAnIndependentBillingOperation() {
        AiService aiService = mock(AiService.class);
        User user = user();
        CreationWorkflowRun run = run(user, 6);
        AsyncJob job = job(GuidedCreationStep.PREMISE);
        when(aiService.chat(eq(user), any(AiChatRequest.class), any(AiUsageContext.class)))
                .thenReturn(response("not-json", 1, 99), response(premiseJson(3), 2, 97));

        GuidedCreationGenerationService.GenerationResult result = service(aiService).generate(run, job, (String) null);

        ArgumentCaptor<AiUsageContext> contexts = ArgumentCaptor.forClass(AiUsageContext.class);
        verify(aiService, org.mockito.Mockito.times(2)).chat(eq(user), any(AiChatRequest.class), contexts.capture());
        assertEquals("premise", contexts.getAllValues().get(0).operation());
        assertEquals("premise:json_repair", contexts.getAllValues().get(1).operation());
        assertEquals(3L, result.chargedCredits());
        assertEquals(97L, result.remainingCredits());
    }

    private GuidedCreationGenerationService service(AiService aiService) {
        ObjectMapper mapper = new ObjectMapper();
        GuidedCreationJsonSupport jsonSupport = new GuidedCreationJsonSupport(new JsonColumnCodec(mapper));
        return new GuidedCreationGenerationService(
                aiService, new GuidedCreationPromptFactory(), jsonSupport, mapper);
    }

    private CreationWorkflowRun run(User user, int chapterCount) {
        CreationWorkflowRun run = new CreationWorkflowRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setUser(user);
        run.setSeedIdea("一名失忆的钟表匠追查停摆的城市");
        run.setTargetChapterCount(chapterCount);
        run.setStepsJson("{}");
        return run;
    }

    private AsyncJob job(GuidedCreationStep step) {
        AsyncJob job = new AsyncJob();
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        job.setStep(step);
        return job;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("guide-user");
        user.setEmail("guide@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid(1001L);
        return user;
    }

    private AiChatResponse response(String content, long charged, long remaining) {
        return new AiChatResponse("assistant", content,
                new AiUsageDto(10, 20, 0, 0, charged), remaining);
    }

    private String premiseJson(int count) {
        String item = "{\"title\":\"停摆之城\",\"synopsis\":\"钟表匠寻找城市停摆与自己失忆之间的真相。\"}";
        return "{\"recommendedIndex\":1,\"candidates\":["
                + String.join(",", java.util.Collections.nCopies(count, item)) + "]}";
    }

    private String chapterJson() {
        return "{\"title\":\"章节\",\"scenes\":[{\"title\":\"场景一\"},{\"title\":\"场景二\"}]}";
    }
}
