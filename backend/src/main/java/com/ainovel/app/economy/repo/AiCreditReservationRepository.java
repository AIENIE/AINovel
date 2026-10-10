package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AiCreditReservationRepository extends JpaRepository<AiCreditReservation, UUID> {
    @org.springframework.data.jpa.repository.Query("select count(r) > 0 from AiCreditReservation r where r.user.id = :userId and r.status = com.ainovel.app.economy.model.AiCreditReservation.Status.RECONCILIATION_REQUIRED and (r.idempotencyKey like :prefix or r.idempotencyKey = :requestId)")
    boolean hasUncertainOperation(@org.springframework.data.repository.query.Param("userId") UUID userId,
                                 @org.springframework.data.repository.query.Param("prefix") String prefix,
                                 @org.springframework.data.repository.query.Param("requestId") String requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiCreditReservation> findByUserAndIdempotencyKey(User user, String idempotencyKey);
}
