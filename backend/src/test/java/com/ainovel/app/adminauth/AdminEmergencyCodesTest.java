package com.ainovel.app.adminauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminEmergencyCodesTest {
    AnnotationConfigApplicationContext context;
    AdminLocalAuthService auth;
    AdminSessionService sessions;
    AdminAuthStore store;
    AdminAuthCrypto crypto;
    JdbcTemplate jdbc;
    AdminLocalAuthProperties properties;
    static final String SECRET="JBSWY3DPEHPK3PXP";

    @BeforeEach void prepare() throws Exception {
        Config.mode="password";
        DriverManagerDataSource ds=new DriverManagerDataSource();
        String config=System.getProperty("adminEmergencyMysqlFile");
        if (config!=null) {
            var c=new ObjectMapper().readTree(Files.readString(Path.of(config)));
            ds.setUrl("jdbc:mysql://"+c.get("host").asText()+":"+c.get("port").asInt()+"/aienie_emergency_20261004_novel?useSSL=false&allowPublicKeyRetrieval=true");
            ds.setUsername(c.get("user").asText());ds.setPassword(c.get("password").asText());
        } else ds.setUrl("jdbc:h2:mem:emergency-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc=new JdbcTemplate(ds);
        for(String table:List.of("admin_emergency_challenge_bindings","admin_emergency_codes","admin_emergency_subjects","admin_emergency_migrations","admin_operation_proofs","admin_operation_challenges","admin_auth_audit","admin_sessions","admin_recovery_codes","admin_auth_challenges","admin_totp_credentials")) jdbc.execute("drop table if exists "+table);
        for(String name:List.of("V10__admin_totp_authentication.sql","V11__harden_admin_authentication.sql","V24__retained_admin_emergency_codes.sql")) migrate(name);
        properties=new AdminLocalAuthProperties();properties.setUsername("test-admin");properties.setPasswordHash(new BCryptPasswordEncoder().encode("test-password"));
        properties.setEncryptionKeys("v1:"+Base64.getEncoder().encodeToString(new byte[32]));properties.setActiveKeyVersion("v1");
        context=new AnnotationConfigApplicationContext();context.getBeanFactory().registerSingleton("dataSource",ds);context.getBeanFactory().registerSingleton("properties",properties);
        context.register(Config.class);context.refresh();
        auth=context.getBean(AdminLocalAuthService.class);sessions=context.getBean(AdminSessionService.class);store=context.getBean(AdminAuthStore.class);crypto=context.getBean(AdminAuthCrypto.class);
        store.insertCredential(AdminLocalAuthService.SUBJECT,crypto.encrypt(SECRET,"v1"),Instant.now());
    }
    void migrate(String name) throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration",name));
        if(System.getProperty("adminEmergencyMysqlFile")==null) sql=sql.replaceAll(" ENGINE=InnoDB[^;]*","");
        for(String statement:sql.split(";")) if(!statement.isBlank()) {
            if(System.getProperty("adminEmergencyMysqlFile")==null && statement.strip().startsWith("ALTER TABLE")) {
                String[] parts=statement.strip().split("\\s+",4);
                for(String clause:parts[3].split(",\\s*\\n")) jdbc.execute("ALTER TABLE "+parts[2]+" "+clause);
            } else jdbc.execute(statement);
        }
    }
    @AfterEach void close(){ if(context!=null) context.close(); }
    String full(){return sessions.issue(AdminLocalAuthService.SUBJECT,"FULL","PASSWORD",Instant.now(),null,"v1").sessionHash();}
    AdminLocalAuthService.LoginResult recover(String code) {
        var gate=auth.recoveryChallenge("test-admin","test-password","test");
        return auth.loginRecovery(gate.challengeId(),code,"test");
    }
    @Test void firstGetRepeatAndFillRetainOldCodes() {
        String session=full();var first=auth.getRecoveryCodes(session,null,"test");assertEquals(10,first.generatedCount());
        assertEquals(10,new HashSet<>(first.recoveryCodes()).size());assertTrue(first.recoveryCodes().stream().allMatch(c->c.matches("[0-9A-F]{8}(-[0-9A-F]{8}){3}")));
        var again=auth.getRecoveryCodes(session,null,"test");assertEquals(0,again.generatedCount());assertEquals(new HashSet<>(first.recoveryCodes()),new HashSet<>(again.recoveryCodes()));
        recover(first.recoveryCodes().getFirst().toLowerCase().replace("-"," "));
        var filled=auth.getRecoveryCodes(session,null,"test");assertEquals(1,filled.generatedCount());assertTrue(filled.recoveryCodes().containsAll(first.recoveryCodes().subList(1,10)));assertFalse(filled.recoveryCodes().contains(first.recoveryCodes().getFirst()));
    }
    @Test void concurrentGetAndSingleUseConsumption() throws Exception {
        String session=full();try(var pool=Executors.newFixedThreadPool(6)) {
            List<Callable<AdminLocalAuthService.RecoveryCodesResult>> jobs=new ArrayList<>();for(int i=0;i<6;i++) jobs.add(()->auth.getRecoveryCodes(session,null,"test"));
            var result=pool.invokeAll(jobs);int generated=0;for(var r:result) generated+=r.get().generatedCount();assertEquals(10,generated);assertEquals(10,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));
            String code=result.getFirst().get().recoveryCodes().getFirst();
            var gate1=auth.recoveryChallenge("test-admin","test-password","test");var gate2=auth.recoveryChallenge("test-admin","test-password","test");
            List<Callable<Boolean>> consumes=List.of(()->attemptRecovery(gate1.challengeId(),code),()->attemptRecovery(gate2.challengeId(),code));int success=0;for(var r:pool.invokeAll(consumes)) if(r.get()) success++;assertEquals(1,success);assertEquals(9,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));
        }
    }
    boolean attemptRecovery(String gate,String code){try{auth.loginRecovery(gate,code,"test");return true;}catch(AdminAuthenticationException ex){return false;}}
    @Test void cancelledRebindPreservesSeedAndSuccessfulRebindKeepsUnusedCodes() {
        String session=full();var codes=auth.getRecoveryCodes(session,null,"test");var recovered=recover(codes.recoveryCodes().getFirst());String recovery=sessions.resolve(recovered.token()).sessionHash();
        var pending=auth.startRebind(recovery,"test");assertEquals(SECRET,secret());assertFalse(sessions.isActive(recovery,"FULL"));
        assertThrows(AdminAuthenticationException.class,()->auth.confirmRebind(recovery,pending.challengeId(),"bad","test"));assertEquals(SECRET,secret());
        String otp=TotpService.code(pending.manualKey(),Instant.now().getEpochSecond()/30,6);
        var bound=auth.confirmRebind(recovery,pending.challengeId(),otp,"test");assertEquals("FULL",bound.sessionScope());assertTrue(bound.recoveryCodes().isEmpty());assertEquals(pending.manualKey(),secret());assertNull(sessions.resolve(recovered.token()));assertFalse(sessions.isActive(session,"FULL"));assertEquals(9,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));
        assertEquals("RECOVERY",recover(codes.recoveryCodes().get(1)).sessionScope());
    }
    String secret(){var c=store.credential(AdminLocalAuthService.SUBJECT).orElseThrow();return crypto.decrypt(c.encryptedSecret(),c.nonce(),c.keyVersion());}
    @Test void wrongPasswordExpiredAuthUsedCodeAndMigrationReplay() throws Exception {
        String session=full();var codes=auth.getRecoveryCodes(session,null,"test");assertThrows(AdminAuthenticationException.class,()->auth.recoveryChallenge("test-admin","wrong","test"));
        var recovery=recover(codes.recoveryCodes().getFirst());assertThrows(AdminAuthenticationException.class,()->recover(codes.recoveryCodes().getFirst()));
        migrate("V24__retained_admin_emergency_codes.sql");assertNotNull(sessions.resolve(recovery.token()));assertEquals(9,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));
        jdbc.update("update admin_sessions set expires_at=? where scope='RECOVERY'",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));assertNull(sessions.resolve(recovery.token()));
    }
    @Test void authenticatedCiphertextIsBoundToSubjectRecordAndBackend() {
        var e=crypto.encryptRecovery("admin-a","record-a","secret","v1");assertEquals("secret",crypto.decryptRecovery("admin-a","record-a",e.ciphertext(),e.nonce(),"v1"));
        assertThrows(IllegalStateException.class,()->crypto.decryptRecovery("admin-b","record-a",e.ciphertext(),e.nonce(),"v1"));assertThrows(IllegalStateException.class,()->crypto.decryptRecovery("admin-a","record-b",e.ciphertext(),e.nonce(),"v1"));
    }
    @Test void currentTotpRequiredAndReplayedOtpRejected() {
        Config.mode="totp";String session=full();
        assertThrows(AdminAuthenticationException.class,()->auth.getRecoveryCodes(session,"bad","test"));
        String code=TotpService.code(SECRET,Instant.now().getEpochSecond()/30,6);
        assertEquals(10,auth.getRecoveryCodes(session,code,"test").remaining());
        assertThrows(AdminAuthenticationException.class,()->auth.getRecoveryCodes(session,code,"test"));
    }
    @Test void rebindChallengeBoundToRecoverySessionAndAttemptLimit() {
        var codes=auth.getRecoveryCodes(full(),null,"test");
        String one=sessions.resolve(recover(codes.recoveryCodes().get(0)).token()).sessionHash();
        String two=sessions.resolve(recover(codes.recoveryCodes().get(1)).token()).sessionHash();
        var pending=auth.startRebind(one,"test");String code=TotpService.code(pending.manualKey(),Instant.now().getEpochSecond()/30,6);
        assertThrows(AdminAuthenticationException.class,()->auth.confirmRebind(two,pending.challengeId(),code,"test"));
        for(int i=0;i<5;i++) assertThrows(AdminAuthenticationException.class,()->auth.confirmRebind(one,pending.challengeId(),"bad","test"));
        assertThrows(AdminAuthenticationException.class,()->auth.confirmRebind(one,pending.challengeId(),code,"test"));assertEquals(SECRET,secret());assertEquals(8,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));
    }
    @Test void keyRotationAndNewServicePreserveCodeValues() {
        String session=full();var first=auth.getRecoveryCodes(session,null,"test");
        byte[] next=new byte[32];Arrays.fill(next,(byte)7);properties.setEncryptionKeys(properties.getEncryptionKeys()+",v2:"+Base64.getEncoder().encodeToString(next));properties.setActiveKeyVersion("v2");
        var rotatedCrypto=new AdminAuthCrypto(properties,context.getBean(AdminAuthPolicySource.class));
        var restarted=new AdminLocalAuthService(properties,context.getBean(AdminAuthPolicySource.class),store,rotatedCrypto,sessions,context.getBean(AdminRateLimiter.class),new BCryptPasswordEncoder());
        var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var second=tx.execute(status->restarted.getRecoveryCodes(session,null,"test"));assertNotNull(second);assertEquals(new HashSet<>(first.recoveryCodes()),new HashSet<>(second.recoveryCodes()));assertEquals(0,second.generatedCount());assertEquals(10,jdbc.queryForObject("select count(*) from admin_emergency_codes where key_version='v2'",Integer.class));
    }
    @Test void replenishmentCompetingWithRebindIsSerialized() throws Exception {
        String full=full();var first=auth.getRecoveryCodes(full,null,"test");String recovery=sessions.resolve(recover(first.recoveryCodes().getFirst()).token()).sessionHash();var pending=auth.startRebind(recovery,"test");String code=TotpService.code(pending.manualKey(),Instant.now().getEpochSecond()/30,6);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> get=()->{try{auth.getRecoveryCodes(full,null,"test");return true;}catch(AdminAuthenticationException ex){return false;}};
            Callable<Boolean> rebind=()->{auth.confirmRebind(recovery,pending.challengeId(),code,"test");return true;};
            var results=pool.invokeAll(List.of(get,rebind));assertTrue(results.get(1).get());results.get(0).get();
        }
        int count=store.activeRecoveryCount(AdminLocalAuthService.SUBJECT);assertTrue(count==9 || count==10);assertEquals(pending.manualKey(),secret());assertFalse(sessions.isActive(full,"FULL"));
    }
    @Test void migrationRetiresHistoryOnceAndKeepsOrdinaryFullSessions() throws Exception {
        String full=full();var codes=auth.getRecoveryCodes(full,null,"test");var recovery=recover(codes.recoveryCodes().getFirst());
        jdbc.update("delete from admin_emergency_migrations");jdbc.update("insert into admin_recovery_codes(subject_id,code_hash,created_at) values(?,?,?)",AdminLocalAuthService.SUBJECT,crypto.hashRecoveryCode("OLD-CODE"),java.sql.Timestamp.from(Instant.now()));
        migrate("V24__retained_admin_emergency_codes.sql");assertNull(sessions.resolve(recovery.token()));assertTrue(sessions.isActive(full,"FULL"));assertEquals(1,jdbc.queryForObject("select count(*) from admin_recovery_codes where replaced_at is not null",Integer.class));assertEquals(9,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));
        var fresh=recover(codes.recoveryCodes().get(1));migrate("V24__retained_admin_emergency_codes.sql");assertNotNull(sessions.resolve(fresh.token()));
    }
    @Test void firstEnrollmentGeneratesTenAndUnboundGetRequiresEnrollment() {
        jdbc.update("delete from admin_totp_credentials");assertThrows(AdminAuthenticationException.class,()->auth.getRecoveryCodes(full(),null,"test"));Config.mode="totp";
        var gate=auth.login("test-admin","test-password","test");assertEquals("ENROLLMENT_REQUIRED",gate.status());var pending=auth.startEnrollment(gate.challengeId(),"test");
        var result=auth.confirmEnrollment(pending.challengeId(),TotpService.code(pending.manualKey(),Instant.now().getEpochSecond()/30,6),"test");assertEquals(10,result.recoveryCodes().size());assertEquals(10,store.activeRecoveryCount(AdminLocalAuthService.SUBJECT));assertEquals("FULL",result.sessionScope());
    }
    @Configuration @EnableTransactionManagement static class Config {
        static String mode="password";
        @Bean PlatformTransactionManager transactions(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean AdminAuthPolicySource policy(){return new AdminAuthPolicySource(){public String env(){return "local";}public String authMode(){return mode;}};}
        @Bean AdminAuthStore store(JdbcTemplate jdbc){return new AdminAuthStore(jdbc);}
        @Bean AdminAuthCrypto crypto(AdminLocalAuthProperties p,AdminAuthPolicySource policy){return new AdminAuthCrypto(p,policy);}
        @Bean AdminSessionService sessions(AdminAuthStore store,AdminLocalAuthProperties p,AdminAuthPolicySource policy){return new AdminSessionService(store,p,policy);}
        @Bean AdminRateLimiter limiter(){var l=mock(AdminRateLimiter.class);when(l.allow(anyString(),anyInt(),any())).thenReturn(true);return l;}
        @Bean AdminLocalAuthService auth(AdminLocalAuthProperties p,AdminAuthPolicySource policy,AdminAuthStore store,AdminAuthCrypto crypto,AdminSessionService sessions,AdminRateLimiter limiter){return new AdminLocalAuthService(p,policy,store,crypto,sessions,limiter,new BCryptPasswordEncoder());}
    }
}
