package com.ainovel.app.manuscript.repo;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface ManuscriptRepository extends JpaRepository<Manuscript, UUID> {
    List<Manuscript> findByOutline(Outline outline);

    @Query("select m from Manuscript m join fetch m.outline o join fetch o.story s join fetch s.user where m.id = :id")
    Optional<Manuscript> findWithStoryById(@Param("id") UUID id);

    @Query("select m from Manuscript m join fetch m.outline o join fetch o.story s where s.user = :user")
    List<Manuscript> findByStoryUser(@Param("user") User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Manuscript m where m.id = :id")
    Optional<Manuscript> findByIdForUpdate(@Param("id") UUID id);
}
