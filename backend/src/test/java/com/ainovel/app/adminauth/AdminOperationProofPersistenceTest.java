package com.ainovel.app.adminauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-proof-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class AdminOperationProofPersistenceTest {
    @Autowired
    private AdminOperationProofService service;
    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private AdminLocalAuthService authService;
    @MockBean
    private AdminRateLimiter limiter;

    private final AtomicBoolean transactionObserved = new AtomicBoolean();

    @BeforeEach
    void prepareExactAdminTables() {
        jdbc.execute("drop table if exists admin_operation_proofs");
        jdbc.execute("drop table if exists admin_operation_challenges");
        jdbc.execute("drop table if exists admin_auth_audit");
        jdbc.execute("""
                create table admin_operation_challenges (
                  challenge_hash char(64) not null primary key,
                  subject_id varchar(255) not null,
                  session_id char(64) not null,
                  action_key varchar(255) not null,
                  target_id varchar(512) not null,
                  source varchar(255),
                  expires_at datetime(6) not null,
                  attempt_count int not null default 0,
                  consumed_at datetime(6),
                  created_at datetime(6) not null default current_timestamp(6)
                )
                """);
        jdbc.execute("""
                create table admin_operation_proofs (
                  proof_hash char(64) not null primary key,
                  subject_id varchar(255) not null,
                  session_id char(64) not null,
                  action_key varchar(255) not null,
                  target_id varchar(512) not null,
                  expires_at datetime(6) not null,
                  consumed_at datetime(6),
                  created_at datetime(6) not null default current_timestamp(6)
                )
                """);
        jdbc.execute("""
                create table admin_auth_audit (
                  id bigint not null auto_increment primary key,
                  event_type varchar(64) not null,
                  subject_id varchar(255),
                  challenge_hash char(64),
                  source varchar(255),
                  result varchar(32) not null,
                  reason_code varchar(64),
                  created_at datetime(6) not null
                )
                """);
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(authService.verifyCurrentTotp("123456")).thenAnswer(invocation -> {
            transactionObserved.set(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
    }

    @Test
    void uuidStatusRouteCompletesChallengeAndVerifyWithoutOverflowingAuditReason() {
        String route = "/v1/admin/g2-evaluations/" + UUID.randomUUID() + "/status";
        String actionKey = "PUT:" + route;
        String targetId = route + "#" + "a".repeat(64);
        String sessionHash = "b".repeat(64);
        assertTrue(actionKey.length() > 64);

        AdminOperationProofService.OperationChallenge challenge = service.createChallenge(
                AdminLocalAuthService.SUBJECT, sessionHash, actionKey, targetId, "127.0.0.1"
        );
        AdminOperationProofService.ProofVerification proof = service.verifyAndIssue(
                challenge.challengeId(), sessionHash, "123456", "127.0.0.1"
        );

        assertTrue(transactionObserved.get());
        assertEquals(actionKey, jdbc.queryForObject(
                "select action_key from admin_operation_challenges", String.class));
        assertEquals(actionKey, jdbc.queryForObject(
                "select action_key from admin_operation_proofs", String.class));
        assertEquals(targetId, jdbc.queryForObject(
                "select target_id from admin_operation_proofs", String.class));
        assertEquals(List.of("CHALLENGE_CREATED", "PROOF_ISSUED"), jdbc.queryForList(
                "select reason_code from admin_auth_audit order by id", String.class));
        assertEquals(List.of(
                        AdminLocalAuthService.hash(challenge.challengeId()),
                        AdminLocalAuthService.hash(challenge.challengeId())
                ), jdbc.queryForList(
                        "select challenge_hash from admin_auth_audit order by id", String.class));
        assertTrue(service.consume(
                proof.proofToken(), AdminLocalAuthService.SUBJECT, sessionHash, actionKey, targetId
        ));
    }
}
