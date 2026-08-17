package com.ainovel.app.economy;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.SafeLogThrowable;
import com.ainovel.app.economy.model.ConversionOrderStatus;
import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.economy.model.CreditLedgerType;
import com.ainovel.app.economy.model.CreditConversionOrder;
import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.economy.model.RedeemCode;
import com.ainovel.app.economy.model.RedeemCodeUsage;
import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
import com.ainovel.app.economy.repo.AiCreditReservationRepository;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import com.ainovel.app.economy.repo.ProjectCreditLedgerRepository;
import com.ainovel.app.economy.repo.RedeemCodeRepository;
import com.ainovel.app.economy.repo.RedeemCodeUsageRepository;
import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import com.ainovel.app.common.ApiStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

@Service
public class EconomyService {
    private static final Logger log = LoggerFactory.getLogger(EconomyService.class);

    private final BillingGrpcClient billingGrpcClient;
    private final UserRepository userRepository;
    private final CreditConversionOrderRepository conversionOrderRepository;
    @Autowired
    private ProjectCreditAccountRepository accountRepository;
    @Autowired
    private ProjectCreditLedgerRepository ledgerRepository;
    @Autowired
    private RedeemCodeRepository redeemCodeRepository;
    @Autowired
    private RedeemCodeUsageRepository redeemCodeUsageRepository;
    @Autowired
    private AiCreditReservationRepository aiReservationRepository;
    @Autowired
    private TransactionTemplate transactions;
    private final String conversionLeaseOwner = UUID.randomUUID().toString();

    public EconomyService(
            BillingGrpcClient billingGrpcClient,
            UserRepository userRepository,
            CreditConversionOrderRepository conversionOrderRepository
    ) {
        this.billingGrpcClient = billingGrpcClient;
        this.userRepository = userRepository;
        this.conversionOrderRepository = conversionOrderRepository;
    }

    public record BalanceSnapshot(long projectCredits, long publicCredits, long totalCredits) {
    }

    public record CreditChangeResult(
            boolean success,
            long points,
            long projectCredits,
            long publicCredits,
            long totalCredits,
            String message
    ) {
    }

    public record ConversionResult(
            String orderNo,
            long amount,
            long projectBefore,
            long projectAfter,
            long publicBefore,
            long publicAfter,
            long totalCredits
    ) {
    }

    public record AiChargeResult(long charged, long remainingProjectCredits) {
    }

    public record AiReservationStart(boolean replay, String content, long promptTokens,
                                     long completionTokens, long cacheTokens, long charged) {
        public static AiReservationStart reserved() {
            return new AiReservationStart(false, null, 0, 0, 0, 0);
        }
    }

    public record LedgerItem(
            String id,
            String userId,
            String username,
            String type,
            long delta,
            long balanceAfter,
            String referenceType,
            String referenceId,
            String description,
            Instant createdAt
    ) {
    }

    public record ConversionHistoryItem(
            String id,
            String orderNo,
            String userId,
            String username,
            long requestedAmount,
            long convertedAmount,
            long projectBefore,
            long projectAfter,
            long publicBefore,
            long publicAfter,
            String status,
            String remoteMessage,
            Instant createdAt
    ) {
    }

    public record RedeemCodeView(
            String id,
            String code,
            long grantAmount,
            Integer maxUses,
            int usedCount,
            Instant startsAt,
            Instant expiresAt,
            boolean enabled,
            boolean stackable,
            String description
    ) {
    }

    public CreditChangeResult redeem(User user, String code) {
        CreditChangeResult local = Objects.requireNonNull(transactions.execute(status -> redeemLocally(user, code)));
        long publicCredits = fetchPublicBalance(user);
        return new CreditChangeResult(true, local.points(), local.projectCredits(), publicCredits,
                local.projectCredits() + publicCredits, local.message());
    }

    private CreditChangeResult redeemLocally(User user, String code) {
        String normalized = normalizeRedeemCode(code);
        if (normalized.isBlank()) {
            throw new BusinessException("兑换码不能为空");
        }
        RedeemCode redeemCode = redeemCodeRepository.findByCode(normalized)
                .orElseThrow(() -> new BusinessException("兑换码无效"));
        Instant now = Instant.now();
        if (!redeemCode.isEnabled()) {
            throw new BusinessException("兑换码已停用");
        }
        if (redeemCode.getStartsAt() != null && redeemCode.getStartsAt().isAfter(now)) {
            throw new BusinessException("兑换码尚未生效");
        }
        if (redeemCode.getExpiresAt() != null && redeemCode.getExpiresAt().isBefore(now)) {
            throw new BusinessException("兑换码已过期");
        }
        if (redeemCode.getMaxUses() != null && redeemCode.getUsedCount() >= redeemCode.getMaxUses()) {
            throw new BusinessException("兑换码已用尽");
        }
        if (!redeemCode.isStackable() && redeemCodeUsageRepository.existsByRedeemCodeAndUser(redeemCode, user)) {
            throw new BusinessException("兑换码已使用");
        }
        ProjectCreditAccount account = accountForUpdate(user);
        long projectCredits = addProjectCredits(
                user,
                account,
                redeemCode.getGrantAmount(),
                CreditLedgerType.REDEEM_CODE,
                "REDEEM_CODE",
                redeemCode.getCode(),
                "兑换码 " + redeemCode.getCode(),
                "redeem:" + redeemCode.getCode() + ":" + user.getId()
        );
        redeemCode.setUsedCount(redeemCode.getUsedCount() + 1);
        redeemCodeRepository.save(redeemCode);
        RedeemCodeUsage usage = new RedeemCodeUsage();
        usage.setRedeemCode(redeemCode);
        usage.setUser(user);
        redeemCodeUsageRepository.save(usage);
        return new CreditChangeResult(true, redeemCode.getGrantAmount(), projectCredits, 0L, projectCredits, "REDEEM_SUCCESS");
    }

    public BalanceSnapshot currentBalance(User user) {
        long project = accountRepository.findByUser(user).map(ProjectCreditAccount::getBalance).orElse(Math.round(user.getCredits()));
        long pub = fetchPublicBalance(user);
        return new BalanceSnapshot(project, pub, project + pub);
    }

    @Transactional(readOnly = true)
    public long projectBalance(User user) {
        return accountRepository.findByUser(user).map(ProjectCreditAccount::getBalance).orElse(Math.round(user.getCredits()));
    }

    @Transactional
    public AiReservationStart reserveAiUsage(User user, long maximumCost, String referenceType,
                                             String referenceId, String idempotencyKey, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("缺少 Idempotency-Key");
        }
        user = lockUser(user);
        long reservationCost = Math.max(1L, maximumCost);
        AiCreditReservation reservation = aiReservationRepository
                .findByUserAndIdempotencyKey(user, idempotencyKey).orElse(null);
        if (reservation != null) {
            if (!Objects.equals(reservation.getRequestHash(), requestHash)) {
                throw new BusinessException("Idempotency-Key 已用于不同请求");
            }
            if (reservation.getStatus() == AiCreditReservation.Status.COMPLETED) {
                return new AiReservationStart(true, reservation.getResultContent(), reservation.getPromptTokens(),
                        reservation.getCompletionTokens(), reservation.getCacheTokens(), reservation.getSettledAmount());
            }
            if (reservation.getStatus() == AiCreditReservation.Status.RESERVED) {
                throw new BusinessException("相同 AI 请求正在处理中");
            }
        }

        ProjectCreditAccount account = accountForUpdate(user);
        if (account.getBalance() < reservationCost) {
            throw new BusinessException("项目积分不足，请先兑换项目积分");
        }
        long nextBalance = account.getBalance() - reservationCost;
        account.setBalance(nextBalance);
        accountRepository.save(account);
        syncUserCreditSnapshot(user, nextBalance);

        if (reservation == null) {
            reservation = new AiCreditReservation();
            reservation.setUser(user);
            reservation.setIdempotencyKey(idempotencyKey);
            reservation.setRequestHash(requestHash);
            reservation.setReferenceType(referenceType);
            reservation.setReferenceId(referenceId);
        }
        reservation.setReservedAmount(reservationCost);
        reservation.setAttemptCount(reservation.getAttemptCount() + 1);
        reservation.setSettledAmount(0);
        reservation.setStatus(AiCreditReservation.Status.RESERVED);
        reservation.setResultContent(null);
        aiReservationRepository.save(reservation);
        writeLedger(user, CreditLedgerType.AI_RESERVATION, -reservationCost, nextBalance,
                referenceType, referenceId, "AI 调用额度预留",
                "ai-reserve:" + idempotencyKey + ":" + reservation.getAttemptCount());
        return AiReservationStart.reserved();
    }

    @Transactional
    public AiChargeResult settleAiUsage(User user, String idempotencyKey, String content,
                                       long inputTokens, long outputTokens, long cacheTokens) {
        user = lockUser(user);
        AiCreditReservation reservation = aiReservationRepository
                .findByUserAndIdempotencyKey(user, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("AI reservation missing"));
        if (reservation.getStatus() == AiCreditReservation.Status.COMPLETED) {
            return new AiChargeResult(reservation.getSettledAmount(), projectBalance(user));
        }
        if (reservation.getStatus() != AiCreditReservation.Status.RESERVED) {
            throw new IllegalStateException("AI reservation is not active");
        }

        long actualCost = calculateAiCost(inputTokens, outputTokens);
        long adjustment = reservation.getReservedAmount() - actualCost;
        ProjectCreditAccount account = accountForUpdate(user);
        if (adjustment < 0 && account.getBalance() < -adjustment) {
            throw new IllegalStateException("AI usage exceeded reserved credit boundary");
        }
        if (adjustment != 0) {
            long nextBalance = account.getBalance() + adjustment;
            account.setBalance(nextBalance);
            accountRepository.save(account);
            syncUserCreditSnapshot(user, nextBalance);
            writeLedger(user, adjustment > 0 ? CreditLedgerType.AI_RESERVATION_RELEASE : CreditLedgerType.AI_DEBIT,
                    adjustment, nextBalance, reservation.getReferenceType(), reservation.getReferenceId(),
                    adjustment > 0 ? "释放未使用的 AI 预留额度" : "补充 AI 实际用量扣费",
                    "ai-settle:" + idempotencyKey + ":" + reservation.getAttemptCount());
        }
        reservation.setSettledAmount(actualCost);
        reservation.setResultContent(content);
        reservation.setPromptTokens(inputTokens);
        reservation.setCompletionTokens(outputTokens);
        reservation.setCacheTokens(cacheTokens);
        reservation.setStatus(AiCreditReservation.Status.COMPLETED);
        aiReservationRepository.save(reservation);
        return new AiChargeResult(actualCost, account.getBalance());
    }

    @Transactional
    public void releaseAiReservation(User user, String idempotencyKey) {
        user = lockUser(user);
        AiCreditReservation reservation = aiReservationRepository
                .findByUserAndIdempotencyKey(user, idempotencyKey).orElse(null);
        if (reservation == null || reservation.getStatus() != AiCreditReservation.Status.RESERVED) {
            return;
        }
        ProjectCreditAccount account = accountForUpdate(user);
        long nextBalance = account.getBalance() + reservation.getReservedAmount();
        account.setBalance(nextBalance);
        accountRepository.save(account);
        syncUserCreditSnapshot(user, nextBalance);
        reservation.setStatus(AiCreditReservation.Status.RELEASED);
        aiReservationRepository.save(reservation);
        writeLedger(user, CreditLedgerType.AI_RESERVATION_RELEASE, reservation.getReservedAmount(), nextBalance,
                reservation.getReferenceType(), reservation.getReferenceId(), "AI 调用失败，释放预留额度",
                "ai-release:" + idempotencyKey + ":" + reservation.getAttemptCount());
    }

    @Transactional
    public AiChargeResult chargeAiUsage(User user, long inputTokens, long outputTokens, String referenceId) {
        return chargeAiUsage(user, inputTokens, outputTokens, "AI_USAGE", referenceId, "ai:" + referenceId);
    }

    @Transactional
    public AiChargeResult chargeAiUsage(User user,
                                        long inputTokens,
                                        long outputTokens,
                                        String referenceType,
                                        String referenceId,
                                        String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ProjectCreditLedger existing = ledgerRepository.findFirstByUserAndIdempotencyKey(user, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return new AiChargeResult(Math.max(0L, -existing.getDelta()), existing.getBalanceAfter());
            }
        }
        long cost = calculateAiCost(inputTokens, outputTokens);
        ProjectCreditAccount account = accountForUpdate(user);
        if (account.getBalance() < cost) {
            throw new BusinessException("项目积分不足，请先兑换项目积分");
        }
        long nextBalance = account.getBalance() - cost;
        account.setBalance(nextBalance);
        accountRepository.save(account);
        syncUserCreditSnapshot(user, nextBalance);
        writeLedger(user, CreditLedgerType.AI_DEBIT, -cost, nextBalance,
                referenceType, referenceId, "AI 生成扣费", idempotencyKey);
        return new AiChargeResult(cost, nextBalance);
    }

    /**
     * Returns all local project-credit debits attached to one failed evaluation sample.
     * The account row lock and the refund idempotency key make repeated worker recovery safe.
     */
    @Transactional
    public long refundFailedEvaluation(User user, UUID evaluationSampleId) {
        String referenceId = String.valueOf(evaluationSampleId);
        String idempotencyKey = "ai-evaluation-refund:" + referenceId + ":" + user.getId();
        ProjectCreditAccount account = accountForUpdate(user);
        if (ledgerRepository.existsByUserAndIdempotencyKey(user, idempotencyKey)) {
            return 0L;
        }
        long refunded = ledgerRepository.findByUserAndReferenceTypeAndReferenceIdAndEntryType(
                        user, "G2_EVALUATION", referenceId, CreditLedgerType.AI_DEBIT)
                .stream()
                .mapToLong(item -> Math.max(0L, -item.getDelta()))
                .sum();
        if (refunded <= 0L) {
            return 0L;
        }
        long nextBalance = account.getBalance() + refunded;
        account.setBalance(nextBalance);
        accountRepository.save(account);
        syncUserCreditSnapshot(user, nextBalance);
        writeLedger(user, CreditLedgerType.AI_EVALUATION_REFUND, refunded, nextBalance,
                "G2_EVALUATION", referenceId, "G2 盲测样本生成失败退款", idempotencyKey);
        return refunded;
    }

    public ConversionResult convertPublicToProject(User user, long amount, String idempotencyKey) {
        if (amount <= 0) {
            throw new BusinessException("兑换积分必须大于 0");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("缺少 idempotencyKey");
        }
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            throw new BusinessException("当前账号未绑定统一用户，无法兑换通用积分");
        }

        CreditConversionOrder existing = conversionOrderRepository.findByUserAndIdempotencyKey(user, idempotencyKey).orElse(null);
        if (existing != null && existing.getStatus() == ConversionOrderStatus.SUCCESS) return conversionResult(existing);
        if (existing != null && existing.getStatus() == ConversionOrderStatus.FAILED) {
            throw new BusinessException("该兑换请求已被业务拒绝");
        }

        UUID orderId = existing == null ? createConversionOrder(user, amount, idempotencyKey) : existing.getId();
        return executeConversion(orderId, true);
    }

    private UUID createConversionOrder(User user, long amount, String idempotencyKey) {
        long publicBefore = fetchPublicBalance(user);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                CreditConversionOrder duplicate = conversionOrderRepository
                        .findByUserAndIdempotencyKey(user, idempotencyKey).orElse(null);
                if (duplicate != null) return duplicate.getId();
                ProjectCreditAccount account = accountForUpdate(user);
                CreditConversionOrder order = new CreditConversionOrder();
                order.setOrderNo("CVT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
                order.setUser(user);
                order.setIdempotencyKey(idempotencyKey);
                order.setRequestedAmount(amount);
                order.setConvertedAmount(0);
                order.setProjectBefore(account.getBalance());
                order.setProjectAfter(account.getBalance());
                order.setPublicBefore(publicBefore);
                order.setPublicAfter(publicBefore);
                order.setStatus(ConversionOrderStatus.PENDING);
                order.setRemoteRequestId(UUID.randomUUID().toString());
                order.setNextAttemptAt(Instant.now());
                return conversionOrderRepository.saveAndFlush(order).getId();
            }));
        } catch (DataIntegrityViolationException ex) {
            return conversionOrderRepository.findByUserAndIdempotencyKey(user, idempotencyKey)
                    .orElseThrow(() -> ex).getId();
        }
    }

    private ConversionResult executeConversion(UUID orderId, boolean reportPending) {
        ConversionClaim claim = transactions.execute(status -> {
            CreditConversionOrder order = conversionOrderRepository.findForUpdateById(orderId).orElse(null);
            if (order == null || order.getStatus() != ConversionOrderStatus.PENDING) return null;
            Instant now = Instant.now();
            if (order.getLeaseExpiresAt() != null && order.getLeaseExpiresAt().isAfter(now)) return null;
            if (order.getNextAttemptAt() != null && order.getNextAttemptAt().isAfter(now)) return null;
            order.setLeaseOwner(conversionLeaseOwner);
            order.setLeaseExpiresAt(now.plusSeconds(30));
            order.setAttemptCount(order.getAttemptCount() + 1);
            return new ConversionClaim(order.getId(), order.getUser().getId(), order.getUser().getRemoteUid(),
                    order.getRequestedAmount(), order.getRemoteRequestId());
        });
        if (claim == null) {
            CreditConversionOrder order = conversionOrderRepository.findById(orderId).orElseThrow();
            if (order.getStatus() == ConversionOrderStatus.SUCCESS) return conversionResult(order);
            if (reportPending) throw new ApiStatusException(HttpStatus.CONFLICT, "CONVERSION_PENDING");
            return null;
        }

        BillingGrpcClient.ConversionResult remote;
        try {
            remote = billingGrpcClient.convertPublicToProject(claim.remoteUid(), claim.amount(), claim.remoteRequestId());
        } catch (RuntimeException ex) {
            scheduleConversionRetry(orderId, ex.getClass().getSimpleName());
            if (reportPending) throw new ApiStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CONVERSION_PENDING_RETRY");
            return null;
        }
        if (!remote.success()) {
            if (isTransientRemoteFailure(remote.errorMessage())) {
                scheduleConversionRetry(orderId, remote.errorMessage());
                if (reportPending) throw new ApiStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CONVERSION_PENDING_RETRY");
                return null;
            }
            transactions.executeWithoutResult(status -> conversionOrderRepository.findForUpdateById(orderId).ifPresent(order -> {
                order.setStatus(ConversionOrderStatus.FAILED);
                order.setRemoteMessage(remote.errorMessage());
                order.setNextAttemptAt(null);
                clearConversionLease(order);
            }));
            throw new BusinessException(normalizeRemoteError(remote.errorMessage(), "通用积分兑换失败"));
        }

        return Objects.requireNonNull(transactions.execute(status -> {
            CreditConversionOrder order = conversionOrderRepository.findForUpdateById(orderId).orElseThrow();
            if (order.getStatus() == ConversionOrderStatus.SUCCESS) return conversionResult(order);
            User user = order.getUser();
            long converted = remote.convertedProjectTokens() > 0 ? remote.convertedProjectTokens() : order.getRequestedAmount();
            ProjectCreditAccount account = accountForUpdate(user);
            long projectAfter = addProjectCredits(user, account, converted, CreditLedgerType.CONVERT_IN,
                    "CONVERSION", order.getOrderNo(), "通用积分兑换项目专属积分",
                    "convert:" + order.getIdempotencyKey());
            order.setConvertedAmount(converted);
            order.setStatus(ConversionOrderStatus.SUCCESS);
            order.setRemoteMessage(remote.errorMessage());
            order.setProjectAfter(projectAfter);
            order.setPublicAfter(Math.max(0, remote.publicRemainingTokens()));
            order.setNextAttemptAt(null);
            clearConversionLease(order);
            return conversionResult(order);
        }));
    }

    @Scheduled(fixedDelayString = "${app.economy.conversion-reconcile-delay-ms:10000}")
    public void reconcilePendingConversions() {
        conversionOrderRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        ConversionOrderStatus.PENDING, Instant.now(), org.springframework.data.domain.PageRequest.of(0, 100))
                .forEach(order -> {
                    try { executeConversion(order.getId(), false); }
                    catch (RuntimeException ex) {
                        log.warn("event=conversion_reconcile_failed orderId={} errorType={}",
                                order.getId(), ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
                    }
                });
    }

    private void scheduleConversionRetry(UUID orderId, String message) {
        transactions.executeWithoutResult(status -> conversionOrderRepository.findForUpdateById(orderId).ifPresent(order -> {
            order.setRemoteMessage(message == null ? "TRANSIENT_REMOTE_FAILURE" : message);
            long delay = Math.min(300L, 1L << Math.min(8, Math.max(1, order.getAttemptCount())));
            order.setNextAttemptAt(Instant.now().plusSeconds(delay));
            clearConversionLease(order);
        }));
    }

    private boolean isTransientRemoteFailure(String message) {
        String value = message == null ? "" : message.toUpperCase(Locale.ROOT);
        return value.contains("UNAVAILABLE") || value.contains("DEADLINE_EXCEEDED")
                || value.contains("RESOURCE_EXHAUSTED") || value.contains("INTERNAL") || value.contains("UNKNOWN");
    }

    private void clearConversionLease(CreditConversionOrder order) {
        order.setLeaseOwner(null);
        order.setLeaseExpiresAt(null);
    }

    private ConversionResult conversionResult(CreditConversionOrder order) {
        return new ConversionResult(order.getOrderNo(), order.getConvertedAmount(), order.getProjectBefore(),
                order.getProjectAfter(), order.getPublicBefore(), order.getPublicAfter(),
                order.getProjectAfter() + order.getPublicAfter());
    }

    private record ConversionClaim(UUID orderId, UUID userId, long remoteUid, long amount, String remoteRequestId) { }

    public CreditChangeResult grantProjectCredits(User target, long amount, String reason, String operator) {
        CreditChangeResult local = Objects.requireNonNull(transactions.execute(status -> grantProjectCreditsLocally(target, amount, reason, operator)));
        long publicCredits = fetchPublicBalance(target);
        return new CreditChangeResult(true, local.points(), local.projectCredits(), publicCredits,
                local.projectCredits() + publicCredits, local.message());
    }

    private CreditChangeResult grantProjectCreditsLocally(User target, long amount, String reason, String operator) {
        if (amount <= 0) {
            throw new BusinessException("发放积分必须大于 0");
        }
        ProjectCreditAccount account = accountForUpdate(target);
        long nextBalance = addProjectCredits(
                target,
                account,
                amount,
                CreditLedgerType.ADMIN_GRANT,
                "ADMIN_GRANT",
                operator,
                reason == null || reason.isBlank() ? "AINovel admin grant by " + operator : reason,
                "grant:" + operator + ":" + Instant.now().toEpochMilli()
        );
        return new CreditChangeResult(true, amount, nextBalance, 0L, nextBalance, "GRANT_SUCCESS");
    }

    @Transactional(readOnly = true)
    public Page<LedgerItem> listLedger(User user, Pageable pageable) {
        return ledgerRepository.findByUserOrderByCreatedAtDesc(user, pageable).map(this::toLedgerItem);
    }

    @Transactional(readOnly = true)
    public Page<LedgerItem> listLedger(Pageable pageable) {
        return ledgerRepository.findByOrderByCreatedAtDesc(pageable).map(this::toLedgerItem);
    }

    @Transactional(readOnly = true)
    public Page<ConversionHistoryItem> listConversions(User user, Pageable pageable) {
        return conversionOrderRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(item -> new ConversionHistoryItem(
                        String.valueOf(item.getId()),
                        item.getOrderNo(),
                        String.valueOf(item.getUser().getId()),
                        item.getUser().getUsername(),
                        item.getRequestedAmount(),
                        item.getConvertedAmount(),
                        item.getProjectBefore(),
                        item.getProjectAfter(),
                        item.getPublicBefore(),
                        item.getPublicAfter(),
                        item.getStatus().name(),
                        item.getRemoteMessage(),
                        item.getCreatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public Page<ConversionHistoryItem> listConversions(Pageable pageable) {
        return conversionOrderRepository.findByOrderByCreatedAtDesc(pageable)
                .map(item -> new ConversionHistoryItem(
                        String.valueOf(item.getId()),
                        item.getOrderNo(),
                        String.valueOf(item.getUser().getId()),
                        item.getUser().getUsername(),
                        item.getRequestedAmount(),
                        item.getConvertedAmount(),
                        item.getProjectBefore(),
                        item.getProjectAfter(),
                        item.getPublicBefore(),
                        item.getPublicAfter(),
                        item.getStatus().name(),
                        item.getRemoteMessage(),
                        item.getCreatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public List<RedeemCodeView> listRedeemCodes() {
        return redeemCodeRepository.findAll().stream().map(this::toRedeemCodeView).toList();
    }

    @Transactional
    public RedeemCodeView createRedeemCode(
            String code,
            long grantAmount,
            Integer maxUses,
            Instant startsAt,
            Instant expiresAt,
            boolean enabled,
            boolean stackable,
            String description
    ) {
        String normalized = normalizeRedeemCode(code);
        if (normalized.isBlank()) {
            throw new BusinessException("兑换码不能为空");
        }
        if (grantAmount <= 0) {
            throw new BusinessException("兑换积分必须大于 0");
        }
        redeemCodeRepository.findByCode(normalized).ifPresent(existing -> {
            throw new BusinessException("兑换码已存在");
        });
        RedeemCode item = new RedeemCode();
        item.setCode(normalized);
        item.setGrantAmount(grantAmount);
        item.setMaxUses(maxUses);
        item.setStartsAt(startsAt);
        item.setExpiresAt(expiresAt);
        item.setEnabled(enabled);
        item.setStackable(stackable);
        item.setDescription(description);
        return toRedeemCodeView(redeemCodeRepository.save(item));
    }

    private ProjectCreditAccount accountForUpdate(User user) {
        ProjectCreditAccount account = accountRepository.findForUpdateByUserId(user.getId()).orElse(null);
        if (account != null) {
            return account;
        }
        ProjectCreditAccount created = new ProjectCreditAccount();
        created.setUser(user);
        created.setBalance(Math.max(0L, Math.round(user.getCredits())));
        return accountRepository.save(created);
    }

    private User lockUser(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException("用户不存在");
        }
        return userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private long addProjectCredits(
            User user,
            ProjectCreditAccount account,
            long amount,
            CreditLedgerType type,
            String referenceType,
            String referenceId,
            String description,
            String idempotencyKey
    ) {
        long nextBalance = account.getBalance() + amount;
        account.setBalance(nextBalance);
        accountRepository.save(account);
        syncUserCreditSnapshot(user, nextBalance);
        writeLedger(user, type, amount, nextBalance, referenceType, referenceId, description, idempotencyKey);
        return nextBalance;
    }

    private void writeLedger(
            User user,
            CreditLedgerType type,
            long delta,
            long balanceAfter,
            String referenceType,
            String referenceId,
            String description,
            String idempotencyKey
    ) {
        ProjectCreditLedger ledger = new ProjectCreditLedger();
        ledger.setUser(user);
        ledger.setEntryType(type);
        ledger.setDelta(delta);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        ledger.setDescription(description);
        ledger.setIdempotencyKey(idempotencyKey);
        ledgerRepository.save(ledger);
    }

    private LedgerItem toLedgerItem(ProjectCreditLedger item) {
        User user = item.getUser();
        return new LedgerItem(
                String.valueOf(item.getId()),
                user == null ? "" : String.valueOf(user.getId()),
                user == null ? "" : user.getUsername(),
                item.getEntryType() == null ? "" : item.getEntryType().name(),
                item.getDelta(),
                item.getBalanceAfter(),
                item.getReferenceType(),
                item.getReferenceId(),
                item.getDescription(),
                item.getCreatedAt()
        );
    }

    private RedeemCodeView toRedeemCodeView(RedeemCode item) {
        return new RedeemCodeView(
                String.valueOf(item.getId()),
                item.getCode(),
                item.getGrantAmount(),
                item.getMaxUses(),
                item.getUsedCount(),
                item.getStartsAt(),
                item.getExpiresAt(),
                item.isEnabled(),
                item.isStackable(),
                item.getDescription()
        );
    }

    private long calculateAiCost(long inputTokens, long outputTokens) {
        long totalTokens = Math.max(0L, inputTokens) + Math.max(0L, outputTokens);
        long unit = 100_000L;
        long cost = (totalTokens + unit - 1L) / unit;
        return Math.max(1L, cost);
    }

    private long fetchPublicBalance(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            return 0L;
        }
        RuntimeException lastError;
        try {
            return billingGrpcClient.publicBalance(remoteUid);
        } catch (RuntimeException ex) {
            lastError = ex;
        }

        long fallback = conversionOrderRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .map(item -> item.getPublicAfter() > 0 ? item.getPublicAfter() : item.getPublicBefore())
                .orElse(0L);
        RuntimeException failure = Objects.requireNonNull(lastError);
        log.warn("event=public_balance_fetch_failed remoteUid={} fallback={} errorType={}",
                remoteUid, fallback, failure.getClass().getSimpleName(), SafeLogThrowable.stackOnly(failure));
        return fallback;
    }

    private String normalizeRedeemCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private long remoteUidOrThrow(User user, String action) {
        Long remoteUid = user == null ? null : user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            throw new BusinessException("当前账号未绑定统一用户，无法" + action);
        }
        return remoteUid;
    }

    private void syncUserCreditSnapshot(User user, long projectCredits) {
        user.setCredits(projectCredits);
        userRepository.save(user);
    }

    private String normalizeRemoteError(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if (raw.contains("NEGATIVE_BALANCE") || raw.contains("INSUFFICIENT")) {
            return "项目积分不足，请先兑换项目积分";
        }
        return raw;
    }
}
