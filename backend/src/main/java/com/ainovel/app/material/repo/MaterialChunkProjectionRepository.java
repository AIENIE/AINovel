package com.ainovel.app.material.repo;

import com.ainovel.app.material.model.MaterialChunkProjection;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MaterialChunkProjectionRepository extends JpaRepository<MaterialChunkProjection,String> {
    void deleteByMaterialId(UUID materialId);
    @Query(value="""
      SELECT * FROM material_chunks c
      WHERE c.status='approved' AND (c.owner_user_id IS NULL OR c.owner_user_id=:ownerId)
        AND (:query='' OR LOWER(c.title) LIKE LOWER(CONCAT('%',:query,'%'))
             OR LOWER(c.tags) LIKE LOWER(CONCAT('%',:query,'%'))
             OR LOWER(c.text) LIKE LOWER(CONCAT('%',:query,'%')))
      ORDER BY CASE WHEN LOWER(c.title) LIKE LOWER(CONCAT('%',:query,'%')) THEN 0 ELSE 1 END, c.updated_at DESC
      LIMIT :limit
      """,nativeQuery=true)
    List<MaterialChunkProjection> searchVisible(@Param("ownerId") UUID ownerId,@Param("query") String query,@Param("limit") int limit);
}
