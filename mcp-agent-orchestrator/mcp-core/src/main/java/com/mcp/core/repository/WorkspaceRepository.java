package com.mcp.core.repository;

import com.mcp.core.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, String> {

    Optional<WorkspaceEntity> findByWorkspaceId(String workspaceId);

    @Query("SELECT w FROM WorkspaceEntity w WHERE w.lastActiveAt >= :since ORDER BY w.lastActiveAt DESC")
    List<WorkspaceEntity> findRecentlyActive(@Param("since") LocalDateTime since);

    List<WorkspaceEntity> findByProjectPath(String projectPath);

    void deleteByWorkspaceId(String workspaceId);
}