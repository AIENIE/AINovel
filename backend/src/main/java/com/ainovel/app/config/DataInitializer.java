package com.ainovel.app.config;

import com.ainovel.app.settings.model.GlobalSettings;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    @Autowired
    private GlobalSettingsRepository globalSettingsRepository;
    @Value("${spring.mail.host:}")
    private String defaultSmtpHost;
    @Value("${spring.mail.port:587}")
    private Integer defaultSmtpPort;
    @Value("${spring.mail.username:}")
    private String defaultSmtpUsername;
    @Value("${spring.mail.password:}")
    private String defaultSmtpPassword;

    @Override
    public void run(String... args) {
        GlobalSettings global = loadGlobalSettingsWithRetry();
        if (global.getId() == null) {
            global.setRegistrationEnabled(true);
            global.setMaintenanceMode(false);
            global.setCheckInMinPoints(10);
            global.setCheckInMaxPoints(50);
            if (defaultSmtpHost != null && !defaultSmtpHost.isBlank()) global.setSmtpHost(defaultSmtpHost);
            if (defaultSmtpPort != null) global.setSmtpPort(defaultSmtpPort);
            if (defaultSmtpUsername != null && !defaultSmtpUsername.isBlank()) global.setSmtpUsername(defaultSmtpUsername);
            if (defaultSmtpPassword != null && !defaultSmtpPassword.isBlank()) global.setSmtpPassword(defaultSmtpPassword);
            globalSettingsRepository.save(global);
        }
    }

    private GlobalSettings loadGlobalSettingsWithRetry() {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return globalSettingsRepository.findTopByOrderByUpdatedAtDesc().orElseGet(GlobalSettings::new);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == 3) {
                    break;
                }
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw lastFailure;
    }
}
