package com.ainovel.app.quality;

import com.ainovel.app.v2.V2AnalysisController;
import com.ainovel.app.v2.V2ContextController;
import com.ainovel.app.v2.V2ExportController;
import com.ainovel.app.v2.V2ModelController;
import com.ainovel.app.v2.V2StyleController;
import com.ainovel.app.v2.V2VersionController;
import com.ainovel.app.v2.V2WorkspaceController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiAnnotationCoverageTest {

    @Test
    void v2AndQualityControllersShouldHaveTagsAndOperationSummaries() {
        List<Class<?>> controllers = List.of(
                SlopQualityController.class,
                PlotQualityController.class,
                V2ContextController.class,
                V2StyleController.class,
                V2AnalysisController.class,
                V2VersionController.class,
                V2ExportController.class,
                V2ModelController.class,
                V2WorkspaceController.class
        );

        for (Class<?> controller : controllers) {
            assertNotNull(controller.getAnnotation(Tag.class), controller.getSimpleName() + " missing @Tag");
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                Operation operation = method.getAnnotation(Operation.class);
                assertNotNull(operation, controller.getSimpleName() + "#" + method.getName() + " missing @Operation");
                assertTrue(!operation.summary().isBlank(), controller.getSimpleName() + "#" + method.getName() + " missing summary");
            }
        }
    }

    private boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
