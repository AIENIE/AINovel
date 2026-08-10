package com.ainovel.app.g2evaluation;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.g2evaluation.dto.G2EvaluationDtos;
import com.ainovel.app.g2evaluation.model.G2EvaluationStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminG2EvaluationControllerTest {
    @Test
    void createUsesLocalAuthenticationSubjectAndWritesAudit() {
        G2EvaluationService service = mock(G2EvaluationService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminG2EvaluationController controller = new AdminG2EvaluationController(service, recordFileSink);
        G2EvaluationDtos.CreateExperimentRequest request =
                new G2EvaluationDtos.CreateExperimentRequest("边界盲测", List.of("reviewer-one"));
        G2EvaluationDtos.ExperimentResponse response = response(UUID.randomUUID(), G2EvaluationStatus.DRAFT);
        when(service.create("local-operator", request)).thenReturn(response);

        G2EvaluationDtos.ExperimentResponse actual = controller.create(
                new UsernamePasswordAuthenticationToken("local-operator", "n/a"), request
        );

        assertEquals(response, actual);
        verify(service).create("local-operator", request);
        ArgumentCaptor<Map<String, Object>> audit = ArgumentCaptor.forClass(Map.class);
        verify(recordFileSink).appendAudit(audit.capture());
        assertEquals("local-operator", audit.getValue().get("actor"));
        assertEquals("g2-evaluation.create", audit.getValue().get("action"));
    }

    @Test
    void transitionKeepsLocalAuthenticationSubjectInAudit() {
        G2EvaluationService service = mock(G2EvaluationService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminG2EvaluationController controller = new AdminG2EvaluationController(service, recordFileSink);
        UUID id = UUID.randomUUID();
        G2EvaluationDtos.TransitionRequest request =
                new G2EvaluationDtos.TransitionRequest(G2EvaluationStatus.COLLECTING);
        when(service.transition(id, G2EvaluationStatus.COLLECTING))
                .thenReturn(response(id, G2EvaluationStatus.COLLECTING));

        controller.transition(
                new UsernamePasswordAuthenticationToken("local-operator", "n/a"), id, request
        );

        ArgumentCaptor<Map<String, Object>> audit = ArgumentCaptor.forClass(Map.class);
        verify(recordFileSink).appendAudit(audit.capture());
        assertEquals("local-operator", audit.getValue().get("actor"));
        assertEquals("COLLECTING", audit.getValue().get("outcome"));
    }

    private G2EvaluationDtos.ExperimentResponse response(UUID id, G2EvaluationStatus status) {
        return new G2EvaluationDtos.ExperimentResponse(
                id, "边界盲测", status, 1,
                0, 0, 0, 0, 0, 0d, false,
                100, 20, 10, 55d, Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
