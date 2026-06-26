package com.mcp.core.repository;

import com.mcp.core.domain.memory.FailureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FailureLibraryRepository extends JpaRepository<FailureEntity, Long> {

    List<FailureEntity> findByIsResolvedFalseOrderByOccurrenceCountDesc();

    @Query("SELECT f FROM FailureEntity f WHERE f.isResolved = false AND f.taskPattern = :taskPattern " +
           "ORDER BY f.occurrenceCount DESC")
    List<FailureEntity> findUnresolvedByTaskPattern(@Param("taskPattern") String taskPattern);

    @Query("SELECT f FROM FailureEntity f WHERE f.isResolved = false " +
           "AND LOWER(f.errorSignature) LIKE LOWER(CONCAT('%', :errorKeyword, '%')) " +
           "ORDER BY f.occurrenceCount DESC")
    List<FailureEntity> findByErrorSignatureContaining(@Param("errorKeyword") String errorKeyword);

    @Query("SELECT f FROM FailureEntity f WHERE f.resolvedBySkillId = :skillId")
    List<FailureEntity> findResolvedBySkill(@Param("skillId") Long skillId);

    @Modifying
    @Transactional
    @Query("UPDATE FailureEntity f SET f.occurrenceCount = f.occurrenceCount + 1, " +
           "f.lastOccurredAt = CURRENT_TIMESTAMP WHERE f.id = :id")
    void incrementOccurrence(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE FailureEntity f SET f.isResolved = true, f.resolvedBySkillId = :skillId WHERE f.id = :id")
    void markResolved(@Param("id") Long id, @Param("skillId") Long skillId);
}