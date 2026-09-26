package com.ainovel.app.world.repo;

import com.ainovel.app.world.model.World;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorldRepository extends JpaRepository<World, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from World w where w.id=:id")
    java.util.Optional<World> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    List<World> findByUser(User user);
    long countByUser(User user);
}
