package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.CreditConversionOrder;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditConversionOrderRepository extends JpaRepository<CreditConversionOrder, UUID> {
    Optional<CreditConversionOrder> findByUserAndIdempotencyKey(User user, String idempotencyKey);
}

