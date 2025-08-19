package com.aiproxy.common.repository;

import com.aiproxy.common.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {
    
    Optional<ApiKeyEntity> findByKeyHash(String keyHash);
    
    Optional<ApiKeyEntity> findByKeyHashAndIsActiveTrue(String keyHash);
    
    @Modifying
    @Transactional
    @Query("UPDATE ApiKeyEntity a SET a.lastUsedAt = CURRENT_TIMESTAMP, a.totalRequests = a.totalRequests + 1, a.totalTokens = a.totalTokens + :tokens WHERE a.id = :id")
    void updateUsageStatistics(@Param("id") String id, @Param("tokens") Long tokens);
}