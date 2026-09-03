package com.mcp.core.repository;

import com.mcp.core.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunRepository extends JpaRepository<RunEntity, String> {

    List<RunEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<RunEntity> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<RunEntity> findBySessionIdAndAgentIdOrderByCreatedAtDesc(String sessionId, String agentId);

    List<RunEntity> findByStatusOrderByCreatedAtDesc(String status);
}