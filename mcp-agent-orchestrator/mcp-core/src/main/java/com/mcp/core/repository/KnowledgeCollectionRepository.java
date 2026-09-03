package com.mcp.core.repository;

import com.mcp.core.entity.KnowledgeCollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeCollectionRepository extends JpaRepository<KnowledgeCollectionEntity, String> {

    List<KnowledgeCollectionEntity> findByNameContainingIgnoreCaseOrderByUpdatedAtDesc(String name);
}