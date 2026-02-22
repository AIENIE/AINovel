package com.ainovel.app.economy;

import com.ainovel.app.economy.model.CheckInRecord;
import com.ainovel.app.economy.model.ConversionOrderStatus;
import com.ainovel.app.economy.model.CreditConversionOrder;
import com.ainovel.app.economy.model.CreditLedgerType;
import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.economy.model.RedeemCode;
import com.ainovel.app.economy.model.RedeemCodeUsage;
import com.ainovel.app.economy.repo.CheckInRecordRepository;
import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import com.ainovel.app.economy.repo.ProjectCreditLedgerRepository;
import com.ainovel.app.economy.repo.RedeemCodeRepository;
import com.ainovel.app.economy.repo.RedeemCodeUsageRepository;
import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EconomyService {
    private static final Logger log = LoggerFactory.getLogger(EconomyService.class);
    private static final ZoneId CHECKIN_ZONE = ZoneId.of("Asia/Shanghai");

    private final BillingGrpcClient billingGrpcClient;
    private final UserRepository userRepository;
    private final ProjectCreditAccountRepository accountRepository;
    private final ProjectCreditLedgerRepository ledgerRepository;
    private final CheckInRecordRepository checkInRecordRepository;
    private final RedeemCodeRepository redeemCodeRepository;
    private final RedeemCodeUsageRepository redeemCodeUsageRepository;
    private final CreditConversionOrderRepository conversionOrderRepository;
    private final com.ainovel.app.settings.SettingsService settingsService;
    private final TransactionTemplate transactionTemplate;

    public EconomyService(
            BillingGrpcClient billingGrpcClient,
            UserRepository userRepository,
            ProjectCreditAccountRepository accountRepository,
            ProjectCreditLedgerRepository ledgerRepository,
            CheckInRecordRepository checkInRecordRepository,
            RedeemCodeRepository redeemCodeRepository,
            RedeemCodeUsageRepository redeemCodeUsageRepository,
            CreditConversionOrderRepository conversionOrderRepository,
            com.ainovel.app.settings.SettingsService settingsService,
            org.springframework.transaction.PlatformTransactionManager transactionManager
    ) {
        this.billingGrpcClient = billingGrpcClient;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.checkInRecordRepository = checkInRecordRepository;
        this.redeemCodeRepository = redeemCodeRepository;
        this.redeemCodeUsageRepository = redeemCodeUsageRepository;
        this.conversionOrderRepository = conversionOrderRepository;
        this.settingsService = settingsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        ProjectCreditAccount account = getOrCreateAccountForUpdate(user);
        LocalDate today = LocalDate.now(CHECKIN_ZONE);
        if (checkInRecordRepository.existsByUserAndCheckinDate(user, today)) {
            long publicCredits = fetchPublicBalance(user);
            return new CreditChangeResult(
                    false,
                    0,
                    account.getBalance(),
                    publicCredits,
                    account.getBalance() + publicCredits,
                    user.getLastCheckInAt(),
                    "TODAY_ALREADY_CHECKED_IN"
            );
        }

        long points = resolveCheckinReward();
        long nextBalance = account.getBalance() + points;
        account.setBalance(nextBalance);
        accountRepository.save(account);

        CheckInRecord checkInRecord = new CheckInRecord();
        checkInRecord.setUser(user);
        checkInRecord.setCheckinDate(today);
        checkInRecord.setReward(points);
        checkInRecordRepository.save(checkInRecord);

        Instant now = Instant.now();
        user.setLastCheckInAt(now);
        user.setCredits(nextBalance);
        userRepository.save(user);

        saveLedger(user, CreditLedgerType.CHECKIN, points, nextBalance, "CHECKIN", today.toString(), "每日签到奖励", null);
        long publicCredits = fetchPublicBalance(user);
        return new CreditChangeResult(true, points, nextBalance, publicCredits, nextBalance + publicCredits, now, "CHECKIN_SUCCESS");
    }

    @Transactional
    public CreditChangeResult redeem(User user, String code) {
        String normalized = normalizeRedeemCode(code);
        if (normalized.isBlank()) {
            throw new RuntimeException("兑换码不能为空");
        }
        RedeemCode redeemCode = redeemCodeRepository.findByCode(normalized)
                .orElseThrow(() -> new RuntimeException("兑换码无效"));

        Instant now = Instant.now();
        if (!redeemCode.isEnabled()) {
            throw new RuntimeException("兑换码已停用");
        }
        if (redeemCode.getStartsAt() != null && now.isBefore(redeemCode.getStartsAt())) {
            throw new RuntimeException("兑换码未到生效时间");
        }
        if (redeemCode.getExpiresAt() != null && now.isAfter(redeemCode.getExpiresAt())) {
            throw new RuntimeException("兑换码已过期");
        }
        if (redeemCode.getMaxUses() != null && redeemCode.getUsedCount() >= redeemCode.getMaxUses()) {
            throw new RuntimeException("兑换码已被领取完");
        }
        if (!redeemCode.isStackable() && redeemCodeUsageRepository.existsByRedeemCodeAndUser(redeemCode, user)) {
            throw new RuntimeException("该兑换码已领取过");
        }

        ProjectCreditAccount account = getOrCreateAccountForUpdate(user);
        long nextBalance = account.getBalance() + redeemCode.getGrantAmount();
        account.setBalance(nextBalance);
        accountRepository.save(account);

        redeemCode.setUsedCount(redeemCode.getUsedCount() + 1);
        redeemCodeRepository.save(redeemCode);

        RedeemCodeUsage usage = new RedeemCodeUsage();
        usage.setRedeemCode(redeemCode);
        usage.setUser(user);
        redeemCodeUsageRepository.save(usage);

        user.setCredits(nextBalance);
        userRepository.save(user);
        saveLedger(user, CreditLedgerType.REDEEM_CODE, redeemCode.getGrantAmount(), nextBalance, "REDEEM_CODE", redeemCode.getCode(), "兑换码奖励", null);

        long publicCredits = fetchPublicBalance(user);
        return new CreditChangeResult(
                true,
                redeemCode.getGrantAmount(),
                nextBalance,
                publicCredits,
                nextBalance + publicCredits,
                user.getLastCheckInAt(),
                "REDEEM_SUCCESS"
        );
    }

    @Transactional(readOnly = true)
    public BalanceSnapshot currentBalance(User user) {
        ProjectCreditAccount account = getOrCreateAccount(user);
        long project = account.getBalance();
        long pub = fetchPublicBalance(user);
        return new BalanceSnapshot(project, pub, project + pub, user.getLastCheckInAt());
    }

    @Transactional(readOnly = true)
    public Instant fetchLastCheckInAt(User user) {
        return user.getLastCheckInAt();
    }

    @Transactional(readOnly = true)
    public long projectBalance(User user) {
        return getOrCreateAccount(user).getBalance();
    }

    @Transactional
    public AiChargeResult chargeAiUsage(User user, long inputTokens, long outputTokens, String referenceId) {
        long cost = calculateAiCost(inputTokens, outputTokens);
        ProjectCreditAccount account = getOrCreateAccountForUpdate(user);
        if (account.getBalance() < cost) {
            throw new RuntimeException("项目积分不足，请先兑换项目积分");
        }
        long next = account.getBalance() - cost;
        account.setBalance(next);
        accountRepository.save(account);

        user.setCredits(next);
        userRepository.save(user);
        saveLedger(user, CreditLedgerType.AI_DEBIT, -cost, next, "AI", referenceId, "AI 调用扣费", null);
        return new AiChargeResult(cost, next);
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

        ProjectCreditAccount accountSnapshot = getOrCreateAccount(user);
        long projectBefore = accountSnapshot.getBalance();
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
        try {
            long project = transactionTemplate.execute(status -> {
                User managed = userRepository.findById(user.getId()).orElseThrow();
                ProjectCreditAccount account = getOrCreateAccountForUpdate(managed);
                long nextBalance = account.getBalance() + converted;
                account.setBalance(nextBalance);
                accountRepository.save(account);

                managed.setCredits(nextBalance);
                userRepository.save(managed);
                saveLedger(managed, CreditLedgerType.CONVERT_IN, converted, nextBalance, "CONVERSION", orderNo, "通用积分兑换项目积分", idempotencyKey);
                return nextBalance;
            });

            order.setConvertedAmount(converted);
            order.setStatus(ConversionOrderStatus.SUCCESS);
            order.setRemoteMessage(remote.errorMessage());
            order.setProjectAfter(project);
            long publicCredits = remote.publicRemainingTokens() >= 0 ? remote.publicRemainingTokens() : fetchPublicBalance(user);
            order.setPublicAfter(publicCredits);
            conversionOrderRepository.save(order);

            return new ConversionResult(
                    orderNo,
                    converted,
                    order.getProjectBefore(),
                    order.getProjectAfter(),
                    order.getPublicBefore(),
                    order.getPublicAfter(),
                    order.getProjectAfter() + order.getPublicAfter()
            );
        } catch (RuntimeException ex) {
            boolean rollback = billingGrpcClient.grantPublicTokens(remoteUid, converted, "AINovel conversion rollback " + orderNo);
            order.setStatus(rollback ? ConversionOrderStatus.ROLLBACK_SUCCESS : ConversionOrderStatus.ROLLBACK_FAILED);
            order.setRemoteMessage("local apply failed: " + ex.getMessage());
            order.setProjectAfter(order.getProjectBefore());
            order.setPublicAfter(fetchPublicBalance(user));
            conversionOrderRepository.save(order);
            throw ex;
        }
    }

    @Transactional
    public CreditChangeResult grantProjectCredits(User target, long amount, String reason, String operator) {
        if (amount <= 0) {
            throw new RuntimeException("发放积分必须大于 0");
        }
        ProjectCreditAccount account = getOrCreateAccountForUpdate(target);
        long nextBalance = account.getBalance() + amount;
        account.setBalance(nextBalance);
        accountRepository.save(account);

        target.setCredits(nextBalance);
        userRepository.save(target);
        saveLedger(target, CreditLedgerType.ADMIN_GRANT, amount, nextBalance, "ADMIN", operator, reason == null ? "管理员加分" : reason, null);

        long publicCredits = fetchPublicBalance(target);
        return new CreditChangeResult(true, amount, nextBalance, publicCredits, nextBalance + publicCredits, target.getLastCheckInAt(), "GRANT_SUCCESS");
    }

    @Transactional(readOnly = true)
    public Page<LedgerItem> listLedger(User user, Pageable pageable) {
        return ledgerRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(item -> new LedgerItem(
                        String.valueOf(item.getId()),
                        String.valueOf(item.getUser().getId()),
                        item.getUser().getUsername(),
                        item.getEntryType().name(),
                        item.getDelta(),
                        item.getBalanceAfter(),
                        item.getReferenceType(),
                        item.getReferenceId(),
                        item.getDescription(),
                        item.getCreatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public Page<LedgerItem> listLedger(Pageable pageable) {
        return ledgerRepository.findByOrderByCreatedAtDesc(pageable)
                .map(item -> new LedgerItem(
                        String.valueOf(item.getId()),
                        String.valueOf(item.getUser().getId()),
                        item.getUser().getUsername(),
                        item.getEntryType().name(),
                        item.getDelta(),
                        item.getBalanceAfter(),
                        item.getReferenceType(),
                        item.getReferenceId(),
                        item.getDescription(),
                        item.getCreatedAt()
                ));
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
        return redeemCodeRepository.findAll().stream()
                .map(code -> new RedeemCodeView(
                        String.valueOf(code.getId()),
                        code.getCode(),
                        code.getGrantAmount(),
                        code.getMaxUses(),
                        code.getUsedCount(),
                        code.getStartsAt(),
                        code.getExpiresAt(),
                        code.isEnabled(),
                        code.isStackable(),
                        code.getDescription()
                ))
                .toList();
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
            throw new RuntimeException("兑换码不能为空");
        }
        if (grantAmount <= 0) {
            throw new RuntimeException("兑换积分必须大于 0");
        }
        if (startsAt != null && expiresAt != null && expiresAt.isBefore(startsAt)) {
            throw new RuntimeException("兑换码过期时间不能早于生效时间");
        }
        if (redeemCodeRepository.findByCode(normalized).isPresent()) {
            throw new RuntimeException("兑换码已存在");
        }

        RedeemCode redeemCode = new RedeemCode();
        redeemCode.setCode(normalized);
        redeemCode.setGrantAmount(grantAmount);
        redeemCode.setMaxUses(maxUses);
        redeemCode.setStartsAt(startsAt);
        redeemCode.setExpiresAt(expiresAt);
        redeemCode.setEnabled(enabled);
        redeemCode.setStackable(stackable);
        redeemCode.setDescription(description);
        redeemCodeRepository.save(redeemCode);
        return new RedeemCodeView(
                String.valueOf(redeemCode.getId()),
                redeemCode.getCode(),
                redeemCode.getGrantAmount(),
                redeemCode.getMaxUses(),
                redeemCode.getUsedCount(),
                redeemCode.getStartsAt(),
                redeemCode.getExpiresAt(),
                redeemCode.isEnabled(),
                redeemCode.isStackable(),
                redeemCode.getDescription()
        );
    }

    private long resolveCheckinReward() {
        int min = settingsService.getGlobalSettings().getCheckInMinPoints();
        int max = settingsService.getGlobalSettings().getCheckInMaxPoints();
        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }
        return ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
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

    private ProjectCreditAccount getOrCreateAccount(User user) {
        return accountRepository.findByUser(user).orElseGet(() -> createSeedAccount(user));
    }

    private ProjectCreditAccount getOrCreateAccountForUpdate(User user) {
        return accountRepository.findForUpdateByUserId(user.getId()).orElseGet(() -> createSeedAccount(user));
    }

    private ProjectCreditAccount createSeedAccount(User user) {
        ProjectCreditAccount account = new ProjectCreditAccount();
        account.setUser(user);
        account.setBalance(Math.max(0L, Math.round(user.getCredits())));
        return accountRepository.save(account);
    }

    private void saveLedger(
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
}
