package com.ainovel.app.common;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerLayerArchitectureTest {

    @Test
    void restControllersShouldNotHoldRepositoriesTransactionsOrPrivateCurrentUserHelpers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = scanner.findCandidateComponents("com.ainovel.app").stream()
                .<Class<?>>map(candidate -> load(candidate.getBeanClassName()))
                .sorted(Comparator.comparing(Class::getName))
                .toList();

        assertTrue(!controllers.isEmpty(), "Expected at least one @RestController");

        List<String> repositoryFields = new ArrayList<>();
        List<String> transactionalControllers = new ArrayList<>();
        List<String> transactionalMethods = new ArrayList<>();
        List<String> currentUserHelpers = new ArrayList<>();

        for (Class<?> controller : controllers) {
            if (controller.isAnnotationPresent(Transactional.class)) {
                transactionalControllers.add(controller.getSimpleName());
            }
            for (Field field : controller.getDeclaredFields()) {
                if (field.getType().getSimpleName().endsWith("Repository")) {
                    repositoryFields.add(controller.getSimpleName() + "." + field.getName());
                }
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Transactional.class)) {
                    transactionalMethods.add(controller.getSimpleName() + "#" + method.getName());
                }
                if (method.getName().equals("currentUser")) {
                    currentUserHelpers.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(repositoryFields.isEmpty(), "Controllers still holding repositories: " + repositoryFields);
        assertTrue(transactionalControllers.isEmpty(), "Controllers still annotated with @Transactional: " + transactionalControllers);
        assertTrue(transactionalMethods.isEmpty(), "Controller methods still annotated with @Transactional: " + transactionalMethods);
        assertTrue(currentUserHelpers.isEmpty(), "Controllers still declaring private currentUser helpers: " + currentUserHelpers);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }
}
