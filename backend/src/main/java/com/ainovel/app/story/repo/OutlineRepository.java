package com.ainovel.app.story.repo;

import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutlineRepository extends JpaRepository<Outline, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Outline o where o.id=:id")
    Optional<Outline> findByIdForUpdate(@Param("id") UUID id);
    List<Outline> findByStory(Story story);

    @Query("select o from Outline o join fetch o.story s join fetch s.user where o.id = :id")
    Optional<Outline> findByIdWithStoryUser(@Param("id") UUID id);

    @Query("select o from Outline o join fetch o.story s join fetch s.user where o.story = :story")
    List<Outline> findByStoryWithStoryUser(@Param("story") Story story);

    @Query("select o from Outline o join fetch o.story s join fetch s.user")
    List<Outline> findAllWithStoryUser();
}
