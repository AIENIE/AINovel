package com.ainovel.app.admin;

import com.ainovel.app.admin.dto.SlopReviewSampleCreateRequest;
import com.ainovel.app.admin.dto.SlopReviewSampleDto;
import com.ainovel.app.admin.dto.SlopReviewSampleUpdateRequest;
import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.MaterialService;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.quality.SlopReviewSampleService;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.world.repo.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminOperationsControllerQualityReviewTest {

    @Test
    void qualityReviewEndpointsShouldDelegateToServiceWithAdminActor() {
        SlopReviewSampleService sampleService = mock(SlopReviewSampleService.class);
        AdminOperationsController controller = new AdminOperationsController(
                mock(MaterialService.class),
                mock(MaterialRepository.class),
                mock(StoryRepository.class),
                mock(WorldRepository.class),
                mock(ManuscriptRepository.class),
                mock(SlopQualityRunRepository.class),
                mock(PlotQualityRunRepository.class),
                mock(OpsRecordFileSink.class),
                sampleService
        );
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("ops-admin");
        UUID sampleId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        SlopReviewSampleDto dto = dto(sampleId, "MANUAL", null);
        SlopReviewSampleCreateRequest createRequest = new SlopReviewSampleCreateRequest(
                "P6-001",
                "空气仿佛凝固，时间像是停了下来。",
                "悬疑",
                "冷峻",
                "",
                "",
                "E1",
                false,
                "弱信号样本"
        );
        SlopReviewSampleUpdateRequest updateRequest = new SlopReviewSampleUpdateRequest(
                "APPROVED",
                "E1",
                false,
                "确认保留"
        );
        when(sampleService.list("PENDING", "MANUAL", "E1")).thenReturn(List.of(dto));
        when(sampleService.createManual(createRequest, "ops-admin")).thenReturn(dto);
        when(sampleService.createFromRun(runId, "ops-admin")).thenReturn(dto(sampleId, "SLOP_RUN", runId));
        when(sampleService.update(sampleId, updateRequest, "ops-admin")).thenReturn(dto);

        assertEquals(1, controller.qualityReviewSamples("PENDING", "MANUAL", "E1").size());
        assertEquals(dto, controller.createQualityReviewSample(principal, createRequest));
        assertEquals(runId, controller.createQualityReviewSampleFromRun(principal, runId).sourceRunId());
        assertEquals(dto, controller.updateQualityReviewSample(principal, sampleId, updateRequest));

        verify(sampleService).list("PENDING", "MANUAL", "E1");
        verify(sampleService).createManual(createRequest, "ops-admin");
        verify(sampleService).createFromRun(runId, "ops-admin");
        verify(sampleService).update(sampleId, updateRequest, "ops-admin");
    }

    private SlopReviewSampleDto dto(UUID id, String sourceType, UUID sourceRunId) {
        return new SlopReviewSampleDto(
                id,
                sourceType,
                sourceRunId,
                null,
                null,
                null,
                "P6-001",
                "空气仿佛凝固。",
                "空气仿佛凝固。",
                "悬疑",
                "冷峻",
                "",
                "",
                "",
                "",
                "E1",
                false,
                "E1",
                false,
                34,
                "LOW",
                true,
                "PENDING",
                "弱信号样本",
                "ops-admin",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }
}
