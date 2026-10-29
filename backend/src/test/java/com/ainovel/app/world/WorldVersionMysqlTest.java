package com.ainovel.app.world;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
class WorldVersionMysqlTest extends WorldVersionTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        p.add("spring.datasource.url",()->url);
        p.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        p.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        p.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        p.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
        p.add("spring.jpa.hibernate.ddl-auto",()->"none");p.add("spring.flyway.enabled",()->"true");p.add("spring.sql.init.mode",()->"never");
    }
}
