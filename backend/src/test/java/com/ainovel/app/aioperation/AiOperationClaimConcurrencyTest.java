package com.ainovel.app.aioperation;

import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiOperationClaimConcurrencyTest {
    @Autowired AiOperationRepository repository;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;

    @Test void twoServiceInstancesExecuteTheSameDatabaseJobOnlyOnce() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager);
        User user = new User(); user.setUsername("claim-"+UUID.randomUUID()); user.setEmail(user.getUsername()+"@example.invalid"); user.setPasswordHash("test"); user.setRoles(Set.of("ROLE_USER"));
        user = users.saveAndFlush(user);
        AiOperationRun run = new AiOperationRun(); run.setUser(user); run.setOperationType("claim-test"); run.setStatus(AiOperationStatus.QUEUED); run.setPayloadJson("{}"); run.setTotalSteps(1);
        run = repository.saveAndFlush(run); UUID id = run.getId();
        AtomicInteger calls = new AtomicInteger(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AiOperationHandler handler = new AiOperationHandler() {
            public String type() { return "claim-test"; }
            public Object execute(AiOperationExecution execution) throws Exception {
                calls.incrementAndGet(); entered.countDown();
                if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                return AiOperationExecutionContext.complete(() -> Map.of("saved",true));
            }
        };
        AiOperationService a = new AiOperationService(repository,new ObjectMapper(),tx,Runnable::run,List.of(handler));
        AiOperationService b = new AiOperationService(repository,new ObjectMapper(),tx,Runnable::run,List.of(handler));
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> { await(barrier); ReflectionTestUtils.invokeMethod(a,"execute",id); });
            Future<?> second = pool.submit(() -> { await(barrier); ReflectionTestUtils.invokeMethod(b,"execute",id); });
            try { assertTrue(entered.await(5,TimeUnit.SECONDS)); } finally { release.countDown(); }
            first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,calls.get()); assertEquals(AiOperationStatus.SUCCEEDED,repository.findById(id).orElseThrow().getStatus());
    }
    private static void await(CyclicBarrier barrier) {
        try { barrier.await(5,TimeUnit.SECONDS); } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    @Test void cancellationOnSecondInstanceFencesTheFirstInstancesLateBusinessCommit() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager);
        User user = new User(); String originalName = "cancel-" + UUID.randomUUID();
        user.setUsername(originalName); user.setEmail(originalName + "@example.invalid");
        user.setPasswordHash("test"); user.setRoles(Set.of("ROLE_USER"));
        user = users.saveAndFlush(user); UUID userId = user.getId();
        AiOperationRun run = new AiOperationRun(); run.setUser(user); run.setOperationType("cancel-test");
        run.setStatus(AiOperationStatus.QUEUED); run.setPayloadJson("{}"); run.setTotalSteps(1);
        run = repository.saveAndFlush(run); UUID id = run.getId();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AiOperationHandler handler = new AiOperationHandler() {
            public String type() { return "cancel-test"; }
            public Object execute(AiOperationExecution execution) throws Exception {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                return AiOperationExecutionContext.complete(() -> {
                    User changed = users.findById(userId).orElseThrow();
                    changed.setUsername("must-not-be-written"); users.saveAndFlush(changed);
                    return Map.of("saved", true);
                });
            }
        };
        AiOperationService a = new AiOperationService(repository, new ObjectMapper(), tx, Runnable::run, List.of(handler));
        AiOperationService b = new AiOperationService(repository, new ObjectMapper(), tx, Runnable::run, List.of(handler));
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> task = pool.submit(() -> ReflectionTestUtils.invokeMethod(a, "execute", id));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                b.cancel(user, id);
            } finally { release.countDown(); }
            task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(AiOperationStatus.CANCELLED, repository.findById(id).orElseThrow().getStatus());
        assertEquals(originalName, users.findById(userId).orElseThrow().getUsername());
    }
}
