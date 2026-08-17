package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AiCreditReservationRepository extends JpaRepository<AiCreditReservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiCreditReservation> findByUserAndIdempotencyKey(User user, String idempotencyKey);
}
