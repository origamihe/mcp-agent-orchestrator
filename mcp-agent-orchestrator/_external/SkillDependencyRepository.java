package com.mcp.core.repository;

import com.mcp.core.domain.memory.SkillDependencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SkillDependencyRepository extends JpaRepository<SkillDependencyEntity, Long> {

    List<SkillDependencyEntity> findBySourceSkillIdOrderByCoOccurrenceCountDesc(Long sourceSkillId);

    List<SkillDependencyEntity> findByTargetSkillIdOrderByCoOccurrenceCountDesc(Long targetSkillId);

    @Query("SELECT d FROM SkillDependencyEntity d WHERE d.sourceSkillId = :skillId1 AND d.targetSkillId = :skillId2")
    List<SkillDependencyEntity> findDependencyBetween(@Param("skillId1") Long skillId1,
                                                       @Param("skillId2") Long skillId2);

    @Query("SELECT d FROM SkillDependencyEntity d WHERE d.sourceSkillId IN :skillIds ORDER BY d.coOccurrenceCount DESC")
    List<SkillDependencyEntity> findBySourceSkillIds(@Param("skillIds") List<Long> skillIds);

    @Modifying
    @Transactional
    @Query("UPDATE SkillDependencyEntity d SET d.coOccurrenceCount = d.coOccurrenceCount + 1 WHERE d.id = :id")
    void incrementCoOccurrence(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE SkillDependencyEntity d SET d.confidence = :confidence WHERE d.id = :id")
    void updateConfidence(@Param("id") Long id, @Param("confidence") Double confidence);
}