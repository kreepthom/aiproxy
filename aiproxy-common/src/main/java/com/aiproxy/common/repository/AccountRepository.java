package com.aiproxy.common.repository;

import com.aiproxy.common.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    
    Optional<AccountEntity> findByEmail(String email);
    
    List<AccountEntity> findByEnabledTrueAndStatus(String status);
    
    List<AccountEntity> findByProvider(String provider);
    
    List<AccountEntity> findByEnabledTrue();
    
    @Query("SELECT a FROM AccountEntity a WHERE a.enabled = true AND a.status = 'ACTIVE' AND a.provider = :provider")
    List<AccountEntity> findActiveAccountsByProvider(@Param("provider") String provider);
    
    @Query("SELECT a FROM AccountEntity a WHERE a.tokenExpiresAt < CURRENT_TIMESTAMP")
    List<AccountEntity> findExpiredAccounts();
    
    @Modifying
    @Transactional
    @Query("UPDATE AccountEntity a SET a.lastUsedAt = CURRENT_TIMESTAMP, a.totalRequests = a.totalRequests + 1, a.totalTokens = a.totalTokens + :tokens WHERE a.id = :id")
    void updateUsageStatistics(@Param("id") String id, @Param("tokens") Long tokens);
}