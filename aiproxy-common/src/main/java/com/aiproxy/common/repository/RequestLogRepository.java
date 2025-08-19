package com.aiproxy.common.repository;

import com.aiproxy.common.entity.RequestLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {
    
    Page<RequestLogEntity> findByApiKeyIdOrderByCreatedAtDesc(String apiKeyId, Pageable pageable);
    
    Page<RequestLogEntity> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);
    
    @Query("SELECT r FROM RequestLogEntity r WHERE r.createdAt BETWEEN :startTime AND :endTime ORDER BY r.createdAt DESC")
    Page<RequestLogEntity> findByDateRange(@Param("startTime") LocalDateTime startTime, 
                                          @Param("endTime") LocalDateTime endTime, 
                                          Pageable pageable);
    
    @Query("SELECT SUM(r.totalTokens) FROM RequestLogEntity r WHERE r.apiKeyId = :apiKeyId AND r.createdAt >= :startTime")
    Long getTotalTokensByApiKeySince(@Param("apiKeyId") String apiKeyId, @Param("startTime") LocalDateTime startTime);
    
    @Query("SELECT COUNT(r) FROM RequestLogEntity r WHERE r.apiKeyId = :apiKeyId AND r.createdAt >= :startTime")
    Long getTotalRequestsByApiKeySince(@Param("apiKeyId") String apiKeyId, @Param("startTime") LocalDateTime startTime);
    
    // 统计成功请求数
    Long countByStatusCodeBetween(int minStatus, int maxStatus);
    
    // 统计错误请求数
    Long countByStatusCodeGreaterThanEqual(int minStatus);
    
    // 统计总token数
    @Query("SELECT SUM(r.totalTokens) FROM RequestLogEntity r WHERE r.totalTokens IS NOT NULL")
    Long sumTotalTokens();
    
    // 统计今日成功请求数
    Long countByCreatedAtAfterAndStatusCodeBetween(LocalDateTime after, int minStatus, int maxStatus);
    
    // 统计今日错误请求数  
    Long countByCreatedAtAfterAndStatusCodeGreaterThanEqual(LocalDateTime after, int minStatus);
    
    // 统计今日token数
    @Query("SELECT SUM(r.totalTokens) FROM RequestLogEntity r WHERE r.createdAt >= :after AND r.totalTokens IS NOT NULL")
    Long sumTotalTokensByCreatedAtAfter(@Param("after") LocalDateTime after);
}