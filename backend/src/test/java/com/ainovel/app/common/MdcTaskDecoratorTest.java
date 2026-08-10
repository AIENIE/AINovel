package com.ainovel.app.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesCallerContextAndRestoresWorkerContext() {
        MDC.put("requestId", "caller-request");
        AtomicReference<String> observedRequestId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            observedRequestId.set(MDC.get("requestId"));
            MDC.put("requestId", "task-mutated");
        });

        MDC.put("requestId", "worker-request");
        MDC.put("workerOnly", "preserved");
        decorated.run();

        assertThat(observedRequestId).hasValue("caller-request");
        assertThat(MDC.get("requestId")).isEqualTo("worker-request");
        assertThat(MDC.get("workerOnly")).isEqualTo("preserved");
    }

    @Test
    void restoresWorkerContextWhenTaskFails() {
        MDC.put("requestId", "caller-request");
        Runnable decorated = decorator.decorate(() -> {
            MDC.put("requestId", "task-mutated");
            throw new IllegalStateException("expected");
        });

        MDC.put("requestId", "worker-request");

        assertThatThrownBy(decorated::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expected");
        assertThat(MDC.get("requestId")).isEqualTo("worker-request");
    }
}
