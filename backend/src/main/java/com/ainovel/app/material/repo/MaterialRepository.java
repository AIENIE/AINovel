package com.ainovel.app.material.repo;

import com.ainovel.app.material.model.Material;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Material m where m.id = :id")
    java.util.Optional<Material> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    List<Material> findByUserUsernameAndStatusIgnoreCase(String username, String status);
    org.springframework.data.domain.Page<Material> findByStatusIgnoreCase(String status, org.springframework.data.domain.Pageable pageable);
    List<Material> findByUser(User user);
    long countByStatusIgnoreCase(String status);
}
