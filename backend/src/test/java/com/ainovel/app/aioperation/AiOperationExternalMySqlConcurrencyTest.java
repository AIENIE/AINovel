package com.ainovel.app.aioperation;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Opt-in: the caller provisions and removes an isolated, empty random schema.
 * No production configuration is loaded; Flyway uses only the supplied audit schema.
 */
@EnabledIfEnvironmentVariable(named = "AIENIE_AUDIT_MYSQL_URL", matches = "jdbc:mysql:.*")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiOperationExternalMySqlConcurrencyTest extends AiOperationClaimConcurrencyTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String url = System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("Audit MySQL must use an isolated aienie_novel_audit_test_* schema");
        String user = System.getenv("AIENIE_AUDIT_MYSQL_USERNAME");
        String password = System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD");
        if (user == null || password == null) throw new IllegalArgumentException("Audit MySQL credentials are required");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
