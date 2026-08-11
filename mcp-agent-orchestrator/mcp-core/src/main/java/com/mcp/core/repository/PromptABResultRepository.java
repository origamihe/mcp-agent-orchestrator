package com.mcp.core.repository;

import com.mcp.core.entity.PromptABResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptABResultRepository extends JpaRepository<PromptABResultEntity, Long> {

    Optional<PromptABResultEntity> findByPromptNameAndVariant(String promptName, String variant);

    List<PromptABResultEntity> findByPromptName(String promptName);
}