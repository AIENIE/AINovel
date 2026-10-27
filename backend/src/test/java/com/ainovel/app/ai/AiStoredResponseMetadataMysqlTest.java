package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiResponseMetadata;
import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.economy.repo.AiCreditReservationRepository;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import fireflychat.ai.v1.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses actual MySQL JDBC read-only transactions and repository queries; never calls an AI gateway. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({AiStoredResponseMetadata.class,AiStoredResponseMetadataMysqlTest.Beans.class})
class AiStoredResponseMetadataMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        properties.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
    }
    @TestConfiguration static class Beans {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean ExternalServiceProperties externalProperties(){return new ExternalServiceProperties();}
    }
    @Autowired AiStoredResponseMetadata reader;
    @Autowired AiCreditReservationRepository reservations;
    @Autowired AiValidationCallRepository calls;
    @Autowired AiValidationBudgetRepository budgets;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;

    @Test void readsCurrentAndHistoricMetadataInJdbcReadOnlyTransactionsWithoutLocksOrBackfill() throws Exception {
        var expected=AiResponseMetadata.reported("length",8192L,true);
        String serialized=json.writeValueAsString(expected),suffix=UUID.randomUUID().toString();
        String currentKey="metadata-new-"+suffix,historicKey="metadata-old-"+suffix;
        var write=new TransactionTemplate(manager);
        var owner=write.execute(status->{
            var user=new User();user.setUsername("metadata-"+suffix);user.setEmail("metadata-"+suffix+"@example.invalid");
            user.setPasswordHash("test");user.setRemoteUid(Math.abs(UUID.randomUUID().getMostSignificantBits()>>>1));users.saveAndFlush(user);
            reservations.saveAndFlush(reservation(user,currentKey,serialized));reservations.saveAndFlush(reservation(user,historicKey,null));
            var budget=new AiValidationBudget();budget.setId("metadata-test-"+suffix);budget.setCallLimit(1);budgets.saveAndFlush(budget);
            var request=ChatCompletionsRequest.newBuilder().setRequestId(historicKey).setUserId(user.getRemoteUid()).setProjectKey("ainovel").setModel("test-model").build();
            var response=ChatCompletionsResponse.newBuilder().setContent("").setModelKey("test-model").setPromptTokens(100).setCompletionTokens(8192)
                    .setCacheTokens(20).setFinishReason("length").setReasoningTokens(8192).setUsageAuthoritative(true).build();
            var call=new AiValidationCall();call.setRunId("metadata-test-"+suffix);call.setAttempt(1);call.setRequestId(historicKey);
            call.setModel("test-model");call.setStatus("COMPLETED");call.setSettlementState("SETTLED");call.setCreatedAt(Instant.now());call.setRequestHash(AiValidationCallBudget.hash(request));
            try {
                call.setRequestJson(json.writeValueAsString(Map.of("requestBase64",Base64.getEncoder().encodeToString(request.toByteArray()))));
                call.setResultJson(json.writeValueAsString(Map.of("responseBase64",Base64.getEncoder().encodeToString(response.toByteArray()))));
            }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalStateException(invalid);}
            calls.saveAndFlush(call);return user;
        });
        var read=new TransactionTemplate(manager);read.setReadOnly(true);
        for(String key:List.of(currentKey,historicKey)) {
            var actual=read.execute(status->{
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                try {assertTrue(DataSourceUtils.getConnection(dataSource).isReadOnly());}
                catch(SQLException failure){throw new IllegalStateException(failure);}
                return reader.find(owner,key);
            });
            assertEquals(expected,actual);
        }
        read.executeWithoutResult(status->{
            var current=reservations.findResultByUserAndIdempotencyKey(owner,currentKey).orElseThrow();
            var historic=reservations.findResultByUserAndIdempotencyKey(owner,historicKey).orElseThrow();
            assertEquals(serialized,current.getResultMetadataJson());assertNull(historic.getResultMetadataJson());
            for(var row:List.of(current,historic)) {
                assertEquals("",row.getResultContent());assertEquals(8192,row.getCompletionTokens());assertEquals(1,row.getSettledAmount());
                assertEquals(AiCreditReservation.Status.COMPLETED,row.getStatus());
            }
        });
    }
    private static AiCreditReservation reservation(User user,String key,String metadata) {
        var row=new AiCreditReservation();row.setUser(user);row.setIdempotencyKey(key);row.setRequestHash("frozen-hash");row.setReferenceType("AI_USAGE");
        row.setReferenceId(key);row.setReservedAmount(10);row.setSettledAmount(1);row.setAttemptCount(1);row.setStatus(AiCreditReservation.Status.COMPLETED);
        row.setResultContent("");row.setPromptTokens(100);row.setCompletionTokens(8192);row.setCacheTokens(20);row.setActualModelKey("test-model");row.setResultMetadataJson(metadata);return row;
    }
}
