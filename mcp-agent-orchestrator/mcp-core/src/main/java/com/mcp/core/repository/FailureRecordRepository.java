package com.mcp.core.repository;

import com.mcp.core.entity.FailureRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailureRecordRepository extends JpaRepository<FailureRecordEntity, Long> {
}