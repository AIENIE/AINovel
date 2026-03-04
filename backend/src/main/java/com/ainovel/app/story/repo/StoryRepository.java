package com.ainovel.app.story.repo;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    List<Story> findByUser(User user);
    long countByUser(User user);

    @Query("select s from Story s join fetch s.user where s.id = :id")
    Optional<Story> findByIdWithUser(@Param("id") UUID id);
}
