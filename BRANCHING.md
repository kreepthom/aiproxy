# Git 分支管理策略

## 概述

本项目采用简化版 Git Flow 分支管理策略，旨在保证代码质量的同时保持开发效率。

## 分支结构

### 长期分支

#### 1. main (主分支)
- **用途**: 生产环境代码，始终保持稳定可部署状态
- **权限**: 保护分支，禁止直接推送，只能通过 Pull Request 合并
- **合并来源**: `develop` 分支或 `hotfix/*` 分支
- **标签**: 每次发布打 tag (如 v1.0.0)

#### 2. develop (开发分支)
- **用途**: 集成最新开发功能，作为开发的基准分支
- **权限**: 保护分支，建议通过 Pull Request 合并
- **合并来源**: `feature/*`、`bugfix/*` 分支
- **部署**: 可部署到测试环境

### 临时分支

#### 3. feature/* (功能分支)
- **用途**: 开发新功能
- **命名规范**: 
  - `feature/功能名称` (如: `feature/oauth-google`)
  - `feature/issue编号-简述` (如: `feature/123-add-gemini-api`)
- **创建自**: `develop`
- **合并至**: `develop`
- **生命周期**: 功能完成并合并后删除

#### 4. bugfix/* (缺陷修复分支)
- **用途**: 修复开发环境中发现的 bug
- **命名规范**: 
  - `bugfix/问题描述` (如: `bugfix/token-validation`)
  - `bugfix/issue编号-简述` (如: `bugfix/456-fix-redis-timeout`)
- **创建自**: `develop`
- **合并至**: `develop`
- **生命周期**: 修复完成并合并后删除

#### 5. hotfix/* (热修复分支)
- **用途**: 紧急修复生产环境问题
- **命名规范**: 
  - `hotfix/紧急问题` (如: `hotfix/security-patch`)
  - `hotfix/版本号` (如: `hotfix/1.0.1`)
- **创建自**: `main`
- **合并至**: `main` 和 `develop`
- **生命周期**: 修复完成并合并后删除

#### 6. release/* (发布分支，可选)
- **用途**: 准备新版本发布，只允许 bug 修复
- **命名规范**: `release/版本号` (如: `release/1.2.0`)
- **创建自**: `develop`
- **合并至**: `main` 和 `develop`
- **生命周期**: 发布完成后删除

## 工作流程

### 1. 开发新功能

```bash
# 从 develop 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/new-feature

# 开发功能...
git add .
git commit -m "feat: 添加新功能"

# 推送到远程
git push origin feature/new-feature

# 创建 Pull Request 到 develop
# 代码审查通过后合并
```

### 2. 修复开发环境 Bug

```bash
# 从 develop 创建修复分支
git checkout develop
git pull origin develop
git checkout -b bugfix/fix-issue

# 修复问题...
git add .
git commit -m "fix: 修复问题描述"

# 推送并创建 PR
git push origin bugfix/fix-issue
```

### 3. 紧急修复生产问题

```bash
# 从 main 创建热修复分支
git checkout main
git pull origin main
git checkout -b hotfix/critical-fix

# 修复问题...
git add .
git commit -m "hotfix: 紧急修复说明"

# 合并到 main
git checkout main
git merge hotfix/critical-fix
git tag v1.0.1

# 同步到 develop
git checkout develop
git merge hotfix/critical-fix

# 推送所有更改
git push origin main develop --tags
```

### 4. 发布新版本

```bash
# 从 develop 创建发布分支（可选）
git checkout develop
git checkout -b release/1.2.0

# 只进行 bug 修复，不添加新功能
# 测试通过后...

# 合并到 main
git checkout main
git merge release/1.2.0
git tag v1.2.0

# 合并回 develop
git checkout develop
git merge release/1.2.0

# 推送
git push origin main develop --tags
```

## Commit 规范

### 格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型
- **feat**: 新功能
- **fix**: 修复 bug
- **docs**: 文档更新
- **style**: 代码格式调整（不影响功能）
- **refactor**: 重构（既不是新功能也不是修复）
- **perf**: 性能优化
- **test**: 测试相关
- **chore**: 构建过程或辅助工具的变动
- **hotfix**: 紧急修复

### 示例
```bash
feat(auth): 添加 Google OAuth 支持

- 实现 Google OAuth 2.0 认证流程
- 添加配置项支持
- 更新文档

Closes #123
```

## 版本号规范

采用语义化版本 (Semantic Versioning)：`MAJOR.MINOR.PATCH`

- **MAJOR**: 不兼容的 API 变更
- **MINOR**: 向后兼容的功能新增
- **PATCH**: 向后兼容的问题修复

示例：
- `1.0.0`: 首个正式版本
- `1.1.0`: 添加新功能
- `1.1.1`: 修复 bug
- `2.0.0`: 重大更新，可能不兼容

## 分支保护规则

### main 分支
- ✅ 禁止直接推送
- ✅ 必须通过 Pull Request
- ✅ 需要至少 1 个审查通过
- ✅ 必须通过 CI/CD 测试
- ✅ 管理员也需要遵守规则

### develop 分支
- ✅ 禁止强制推送
- ✅ 建议通过 Pull Request
- ✅ 自动运行测试

## 最佳实践

1. **频繁提交**: 小步快跑，每个提交只做一件事
2. **及时合并**: 功能完成后尽快合并，避免冲突
3. **保持同步**: 经常从上游分支拉取最新代码
4. **清理分支**: 合并后删除临时分支
5. **写好提交信息**: 清晰描述改动内容
6. **代码审查**: 重要改动必须经过审查
7. **自动化测试**: 确保所有测试通过再合并

## 常见问题

### Q: 什么时候使用 feature 分支？
A: 开发任何新功能时，无论大小都应该使用 feature 分支。

### Q: hotfix 和 bugfix 的区别？
A: hotfix 用于修复生产环境的紧急问题，从 main 分支创建；bugfix 用于修复开发中的问题，从 develop 创建。

### Q: 是否必须使用 release 分支？
A: 不是必须的。小项目可以直接从 develop 合并到 main。大项目或需要预发布测试时使用 release 分支。

### Q: 分支命名可以用中文吗？
A: 技术上可以，但建议使用英文，避免编码问题。可以在 commit message 中使用中文详细说明。

## 快速参考

```bash
# 查看所有分支
git branch -a

# 创建并切换分支
git checkout -b feature/new-feature

# 删除本地分支
git branch -d feature/old-feature

# 删除远程分支
git push origin --delete feature/old-feature

# 合并分支
git merge feature/new-feature

# 变基（保持线性历史）
git rebase develop

# 推送标签
git push origin v1.0.0
```

## 工具推荐

- **Git Flow 工具**: `git flow init` 自动化分支管理
- **GitHub Flow**: 适合持续部署的简化流程
- **GitLab Flow**: 结合环境部署的分支策略
- **SourceTree**: 可视化 Git 管理工具
- **GitKraken**: 跨平台 Git GUI 客户端

---

最后更新: 2025-08-19