package com.ainovel.app;

import com.ainovel.app.config.RuntimeEnvironmentPreflight;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class AiNovelApplication {
    public static void main(String[] args) {
        launch(System.getenv(), () -> SpringApplication.run(AiNovelApplication.class, args));
    }

    static void preflight(Map<String, String> environment) {
        RuntimeEnvironmentPreflight.validate(environment);
    }

    static void launch(Map<String, String> environment, Runnable springLauncher) {
        preflight(environment);
        springLauncher.run();
    }
}
