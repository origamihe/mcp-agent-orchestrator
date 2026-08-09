package com.mcp.core.repository;

import com.mcp.core.entity.WorldStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorldStateRepository extends JpaRepository<WorldStateEntity, Long> {

    Optional<WorldStateEntity> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}