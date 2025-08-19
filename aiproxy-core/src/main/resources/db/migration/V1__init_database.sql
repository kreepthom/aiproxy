-- 创建账户表：存储AI服务提供商的OAuth账户信息
CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(36) PRIMARY KEY COMMENT '账户唯一标识',
    email VARCHAR(255) NOT NULL COMMENT '账户邮箱',
    provider VARCHAR(20) NOT NULL COMMENT 'AI提供商：CLAUDE, OPENAI, GEMINI',
    access_token TEXT COMMENT '访问令牌',
    refresh_token TEXT COMMENT '刷新令牌',
    token_expires_at TIMESTAMP NULL COMMENT '令牌过期时间',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '账户状态：ACTIVE/INACTIVE/DISABLED/EXPIRED/SUSPENDED/ERROR',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_used_at TIMESTAMP NULL COMMENT '最后使用时间',
    total_requests BIGINT NOT NULL DEFAULT 0 COMMENT '总请求次数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总消耗token数',
    metadata TEXT COMMENT '额外元数据（JSON格式）',
    INDEX idx_enabled_status (enabled, status) COMMENT '启用状态索引',
    INDEX idx_provider (provider) COMMENT '提供商索引',
    UNIQUE KEY uk_email_provider (email, provider) COMMENT '邮箱-提供商唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI提供商账户表';

-- 创建API密钥表：管理系统的API访问密钥
CREATE TABLE IF NOT EXISTS api_keys (
    id VARCHAR(36) PRIMARY KEY COMMENT 'API密钥ID',
    key_hash VARCHAR(255) NOT NULL UNIQUE COMMENT '密钥哈希值（SHA-256）',
    name VARCHAR(100) COMMENT '密钥名称',
    created_by VARCHAR(100) COMMENT '创建者',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
    rate_limit INT NOT NULL DEFAULT 1000 COMMENT '速率限制（请求/分钟）',
    daily_token_limit BIGINT NOT NULL DEFAULT 1000000 COMMENT '每日token限额',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_used_at TIMESTAMP NULL COMMENT '最后使用时间',
    total_requests BIGINT NOT NULL DEFAULT 0 COMMENT '总请求次数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总消耗token数',
    allowed_models TEXT COMMENT '允许使用的模型列表（JSON数组）',
    metadata TEXT COMMENT '额外配置信息（JSON格式）',
    INDEX idx_key_hash (key_hash) COMMENT '密钥哈希索引',
    INDEX idx_is_active (is_active) COMMENT '激活状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API密钥管理表';

-- 创建请求日志表：记录所有API请求的详细信息
CREATE TABLE IF NOT EXISTS request_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    api_key_id VARCHAR(36) COMMENT '使用的API密钥ID',
    account_id VARCHAR(36) COMMENT '使用的账户ID',
    provider VARCHAR(20) COMMENT 'AI提供商',
    model VARCHAR(100) COMMENT '使用的模型',
    request_tokens INT COMMENT '请求消耗的token数',
    response_tokens INT COMMENT '响应消耗的token数',
    total_tokens INT COMMENT '总消耗token数',
    latency_ms INT COMMENT '请求延迟（毫秒）',
    status_code INT COMMENT 'HTTP状态码',
    error_message TEXT COMMENT '错误信息',
    request_path TEXT COMMENT '请求路径',
    client_ip VARCHAR(45) COMMENT '客户端IP地址',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
    INDEX idx_created_at (created_at) COMMENT '时间索引',
    INDEX idx_api_key (api_key_id) COMMENT 'API密钥索引',
    INDEX idx_account (account_id) COMMENT '账户索引',
    FOREIGN KEY (api_key_id) REFERENCES api_keys(id) ON DELETE SET NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API请求日志表';

-- 创建使用统计表：按日汇总的使用情况统计
CREATE TABLE IF NOT EXISTS usage_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '统计ID',
    date DATE NOT NULL COMMENT '统计日期',
    api_key_id VARCHAR(36) COMMENT 'API密钥ID',
    account_id VARCHAR(36) COMMENT '账户ID',
    provider VARCHAR(20) COMMENT 'AI提供商',
    model VARCHAR(100) COMMENT '模型名称',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求次数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总消耗token数',
    total_errors INT NOT NULL DEFAULT 0 COMMENT '总错误次数',
    avg_latency_ms INT COMMENT '平均延迟（毫秒）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_daily_stats (date, api_key_id, account_id, provider, model) COMMENT '每日统计唯一约束',
    INDEX idx_date (date) COMMENT '日期索引',
    INDEX idx_api_key_date (api_key_id, date) COMMENT 'API密钥-日期联合索引',
    FOREIGN KEY (api_key_id) REFERENCES api_keys(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='使用情况统计表（按日汇总）';

-- 创建管理员用户表：系统管理员账户
CREATE TABLE IF NOT EXISTS admin_users (
    id VARCHAR(36) PRIMARY KEY COMMENT '管理员ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt）',
    email VARCHAR(255) COMMENT '邮箱地址',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    INDEX idx_username (username) COMMENT '用户名索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员用户表';