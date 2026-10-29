package com.ainovel.app.ai;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none",
        "app.ai.validation.run-id=money-test","app.ai.validation.monetary-enabled=true","app.ai.validation.maximum-cost-cny=30","app.ai.validation.maximum-calls=24"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class AiValidationMoneyMysqlTest extends AiValidationMoneyBudgetTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null || !url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?")) throw new IllegalArgumentException("Isolated schema required");
        r.add("spring.datasource.url",()->url); r.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));r.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
    }
}
