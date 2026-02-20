package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectCreditLedgerRepository extends JpaRepository<ProjectCreditLedger, UUID> {
    Page<ProjectCreditLedger> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}

