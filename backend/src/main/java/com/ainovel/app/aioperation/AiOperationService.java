package com.ainovel.app.aioperation;

import com.ainovel.app.ai.AiProgressContext;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class AiOperationService {
    private static final Logger log = LoggerFactory.getLogger(AiOperationService.class);
    private static final Set<AiOperationStatus> ACTIVE = EnumSet.of(
            AiOperationStatus.QUEUED, AiOperationStatus.RUNNING, AiOperationStatus.STREAMING,
            AiOperationStatus.RECOVERY_REQUIRED);

    private final AiOperationRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final Map<String, AiOperationHandler> handlers = new HashMap<>();
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<UUID, Future<?>> tasks = new ConcurrentHashMap<>();

    public AiOperationService(AiOperationRepository repository,
                              ObjectMapper objectMapper,
                              TransactionTemplate transactions,
                              @Qualifier("aiOperationExecutor") Executor executor,
                              List<AiOperationHandler> handlerList) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.executor = executor;
        handlerList.forEach(handler -> handlers.put(handler.type(), handler));
    }

    public AiOperationDtos.Accepted submit(User user, String type, String scopeType, UUID scopeId,
                                           Object payload, int totalSteps, String firstStep) {
        if (!handlers.containsKey(type)) throw new BusinessException("不支持的 AI 操作：" + type);
        if (scopeType != null && scopeId != null) {
            Optional<AiOperationRun> active = repository
                    .findFirstByUserIdAndScopeTypeAndScopeIdAndStatusInOrderByCreatedAtDesc(
                            user.getId(), scopeType, scopeId, ACTIVE);
            if (active.isPresent()) return new AiOperationDtos.Accepted(active.get().getId());
        }
        AiOperationRun run = new AiOperationRun();
        run.setUser(user);
        run.setOperationType(type);
        run.setScopeType(scopeType);
        run.setScopeId(scopeId);
        run.setStatus(AiOperationStatus.QUEUED);
        run.setCurrentStep(firstStep);
        run.setTotalSteps(Math.max(1, totalSteps));
        run.setCompletedSteps(0);
        run.setOutputTokens(0);
        run.setOutputTokensEstimated(true);
        run.setPayloadJson(write(payload));
        repository.save(run);
        dispatch(run.getId());
        return new AiOperationDtos.Accepted(run.getId());
    }

    public AiOperationDtos.Progress get(User user, UUID id) {
        return snapshot(requireOwned(id, user));
    }

    public AiOperationDtos.Progress active(User user, String scopeType, UUID scopeId) {
        return repository.findFirstByUserIdAndScopeTypeAndScopeIdAndStatusInOrderByCreatedAtDesc(
                        user.getId(), scopeType, scopeId, ACTIVE)
                .map(this::snapshot).orElse(null);
    }

    public SseEmitter events(User user, UUID id) {
        AiOperationRun run = requireOwned(id, user);
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        emitters.computeIfAbsent(id, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(id, emitter));
        emitter.onTimeout(() -> removeEmitter(id, emitter));
        emitter.onError(ignored -> removeEmitter(id, emitter));
        send(emitter, snapshot(run));
        if (isTerminal(run.getStatus())) {
            emitter.complete();
            removeEmitter(id, emitter);
        }
        return emitter;
    }

    public AiOperationDtos.Accepted retry(User user, UUID id) {
        AiOperationRun run = requireOwned(id, user);
        if (run.getStatus() != AiOperationStatus.FAILED && run.getStatus() != AiOperationStatus.RECOVERY_REQUIRED) {
            throw new BusinessException("当前 AI 操作不需要重试");
        }
        run.setStatus(AiOperationStatus.QUEUED);
        run.setErrorMessage(null);
        run.setStreamStarted(false);
        run.setCompletedSteps(0);
        run.setOutputTokens(0);
        run.setOutputTokensEstimated(true);
        run.setRequestId(null);
        run.setModelKey(null);
        run.setResultJson(null);
        run.setCompletedAt(null);
        repository.save(run);
        cancelTask(id);
        dispatch(id);
        return new AiOperationDtos.Accepted(id);
    }

    public void cancel(User user, UUID id) {
        AiOperationRun run = requireOwned(id, user);
        if (run.getStatus() == AiOperationStatus.SUCCEEDED || run.getStatus() == AiOperationStatus.FAILED) return;
        run.setStatus(AiOperationStatus.CANCELLED);
        run.setCompletedAt(Instant.now());
        repository.save(run);
        publish(id);
        cancelTask(id);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        transactions.executeWithoutResult(status -> {
            for (AiOperationRun run : repository.findByStatusIn(List.of(
                    AiOperationStatus.RUNNING, AiOperationStatus.STREAMING))) {
                run.setStatus(AiOperationStatus.RECOVERY_REQUIRED);
                run.setErrorMessage("服务重启时 AI 调用状态不确定，请确认后重试");
            }
        });
        repository.findByStatusIn(List.of(AiOperationStatus.QUEUED)).forEach(run -> dispatch(run.getId()));
    }

    private void dispatch(UUID id) {
        AtomicReference<FutureTask<Void>> reference = new AtomicReference<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                execute(id);
            } finally {
                tasks.remove(id, reference.get());
            }
            return null;
        });
        reference.set(task);
        if (tasks.putIfAbsent(id, task) == null) executor.execute(task);
    }

    private void cancelTask(UUID id) {
        Future<?> task = tasks.remove(id);
        if (task != null) task.cancel(true);
    }

    private void execute(UUID id) {
        Claim claim = transactions.execute(status -> {
            AiOperationRun run = repository.findById(id).orElse(null);
            if (run == null || run.getStatus() != AiOperationStatus.QUEUED) return null;
            run.setStatus(AiOperationStatus.RUNNING);
            run.setAttemptCount(run.getAttemptCount() + 1);
            run.setErrorMessage(null);
            run.setStreamStarted(false);
            run.getUser().getUsername();
            return new Claim(run.getId(), run.getUser(), run.getOperationType(), run.getPayloadJson());
        });
        if (claim == null) return;
        publish(id);
        AiOperationHandler handler = handlers.get(claim.type());
        if (handler == null) {
            fail(id, new IllegalStateException("AI operation handler is unavailable"));
            return;
        }
        AtomicLong lastUpdate = new AtomicLong();
        AtomicLong currentStepTokenBase = new AtomicLong();
        AiGatewayGrpcClient.StreamProgressListener listener = new AiGatewayGrpcClient.StreamProgressListener() {
            @Override public void onStarted(String requestId, String modelKey) {
                currentStepTokenBase.set(repository.findById(id).map(AiOperationRun::getOutputTokens).orElse(0L));
                update(id, run -> {
                    run.setStatus(AiOperationStatus.STREAMING);
                    run.setStreamStarted(true);
                    run.setRequestId(requestId);
                    run.setModelKey(modelKey);
                });
            }
            @Override public void onDelta(long outputTokens, boolean estimated) {
                long now = System.nanoTime();
                long previous = lastUpdate.get();
                if (now - previous < 250_000_000L && outputTokens > 0) return;
                if (lastUpdate.compareAndSet(previous, now)) {
                    update(id, run -> {
                        run.setOutputTokens(currentStepTokenBase.get() + outputTokens);
                        run.setOutputTokensEstimated(estimated);
                    });
                }
            }
            @Override public void onCompleted(long completionTokens, long promptTokens, long cacheTokens) {
                long completedTotal = currentStepTokenBase.get() + completionTokens;
                currentStepTokenBase.set(completedTotal);
                update(id, run -> {
                    run.setOutputTokens(completedTotal);
                    run.setOutputTokensEstimated(false);
                    run.setStatus(AiOperationStatus.RUNNING);
                });
            }
        };
        try {
            var authorities = Optional.ofNullable(claim.user().getRoles()).orElseGet(Set::of).stream()
                    .map(SimpleGrantedAuthority::new).toList();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    claim.user().getUsername(), null, authorities));
            SecurityContextHolder.setContext(context);
            Object result;
            try {
                result = AiProgressContext.withListener(listener, () -> {
                    try {
                        return handler.execute(new AiOperationExecution(id, claim.user(), claim.payloadJson(), objectMapper,
                                (label, completed, total) -> update(id, run -> {
                                    run.setCurrentStep(label);
                                    run.setCompletedSteps(Math.max(0, completed));
                                    run.setTotalSteps(Math.max(1, total));
                                    run.setOutputTokens(0);
                                    run.setOutputTokensEstimated(true);
                                })));
                    } catch (RuntimeException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } finally {
                SecurityContextHolder.clearContext();
            }
            update(id, run -> {
                if (run.getStatus() == AiOperationStatus.CANCELLED) return;
                run.setStatus(AiOperationStatus.SUCCEEDED);
                run.setCompletedSteps(run.getTotalSteps());
                run.setResultJson(write(result));
                run.setCompletedAt(Instant.now());
                run.setCurrentStep("已完成");
            });
            log.info("AI operation completed operationId={} type={} attempt={}", id, claim.type(),
                    getAttemptCount(id));
        } catch (RuntimeException ex) {
            fail(id, ex);
        }
    }

    private int getAttemptCount(UUID id) {
        return repository.findById(id).map(AiOperationRun::getAttemptCount).orElse(0);
    }

    private void fail(UUID id, RuntimeException failure) {
        update(id, run -> {
            if (run.getStatus() == AiOperationStatus.CANCELLED) return;
            run.setStatus(run.isStreamStarted() ? AiOperationStatus.RECOVERY_REQUIRED : AiOperationStatus.FAILED);
            run.setErrorMessage(truncate(failure.getMessage()));
            run.setCompletedAt(Instant.now());
        });
        log.warn("AI operation failed operationId={} reason={}", id, failure.getMessage());
    }

    private void update(UUID id, Consumer<AiOperationRun> mutation) {
        transactions.executeWithoutResult(status -> repository.findById(id).ifPresent(mutation));
        publish(id);
    }

    private void publish(UUID id) {
        Set<SseEmitter> listeners = emitters.get(id);
        if (listeners == null || listeners.isEmpty()) return;
        AiOperationDtos.Progress progress = repository.findById(id).map(this::snapshot).orElse(null);
        if (progress == null) return;
        listeners.forEach(emitter -> send(emitter, progress));
        if (isTerminal(progress.status())) {
            listeners.forEach(SseEmitter::complete);
            emitters.remove(id);
        }
    }

    private void send(SseEmitter emitter, AiOperationDtos.Progress progress) {
        try {
            emitter.send(SseEmitter.event().name("progress").id(progress.updatedAt() == null
                    ? progress.id().toString() : progress.updatedAt().toString()).data(progress));
        } catch (IOException | IllegalStateException ex) {
            removeEmitter(progress.id(), emitter);
        }
    }

    private void removeEmitter(UUID id, SseEmitter emitter) {
        Set<SseEmitter> listeners = emitters.get(id);
        if (listeners != null) {
            listeners.remove(emitter);
            if (listeners.isEmpty()) emitters.remove(id);
        }
    }

    private boolean isTerminal(AiOperationStatus status) {
        return EnumSet.of(AiOperationStatus.SUCCEEDED, AiOperationStatus.FAILED,
                AiOperationStatus.RECOVERY_REQUIRED, AiOperationStatus.CANCELLED).contains(status);
    }

    private AiOperationRun requireOwned(UUID id, User user) {
        return repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("AI 操作不存在"));
    }

    private AiOperationDtos.Progress snapshot(AiOperationRun run) {
        int remaining = Math.max(0, run.getTotalSteps() - run.getCompletedSteps());
        return new AiOperationDtos.Progress(run.getId(), run.getOperationType(), run.getScopeType(), run.getScopeId(),
                run.getStatus(), run.getCurrentStep(), run.getTotalSteps(), run.getCompletedSteps(), remaining,
                run.getOutputTokens(), run.isOutputTokensEstimated(), run.getAttemptCount(), run.getResultJson(),
                run.getErrorMessage(), run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt());
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new BusinessException("AI 操作数据序列化失败"); }
    }

    private String truncate(String message) {
        String value = message == null || message.isBlank() ? "AI 操作失败" : message;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record Claim(UUID id, User user, String type, String payloadJson) {}
}
