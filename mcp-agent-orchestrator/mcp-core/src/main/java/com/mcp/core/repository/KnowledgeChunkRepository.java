package com.mcp.core.repository;

import com.mcp.core.entity.KnowledgeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, String> {

    List<KnowledgeChunkEntity> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    long countByDocumentId(String documentId);
}