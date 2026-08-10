package com.ainovel.app.common;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Runnable task = Objects.requireNonNull(runnable, "runnable");
        Map<String, String> callerContext = copyContext();
        return () -> {
            Map<String, String> workerContext = copyContext();
            try {
                replaceContext(callerContext);
                task.run();
            } finally {
                replaceContext(workerContext);
            }
        };
    }

    private static Map<String, String> copyContext() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return context == null ? null : Map.copyOf(context);
    }

    private static void replaceContext(Map<String, String> context) {
        MDC.clear();
        if (context != null && !context.isEmpty()) {
            MDC.setContextMap(context);
        }
    }
}
