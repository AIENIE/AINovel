package com.ainovel.app.economy;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.economy.model.ConversionOrderStatus;
import com.ainovel.app.economy.model.CreditLedgerType;
import com.ainovel.app.economy.model.CreditConversionOrder;
import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.economy.model.RedeemCode;
import com.ainovel.app.economy.model.RedeemCodeUsage;
import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
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
    @Autowired
    private ProjectCreditAccountRepository accountRepository;
    @Autowired
    private ProjectCreditLedgerRepository ledgerRepository;
    @Autowired
    private RedeemCodeRepository redeemCodeRepository;
    @Autowired
    private RedeemCodeUsageRepository redeemCodeUsageRepository;

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
        throw new IllegalStateException("CHECKIN_DISABLED");
    }

    @Transactional
    public CreditChangeResult redeem(User user, String code) {
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
        long publicCredits = fetchPublicBalance(user);
        return new CreditChangeResult(true, redeemCode.getGrantAmount(), projectCredits, publicCredits, projectCredits + publicCredits, "REDEEM_SUCCESS");
    }

    @Transactional(readOnly = true)
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
    public AiChargeResult chargeAiUsage(User user, long inputTokens, long outputTokens, String referenceId) {
        long cost = calculateAiCost(inputTokens, outputTokens);
        ProjectCreditAccount account = accountForUpdate(user);
        if (account.getBalance() < cost) {
            throw new BusinessException("项目积分不足，请先兑换项目积分");
        }
        long nextBalance = account.getBalance() - cost;
        account.setBalance(nextBalance);
        accountRepository.save(account);
        syncUserCreditSnapshot(user, nextBalance);
        writeLedger(user, CreditLedgerType.AI_DEBIT, -cost, nextBalance, "AI_USAGE", referenceId, "AI 生成扣费", "ai:" + referenceId);
        return new AiChargeResult(cost, nextBalance);
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
            throw new BusinessException("该兑换请求正在处理或已失败，请更换 idempotencyKey");
        }

        long projectBefore = projectBalance(user);
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
            throw new BusinessException(remote.errorMessage() == null || remote.errorMessage().isBlank() ? "通用积分兑换失败" : remote.errorMessage());
        }

        long converted = remote.convertedProjectTokens() > 0 ? remote.convertedProjectTokens() : amount;
        ProjectCreditAccount account = accountForUpdate(user);
        long projectAfter = addProjectCredits(
                user,
                account,
                converted,
                CreditLedgerType.CONVERT_IN,
                "CONVERSION",
                orderNo,
                "通用积分兑换项目专属积分",
                "convert:" + idempotencyKey
        );
        long publicAfter = remote.publicRemainingTokens() >= 0 ? remote.publicRemainingTokens() : fetchPublicBalance(user);
        order.setConvertedAmount(converted);
        order.setStatus(ConversionOrderStatus.SUCCESS);
        order.setRemoteMessage(remote.errorMessage());
        order.setProjectAfter(projectAfter);
        order.setPublicAfter(publicAfter);
        conversionOrderRepository.save(order);
        syncUserCreditSnapshot(user, projectAfter);

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
        long publicCredits = fetchPublicBalance(target);
        return new CreditChangeResult(true, amount, nextBalance, publicCredits, nextBalance + publicCredits, "GRANT_SUCCESS");
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
