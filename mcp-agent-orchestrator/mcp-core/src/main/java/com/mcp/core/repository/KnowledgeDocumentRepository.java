package com.mcp.core.repository;

import com.mcp.core.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, String> {

    List<KnowledgeDocumentEntity> findByCollectionIdOrderByCreatedAtDesc(String collectionId);

    long countByCollectionId(String collectionId);
}