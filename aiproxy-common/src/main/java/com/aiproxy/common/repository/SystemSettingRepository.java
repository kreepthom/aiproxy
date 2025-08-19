package com.aiproxy.common.repository;

import com.aiproxy.common.entity.SystemSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSettingEntity, String> {
    
    List<SystemSettingEntity> findBySettingGroup(String settingGroup);
    
    Optional<SystemSettingEntity> findBySettingKey(String settingKey);
}