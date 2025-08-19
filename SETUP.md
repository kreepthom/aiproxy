# 开发环境配置指南

本项目使用 Spring Boot 标准的配置文件管理方式。

## 配置文件说明

```
application.yml              # 基础配置（已提交到 Git）
application-local.yml        # 本地开发配置（不提交，包含敏感信息）
application-prod.yml         # 生产环境配置（已提交，使用环境变量）
application-local.yml.example # 本地配置示例（已提交，供参考）
```

## 快速开始

### 1. 创建本地配置文件

首次克隆项目后，复制示例配置文件：

```bash
cd aiproxy-api/src/main/resources
cp application-local.yml.example application-local.yml
```

### 2. 编辑本地配置

编辑 `application-local.yml` 文件，填入您的实际配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_gateway?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_mysql_password  # <-- 修改这里
    
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password  # <-- 修改这里（如果有密码）

relay:
  admin:
    username: admin
    password: your_admin_password  # <-- 修改这里
  
  security:
    jwt:
      secret: your-jwt-secret-at-least-32-characters  # <-- 修改这里（至少32字符）
    encryption:
      key: your-encryption-key-must-be-32-chars  # <-- 修改这里（必须32字符）
```

### 3. 安装依赖

#### 后端依赖
```bash
mvn clean install
```

#### 前端依赖
```bash
cd frontend
npm install
```

### 4. 初始化数据库

确保 MySQL 服务已启动，项目会自动创建数据库和表结构（使用 Flyway）。

### 5. 启动后端服务

```bash
# 方式一：使用 Spring Boot 插件（推荐）
mvn spring-boot:run -pl aiproxy-api

# 方式二：指定配置文件
mvn spring-boot:run -pl aiproxy-api -Dspring.profiles.active=local

# 方式三：打包后运行
mvn clean package
java -jar aiproxy-api/target/aiproxy-api-1.0.0.jar --spring.profiles.active=local
```

### 6. 启动前端开发服务器

```bash
cd frontend
npm run dev
```

访问 http://localhost:5173 查看前端界面。

## 配置优先级

Spring Boot 配置优先级（从高到低）：

1. 命令行参数
2. 系统环境变量
3. application-{profile}.yml（如 application-local.yml）
4. application.yml

## 生产环境部署

### 使用环境变量（推荐）

生产环境使用 `application-prod.yml`，所有敏感信息通过环境变量注入：

```bash
# 设置环境变量
export MYSQL_PASSWORD=your_production_password
export REDIS_PASSWORD=your_redis_password
export JWT_SECRET=your_production_jwt_secret_at_least_32_chars
export ENCRYPTION_KEY=your_production_encryption_key_32ch
export ADMIN_PASSWORD=your_admin_password

# 启动应用
java -jar aiproxy-api-1.0.0.jar --spring.profiles.active=prod
```

### 使用 Docker

```bash
docker run -d \
  -e MYSQL_PASSWORD=your_password \
  -e REDIS_PASSWORD=your_password \
  -e JWT_SECRET=your_jwt_secret \
  -e ENCRYPTION_KEY=your_encryption_key \
  -e ADMIN_PASSWORD=your_admin_password \
  -p 8080:8080 \
  claude-relay:latest
```

### 使用 Docker Compose

创建 `.env` 文件（不提交到 Git）：

```env
MYSQL_PASSWORD=your_password
REDIS_PASSWORD=your_password
JWT_SECRET=your_jwt_secret
ENCRYPTION_KEY=your_encryption_key
ADMIN_PASSWORD=your_admin_password
```

然后运行：

```bash
docker-compose up -d
```

## 配置项说明

### 必需配置

| 配置项 | 说明 | 示例 |
|--------|------|------|
| spring.datasource.password | MySQL 密码 | your_password |
| relay.admin.password | 管理员密码 | admin123 |
| relay.security.jwt.secret | JWT 签名密钥（至少32字符） | your-secret-key-must-be-at-least-32-characters |
| relay.security.encryption.key | 数据加密密钥（必须32字符） | 12345678901234567890123456789012 |

### 可选配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| spring.datasource.url | MySQL 连接URL | jdbc:mysql://localhost:3306/ai_gateway |
| spring.datasource.username | MySQL 用户名 | root |
| spring.data.redis.host | Redis 主机 | localhost |
| spring.data.redis.port | Redis 端口 | 6379 |
| spring.data.redis.password | Redis 密码 | （空） |
| server.port | 服务端口 | 8080 |

## 安全提醒

⚠️ **重要提醒**：

1. **绝对不要提交** `application-local.yml` 到 Git 仓库
2. JWT secret 至少需要 32 个字符
3. Encryption key 必须是 32 个字符
4. 生产环境务必使用强密码
5. 定期更换密钥和密码

## IDE 配置

### IntelliJ IDEA

1. 运行配置中添加：
   - Active profiles: `local`
   - 或在 VM options 中添加：`-Dspring.profiles.active=local`

### VS Code

在 `.vscode/launch.json` 中添加：

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot App",
      "request": "launch",
      "mainClass": "com.aiproxy.api.RelayApiApplication",
      "args": "--spring.profiles.active=local"
    }
  ]
}
```

## 故障排除

### 问题：找不到配置文件

确保 `application-local.yml` 在正确的位置：
```
aiproxy-api/src/main/resources/application-local.yml
```

### 问题：数据库连接失败

1. 确认 MySQL 服务已启动
2. 检查 `application-local.yml` 中的数据库配置
3. 确认用户有创建数据库的权限

### 问题：JWT 密钥错误

确保 JWT secret 至少 32 个字符，Encryption key 必须正好 32 个字符。

### 查看当前激活的配置

启动日志中会显示：
```
The following profiles are active: local
```

## 联系支持

如有问题，请提交 Issue 到项目仓库。