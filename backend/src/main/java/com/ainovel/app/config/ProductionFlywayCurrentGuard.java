package com.ainovel.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Ensures production startup validates the sealed Flyway chain without applying it. */
@Component
@Profile("production")
final class ProductionFlywayCurrentGuard implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        ProductionNovelMigrationMain.validateCurrentFromEnvironment();
    }
}
