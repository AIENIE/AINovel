package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.CreditConversionOrder;
import com.ainovel.app.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface CreditConversionOrderRepository extends JpaRepository<CreditConversionOrder, UUID> {
    Optional<CreditConversionOrder> findByUserAndIdempotencyKey(User user, String idempotencyKey);
    Optional<CreditConversionOrder> findFirstByUserOrderByCreatedAtDesc(User user);
    Page<CreditConversionOrder> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<CreditConversionOrder> findByOrderByCreatedAtDesc(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreditConversionOrder> findForUpdateById(UUID id);
    List<CreditConversionOrder> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            com.ainovel.app.economy.model.ConversionOrderStatus status, Instant nextAttemptAt, Pageable pageable);
}
