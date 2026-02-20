package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.RedeemCode;
import com.ainovel.app.economy.model.RedeemCodeUsage;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RedeemCodeUsageRepository extends JpaRepository<RedeemCodeUsage, UUID> {
    boolean existsByRedeemCodeAndUser(RedeemCode redeemCode, User user);
}

