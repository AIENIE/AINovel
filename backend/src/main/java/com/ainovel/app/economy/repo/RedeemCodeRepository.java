package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.RedeemCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RedeemCodeRepository extends JpaRepository<RedeemCode, UUID> {
    Optional<RedeemCode> findByCode(String code);
}

