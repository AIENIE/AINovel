package com.ainovel.app.economy;

import com.ainovel.app.economy.model.ConversionOrderStatus;
import com.ainovel.app.economy.model.CreditConversionOrder;
import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

@Service
public class EconomyService {
    private static final Logger log = LoggerFactory.getLogger(EconomyService.class);

    private final BillingGrpcClient billingGrpcClient;
    private final UserRepository userRepository;
    private final CreditConversionOrderRepository conversionOrderRepository;

    public EconomyService(
            BillingGrpcClient billingGrpcClient,
            UserRepository userRepository,
            CreditConversionOrderRepository conversionOrderRepository
    ) {
        this.billingGrpcClient = billingGrpcClient;
        this.userRepository = userRepository;
        this.conversionOrderRepository = conversionOrderRepository;
    }

    public record BalanceSnapshot(long projectCredits, long publicCredits, long totalCredits, Instant lastCheckInAt) {
    }

    public record CreditChangeResult(
            boolean success,
            long points,
            long projectCredits,
            long publicCredits,
            long totalCredits,
            Instant lastCheckInAt,
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

    @Transactional
    public CreditChangeResult checkIn(User user) {
        long remoteUid = remoteUidOrThrow(user, "执行签到");
        BillingGrpcClient.CheckinResult remote = billingGrpcClient.checkin(remoteUid);
        long projectCredits = billingGrpcClient.projectBalance(remoteUid);
        long publicCredits = fetchPublicBalance(user);
        Instant lastCheckInAt = remote.success() ? Instant.now() : fetchLastCheckInAt(user);
        syncUserCreditSnapshot(user, projectCredits, lastCheckInAt);
        String message = remote.errorMessage();
        if (message == null || message.isBlank()) {
            message = remote.alreadyCheckedIn() ? "TODAY_ALREADY_CHECKED_IN" : "CHECKIN_SUCCESS";
        }
        return new CreditChangeResult(
                remote.success(),
                remote.tokensGranted(),
                projectCredits,
                publicCredits,
                projectCredits + publicCredits,
                lastCheckInAt,
                message
        );
    }

    @Transactional
    public CreditChangeResult redeem(User user, String code) {
        String normalized = normalizeRedeemCode(code);
        if (normalized.isBlank()) {
            throw new RuntimeException("兑换码不能为空");
        }
        long remoteUid = remoteUidOrThrow(user, "兑换积分");
        BillingGrpcClient.RedeemResult remote = billingGrpcClient.redeem(remoteUid, normalized);
        if (!remote.success()) {
            throw new RuntimeException(normalizeRemoteError(remote.errorMessage(), "兑换码无效"));
        }
        long projectCredits = billingGrpcClient.projectBalance(remoteUid);
        long publicCredits = fetchPublicBalance(user);
        syncUserCreditSnapshot(user, projectCredits, user.getLastCheckInAt());
        return new CreditChangeResult(
                true,
                remote.tokensGranted(),
                projectCredits,
                publicCredits,
                projectCredits + publicCredits,
                user.getLastCheckInAt(),
                "REDEEM_SUCCESS"
        );
    }

    @Transactional(readOnly = true)
    public BalanceSnapshot currentBalance(User user) {
        long remoteUid = remoteUidOrThrow(user, "查询积分");
        long project = billingGrpcClient.projectBalance(remoteUid);
        long pub = fetchPublicBalance(user);
        return new BalanceSnapshot(project, pub, project + pub, fetchLastCheckInAt(user));
    }

    @Transactional(readOnly = true)
    public Instant fetchLastCheckInAt(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            return user.getLastCheckInAt();
        }
        try {
            BillingGrpcClient.CheckinStatus status = billingGrpcClient.checkinStatus(remoteUid);
            return status.lastCheckinAt() == null ? user.getLastCheckInAt() : status.lastCheckinAt();
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch checkin status for remoteUid={}, fallback to local snapshot reason={}", remoteUid, ex.getMessage());
            return user.getLastCheckInAt();
        }
    }

    @Transactional(readOnly = true)
    public long projectBalance(User user) {
        return billingGrpcClient.projectBalance(remoteUidOrThrow(user, "查询项目积分"));
    }

    @Transactional
    public AiChargeResult chargeAiUsage(User user, long inputTokens, long outputTokens, String referenceId) {
        long cost = calculateAiCost(inputTokens, outputTokens);
        try {
            BillingGrpcClient.UsageDeductionResult remote = billingGrpcClient.deductUsage(
                    remoteUidOrThrow(user, "扣减 AI 用量"),
                    cost,
                    0L,
                    referenceId
            );
            syncUserCreditSnapshot(user, remote.remainingProjectCredits(), user.getLastCheckInAt());
            return new AiChargeResult(remote.chargedCredits(), remote.remainingProjectCredits());
        } catch (RuntimeException ex) {
            throw new RuntimeException(normalizeRemoteError(ex.getMessage(), "项目积分不足，请先兑换项目积分"), ex);
        }
    }

    public ConversionResult convertPublicToProject(User user, long amount, String idempotencyKey) {
        if (amount <= 0) {
            throw new RuntimeException("兑换积分必须大于 0");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new RuntimeException("缺少 idempotencyKey");
        }
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            throw new RuntimeException("当前账号未绑定统一用户，无法兑换通用积分");
        }

        CreditConversionOrder existing = conversionOrderRepository.findByUserAndIdempotencyKey(user, idempotencyKey).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ConversionOrderStatus.SUCCESS) {
                long projectAfter = existing.getProjectAfter();
                long publicAfter = existing.getPublicAfter();
                return new ConversionResult(
                        existing.getOrderNo(),
                        existing.getConvertedAmount(),
                        existing.getProjectBefore(),
                        projectAfter,
                        existing.getPublicBefore(),
                        publicAfter,
                        projectAfter + publicAfter
                );
            }
            throw new RuntimeException("该兑换请求正在处理或已失败，请更换 idempotencyKey");
        }

        long projectBefore = billingGrpcClient.projectBalance(remoteUid);
        long publicBefore = fetchPublicBalance(user);

        String orderNo = "CVT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        CreditConversionOrder order = new CreditConversionOrder();
        order.setOrderNo(orderNo);
        order.setUser(user);
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestedAmount(amount);
        order.setConvertedAmount(0);
        order.setProjectBefore(projectBefore);
        order.setProjectAfter(projectBefore);
        order.setPublicBefore(publicBefore);
        order.setPublicAfter(publicBefore);
        order.setStatus(ConversionOrderStatus.PENDING);
        order.setRemoteRequestId(UUID.randomUUID().toString());
        conversionOrderRepository.save(order);

        BillingGrpcClient.ConversionResult remote;
        try {
            remote = billingGrpcClient.convertPublicToProject(remoteUid, amount, order.getRemoteRequestId());
        } catch (RuntimeException ex) {
            order.setStatus(ConversionOrderStatus.FAILED);
            order.setRemoteMessage(ex.getMessage());
            conversionOrderRepository.save(order);
            throw ex;
        }
        if (!remote.success()) {
            order.setStatus(ConversionOrderStatus.FAILED);
            order.setRemoteMessage(remote.errorMessage());
            conversionOrderRepository.save(order);
            throw new RuntimeException(remote.errorMessage() == null || remote.errorMessage().isBlank() ? "通用积分兑换失败" : remote.errorMessage());
        }

        long converted = remote.convertedProjectTokens() > 0 ? remote.convertedProjectTokens() : amount;
        long projectAfter = billingGrpcClient.projectBalance(remoteUid);
        long publicAfter = remote.publicRemainingTokens() >= 0 ? remote.publicRemainingTokens() : fetchPublicBalance(user);
        order.setConvertedAmount(converted);
        order.setStatus(ConversionOrderStatus.SUCCESS);
        order.setRemoteMessage(remote.errorMessage());
        order.setProjectAfter(projectAfter);
        order.setPublicAfter(publicAfter);
        conversionOrderRepository.save(order);
        syncUserCreditSnapshot(user, projectAfter, user.getLastCheckInAt());

        return new ConversionResult(
                orderNo,
                converted,
                order.getProjectBefore(),
                order.getProjectAfter(),
                order.getPublicBefore(),
                order.getPublicAfter(),
                order.getProjectAfter() + order.getPublicAfter()
        );
    }

    @Transactional
    public CreditChangeResult grantProjectCredits(User target, long amount, String reason, String operator) {
        if (amount <= 0) {
            throw new RuntimeException("发放积分必须大于 0");
        }
        BillingGrpcClient.ProjectGrantResult remote = billingGrpcClient.grantProjectCredits(
                remoteUidOrThrow(target, "管理员发放项目积分"),
                amount,
                reason == null || reason.isBlank() ? "AINovel admin grant by " + operator : reason
        );
        long nextBalance = remote.projectCredits();
        syncUserCreditSnapshot(target, nextBalance, target.getLastCheckInAt());
        long publicCredits = fetchPublicBalance(target);
        return new CreditChangeResult(true, amount, nextBalance, publicCredits, nextBalance + publicCredits, target.getLastCheckInAt(), "GRANT_SUCCESS");
    }

    @Transactional(readOnly = true)
    public Page<LedgerItem> listLedger(User user, Pageable pageable) {
        BillingGrpcClient.LedgerPage remote = billingGrpcClient.listLedgerEntries(
                remoteUidOrThrow(user, "查询项目积分流水"),
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
        List<LedgerItem> items = remote.entries().stream()
                .map(item -> new LedgerItem(
                        item.id(),
                        String.valueOf(user.getId()),
                        user.getUsername(),
                        item.type(),
                        item.delta(),
                        item.balanceAfter(),
                        item.referenceType(),
                        item.referenceId(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageImpl<>(items, pageable, remote.totalElements());
    }

    @Transactional(readOnly = true)
    public Page<LedgerItem> listLedger(Pageable pageable) {
        throw new IllegalStateException("PAY_SERVICE_ADMIN_LEDGER_NOT_CONFIGURED");
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
        throw new IllegalStateException("PAY_SERVICE_ADMIN_REDEEM_CODE_NOT_CONFIGURED");
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
        throw new IllegalStateException("PAY_SERVICE_ADMIN_REDEEM_CODE_NOT_CONFIGURED");
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
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return billingGrpcClient.publicBalance(remoteUid);
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }

        long fallback = conversionOrderRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .map(item -> item.getPublicAfter() > 0 ? item.getPublicAfter() : item.getPublicBefore())
                .orElse(0L);
        String reason = lastError == null ? "unknown" : lastError.getMessage();
        log.warn("Failed to fetch public balance for remoteUid={}, fallback={} reason={}", remoteUid, fallback, reason);
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
            throw new RuntimeException("当前账号未绑定统一用户，无法" + action);
        }
        return remoteUid;
    }

    private void syncUserCreditSnapshot(User user, long projectCredits, Instant lastCheckInAt) {
        user.setCredits(projectCredits);
        if (lastCheckInAt != null) {
            user.setLastCheckInAt(lastCheckInAt);
        }
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
