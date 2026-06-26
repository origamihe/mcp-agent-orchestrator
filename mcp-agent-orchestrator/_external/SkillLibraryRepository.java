package com.mcp.core.repository;

import com.mcp.core.domain.memory.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SkillLibraryRepository extends JpaRepository<SkillEntity, Long> {

    List<SkillEntity> findByIsActiveTrueOrderBySuccessRateDesc();

    List<SkillEntity> findByNameOrderByVersionDesc(String name);

    @Query("SELECT s FROM SkillEntity s WHERE s.isActive = true AND s.name = :name ORDER BY s.version DESC")
    List<SkillEntity> findActiveByName(@Param("name") String name);

    @Query("SELECT s FROM SkillEntity s WHERE s.isActive = true AND s.successRate >= :minRate ORDER BY s.successRate DESC")
    List<SkillEntity> findHighSuccessSkills(@Param("minRate") double minRate);

    @Query("SELECT s FROM SkillEntity s WHERE s.isActive = true AND s.evolvedFromId = :skillId")
    List<SkillEntity> findEvolvedVersions(@Param("skillId") Long skillId);

    @Modifying
    @Transactional
    @Query("UPDATE SkillEntity s SET s.totalExecutions = s.totalExecutions + 1, " +
           "s.successCount = s.successCount + :successInc, " +
           "s.failureCount = s.failureCount + :failureInc, " +
           "s.successRate = CASE WHEN (s.totalExecutions + 1) > 0 " +
           "THEN (s.successCount + :successInc) * 100.0 / (s.totalExecutions + 1) " +
           "ELSE 0.0 END, " +
           "s.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE s.id = :id")
    void recordExecution(@Param("id") Long id,
                         @Param("successInc") int successInc,
                         @Param("failureInc") int failureInc);

    @Modifying
    @Transactional
    @Query("UPDATE SkillEntity s SET s.isActive = false WHERE s.id = :id")
    void deactivate(@Param("id") Long id);
}