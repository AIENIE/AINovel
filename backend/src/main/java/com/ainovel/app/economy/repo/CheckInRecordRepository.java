package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.CheckInRecord;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, UUID> {
    boolean existsByUserAndCheckinDate(User user, LocalDate checkinDate);
}

