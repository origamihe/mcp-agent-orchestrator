package com.mcp.core.repository;

import com.mcp.core.domain.memory.ReflectionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReflectionLogRepository extends JpaRepository<ReflectionLogEntity, Long> {

    List<ReflectionLogEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<ReflectionLogEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ReflectionLogEntity> findByWorthLearningTrueOrderByCreatedAtDesc();

    @Query("SELECT r FROM ReflectionLogEntity r WHERE r.outcome = :outcome ORDER BY r.createdAt DESC")
    List<ReflectionLogEntity> findByOutcome(@Param("outcome") ReflectionLogEntity.ReflectionOutcome outcome);

    @Query("SELECT r FROM ReflectionLogEntity r WHERE r.generatedSkillId = :skillId")
    List<ReflectionLogEntity> findByGeneratedSkillId(@Param("skillId") Long skillId);

    @Query("SELECT r FROM ReflectionLogEntity r WHERE r.generatedFailureId = :failureId")
    List<ReflectionLogEntity> findByGeneratedFailureId(@Param("failureId") Long failureId);

    @Query("SELECT COUNT(r) FROM ReflectionLogEntity r WHERE r.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(r) FROM ReflectionLogEntity r WHERE r.sessionId = :sessionId AND r.worthLearning = true")
    long countWorthLearningBySessionId(@Param("sessionId") String sessionId);
}