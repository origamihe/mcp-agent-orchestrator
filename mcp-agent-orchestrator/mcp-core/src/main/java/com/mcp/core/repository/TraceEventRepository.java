package com.mcp.core.repository;

import com.mcp.core.entity.TraceEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TraceEventRepository extends JpaRepository<TraceEventEntity, Long> {

    List<TraceEventEntity> findByRunIdOrderBySequenceAsc(String runId);

    List<TraceEventEntity> findByRunIdAndParentIdIsNullOrderBySequenceAsc(String runId);

    void deleteByRunId(String runId);

    Page<TraceEventEntity> findByAgentId(String agentId, Pageable pageable);

    Page<TraceEventEntity> findBySessionId(String sessionId, Pageable pageable);

    @Query("SELECT e FROM TraceEventEntity e WHERE " +
           "(:eventType IS NULL OR e.eventType = :eventType) AND " +
           "(:agentId IS NULL OR e.agentId = :agentId) AND " +
           "(:sessionId IS NULL OR e.sessionId = :sessionId) AND " +
           "(:runId IS NULL OR e.runId = :runId) AND " +
           "(:startTime IS NULL OR e.startTime >= :startTime) AND " +
           "(:endTime IS NULL OR e.startTime <= :endTime) AND " +
           "(:search IS NULL OR LOWER(e.operation) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(e.eventType) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY e.startTime DESC")
    Page<TraceEventEntity> findByFilters(
            @Param("eventType") String eventType,
            @Param("agentId") String agentId,
            @Param("sessionId") String sessionId,
            @Param("runId") String runId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("search") String search,
            Pageable pageable);
}