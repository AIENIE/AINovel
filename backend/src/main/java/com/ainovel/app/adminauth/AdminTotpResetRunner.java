package com.ainovel.app.adminauth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Host-only recovery command. It is never registered in the normal web profile.
 */
@Component
@Profile("admin-totp-reset")
public class AdminTotpResetRunner implements ApplicationRunner {
    private final AdminLocalAuthService authService;
    private final ConfigurableApplicationContext context;
    private final String confirmation;
    private final String approvalId;

    public AdminTotpResetRunner(
            AdminLocalAuthService authService,
            ConfigurableApplicationContext context,
            @Value("${ADMIN_TOTP_RESET_CONFIRM:}") String confirmation,
            @Value("${ADMIN_TOTP_RESET_APPROVAL_ID:}") String approvalId
    ) {
        this.authService = authService;
        this.context = context;
        this.confirmation = confirmation;
        this.approvalId = approvalId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"RESET".equals(confirmation)) {
            throw new IllegalStateException("ADMIN_TOTP_RESET_CONFIRM must equal RESET");
        }
        authService.resetFromApprovedOperation(approvalId);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
