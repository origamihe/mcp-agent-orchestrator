package com.mcp.core.repository;

import com.mcp.core.entity.TraceEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraceEventRepository extends JpaRepository<TraceEventEntity, Long> {

    List<TraceEventEntity> findByRunIdOrderBySequenceAsc(String runId);

    List<TraceEventEntity> findByRunIdAndParentIdIsNullOrderBySequenceAsc(String runId);

    void deleteByRunId(String runId);
}