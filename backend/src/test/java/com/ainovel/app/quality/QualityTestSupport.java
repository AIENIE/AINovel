package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

final class QualityTestSupport {
    private QualityTestSupport() {}

    @SuppressWarnings("unchecked")
    static void install(Object service) {
        com.ainovel.app.manuscript.ManuscriptContentTestSupport.injectLegacy(service);
        var boundary = mock(QualityPersistenceBoundary.class);
        var current = new AtomicReference<Manuscript>();
        when(boundary.snapshot(any(), any())).thenAnswer(call -> {
            Manuscript manuscript = call.getArgument(0);
            current.set(manuscript);
            Function<Manuscript,Object> read = call.getArgument(1);
            return new QualityPersistenceBoundary.Snapshot<>(manuscript.getId(), manuscript.getVersion(), read.apply(manuscript));
        });
        when(boundary.save(any(), any(), any())).thenAnswer(call -> {
            Function<Manuscript,Object> write = call.getArgument(1);
            return write.apply(current.get());
        });
        ReflectionTestUtils.setField(service, "boundary", boundary);
        if (service instanceof SlopDiagnosticService) {
            ReflectionTestUtils.setField(service, "isolation", mock(com.ainovel.app.narrative.NarrativeContextService.class));
        }
        if (service instanceof PlotQualityService) {
            ReflectionTestUtils.setField(service, "isolation", mock(com.ainovel.app.narrative.NarrativeContextService.class));
            ReflectionTestUtils.setField(service, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        }
    }
}
