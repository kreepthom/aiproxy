# AI Proxy

企业级 AI API 代理服务，支持多 AI 提供商（Claude、ChatGPT、Gemini），提供统一的接口、账号池管理、智能路由等功能。

## ✨ 核心特性

- 🚀 **高性能** - 基于 Spring WebFlux 响应式架构，支持 SSE 流式传输
- 🔄 **账号池** - 多账号自动切换，智能负载均衡
- 🔐 **安全认证** - OAuth 2.0 PKCE 流程，JWT 认证，API Key 管理
- 📊 **监控统计** - 请求日志、使用统计、错误追踪
- 🛡️ **限流保护** - 请求频率限制、Token 用量控制
- 🔧 **易于部署** - Docker 容器化，支持环境变量配置
- 🌐 **前端界面** - React + TypeScript 管理后台

## 🛠️ 技术栈

- **后端**: Java 21, Spring Boot 3.2+, Spring WebFlux
- **前端**: React 18, TypeScript, Vite, TailwindCSS
- **数据库**: MySQL 8.0+, Redis 7.0+
- **部署**: Docker, Docker Compose

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+
- Node.js 18+ (前端开发)

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/yourusername/aiproxy.git
cd aiproxy
```

2. **配置本地环境**
```bash
cd aiproxy-api/src/main/resources
cp application-local.yml.example application-local.yml
# 编辑 application-local.yml，填入你的数据库密码等配置
```

3. **启动服务**
```bash
# 启动后端
mvn spring-boot:run -pl aiproxy-api

# 启动前端（新终端）
cd frontend
npm install
npm run dev
```

4. **访问应用**
- 前端界面: http://localhost:5173
- 后端 API: http://localhost:8080

详细配置说明请参考 [SETUP.md](./SETUP.md)

## 📖 API 使用示例

### 发送消息（兼容 Claude API）
```bash
curl -X POST http://localhost:8080/v1/messages \
  -H "x-api-key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-opus-20240229",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }'
```

### 健康检查
```bash
curl http://localhost:8080/health
```

## 📁 项目结构

```
aiproxy/
├── aiproxy-common/     # 共享组件（实体、工具类、常量）
├── aiproxy-auth/       # 认证授权模块
├── aiproxy-admin/      # 管理接口模块
├── aiproxy-core/       # 核心代理功能
├── aiproxy-api/        # 应用主入口
├── frontend/           # React 前端项目
├── docker-compose.yml  # Docker 编排文件
└── SETUP.md           # 详细配置指南
```

## 🔧 主要功能

### 账号管理
- 支持多个 AI 账号
- 自动切换失败账号
- 账号健康检查
- OAuth Token 自动刷新

### 请求代理
- 完全兼容主流 AI API
- 支持流式和非流式响应
- 智能错误重试
- 请求日志记录

### 监控统计
- 实时请求监控
- API Key 使用统计
- 错误率分析
- 性能指标

### 安全特性
- API Key 认证
- JWT Token 签名
- 请求频率限制
- 数据加密存储

## 🐳 Docker 部署

```bash
# 使用 Docker Compose
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

生产环境部署请参考 [SETUP.md](./SETUP.md#生产环境部署)

## 📊 监控端点

- `/actuator/health` - 健康检查
- `/actuator/metrics` - 应用指标
- `/actuator/info` - 应用信息

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

[MIT License](LICENSE)

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Anthropic Claude](https://www.anthropic.com/claude)
- [OpenAI](https://openai.com)
- [Google Gemini](https://ai.google.dev)
- [React](https://react.dev)

## 📮 联系方式

如有问题，请提交 [Issue](https://github.com/yourusername/aiproxy/issues)