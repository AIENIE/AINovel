package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectCreditAccountRepository extends JpaRepository<ProjectCreditAccount, UUID> {
    Optional<ProjectCreditAccount> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ProjectCreditAccount a where a.user.id = :userId")
    Optional<ProjectCreditAccount> findForUpdateByUserId(@Param("userId") UUID userId);
}

