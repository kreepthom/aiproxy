package com.aiproxy.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
public class SystemSettingEntity {
    
    @Id
    @Column(length = 100)
    private String settingKey;
    
    @Column(columnDefinition = "TEXT")
    private String settingValue;
    
    @Column(length = 50)
    private String settingGroup; // general, security, rate_limit
    
    @Column(length = 255)
    private String description;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}