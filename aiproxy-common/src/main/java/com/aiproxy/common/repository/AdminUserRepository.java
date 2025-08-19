package com.aiproxy.common.repository;

import com.aiproxy.common.entity.AdminUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, String> {
    
    Optional<AdminUserEntity> findByUsername(String username);
    
    Optional<AdminUserEntity> findByUsernameAndIsActiveTrue(String username);
    
    boolean existsByUsername(String username);
}