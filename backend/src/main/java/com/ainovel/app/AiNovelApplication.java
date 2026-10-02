package com.ainovel.app;

import com.ainovel.app.config.RuntimeEnvironmentPreflight;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class AiNovelApplication {
    public static void main(String[] args) {
        com.aienie.configpair.RuntimeConfiguration.initialize(args);
        launch(com.aienie.configpair.RuntimeConfiguration.getenv(), () -> com.aienie.configpair.RuntimeConfiguration.run(AiNovelApplication.class, args));
    }

    static void preflight(Map<String, String> environment) {
        RuntimeEnvironmentPreflight.validate(environment);
    }

    static void launch(Map<String, String> environment, Runnable springLauncher) {
        preflight(environment);
        springLauncher.run();
    }
}
