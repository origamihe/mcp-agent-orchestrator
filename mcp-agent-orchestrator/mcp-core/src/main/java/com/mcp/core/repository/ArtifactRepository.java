package com.mcp.core.repository;

import com.mcp.core.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {

    Optional<ArtifactEntity> findByIdAndDeletedAtIsNull(String id);

    List<ArtifactEntity> findBySessionIdAndDeletedAtIsNullOrderByModifiedAtDesc(String sessionId);

    Optional<ArtifactEntity> findBySessionIdAndFilePathAndDeletedAtIsNull(String sessionId, String filePath);

    List<ArtifactEntity> findBySessionIdAndArtifactTypeAndDeletedAtIsNull(String sessionId, String artifactType);

    @Modifying
    @Query("UPDATE ArtifactEntity a SET a.deletedAt = :now WHERE a.sessionId = :sessionId AND a.filePath = :filePath AND a.deletedAt IS NULL")
    int softDeleteByPath(@Param("sessionId") String sessionId,
                         @Param("filePath") String filePath,
                         @Param("now") Instant now);

    @Modifying
    @Query("UPDATE ArtifactEntity a SET a.deletedAt = :now WHERE a.sessionId = :sessionId AND a.deletedAt IS NULL")
    int softDeleteAllBySession(@Param("sessionId") String sessionId, @Param("now") Instant now);
}