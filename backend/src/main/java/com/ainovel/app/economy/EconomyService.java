package com.ainovel.app.economy;

import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EconomyService {

    private final BillingGrpcClient billingGrpcClient;
    private final UserRepository userRepository;

    public EconomyService(BillingGrpcClient billingGrpcClient, UserRepository userRepository) {
        this.billingGrpcClient = billingGrpcClient;
        this.userRepository = userRepository;
    }

    public record CreditChangeResult(boolean success, double points, double newTotal, Instant lastCheckInAt) {}

    @Transactional
    public CreditChangeResult checkIn(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            return new CreditChangeResult(false, 0, user.getCredits(), user.getLastCheckInAt());
        }
        BillingGrpcClient.CheckinResult result = billingGrpcClient.checkin(remoteUid);
        BillingGrpcClient.CheckinStatus status = billingGrpcClient.checkinStatus(remoteUid);
        double total = result.totalTokens();
        Instant lastCheckIn = status.lastCheckinAt();
        user.setCredits(total);
        user.setLastCheckInAt(lastCheckIn);
        userRepository.save(user);
        if (!result.success() && !result.alreadyCheckedIn()) {
            throw new RuntimeException(result.errorMessage() == null || result.errorMessage().isBlank()
                    ? "签到失败"
                    : result.errorMessage());
        }
        return new CreditChangeResult(!result.alreadyCheckedIn(), result.tokensGranted(), total, lastCheckIn);
    }

    @Transactional
    public CreditChangeResult redeem(User user, String code) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            throw new RuntimeException("当前账号未绑定统一用户，无法执行兑换");
        }
        BillingGrpcClient.RedeemResult result = billingGrpcClient.redeem(remoteUid, code);
        if (!result.success()) {
            throw new RuntimeException(result.errorMessage() == null || result.errorMessage().isBlank()
                    ? "兑换失败"
                    : result.errorMessage());
        }
        user.setCredits(result.totalTokens());
        userRepository.save(user);
        return new CreditChangeResult(true, result.tokensGranted(), result.totalTokens(), user.getLastCheckInAt());
    }

    @Transactional(readOnly = true)
    public double currentBalance(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            return user.getCredits();
        }
        return billingGrpcClient.totalBalance(remoteUid);
    }

    @Transactional(readOnly = true)
    public Instant fetchLastCheckInAt(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            return user.getLastCheckInAt();
        }
        return billingGrpcClient.checkinStatus(remoteUid).lastCheckinAt();
    }
}
