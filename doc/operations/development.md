# 开发指南

## 环境要求

- Node.js >=22.23.2 <23
- pnpm >=11.22.0 <12（默认通过 Corepack 调用 11.22.0）
- JDK 25
- Maven 3.9+

## 本地检查

后端：

```bash
mvn -q -f backend/pom.xml test
```

前端：

```bash
cd frontend
corepack pnpm@11.22.0 install --frozen-lockfile
corepack pnpm@11.22.0 run test
corepack pnpm@11.22.0 run build
```

标准本地运行入口是 [`windows-native.md`](windows-native.md) 的 `scripts/windows/` 契约，其私有环境文件位于仓库外。如需宿主机 IDE 调试，可从 `env.example` 复制出本地、Git 忽略的 `env.txt` 作为键清单对照；该文件包含运行秘密，不得提交。

## 会话校验超时

会话 gRPC 校验统一使用 `sso.session-validation.timeout-ms`，默认 5000ms，沿用最小 500ms 的实际调用时限。
显式新属性优先；未配置时依次读取新别名 `runtime.configuration.USER_SESSION_GRPC_TIMEOUT_MS` / `USER_SESSION_GRPC_TIMEOUT_MS`、
旧属性 `app.external.session-timeout-ms`、旧别名 `runtime.configuration.EXTERNAL_SESSION_TIMEOUT_MS` / `EXTERNAL_SESSION_TIMEOUT_MS`，最后使用 5000ms。
同一 canonical 属性仍按 Spring ConfigData 优先级解析。旧键保留兼容，不再另设 3000ms 默认或作为第二个运行权威。

RPC 失败日志只记录固定 `stage`、gRPC 状态枚举、实际 `timeoutMs` 与异常类型，保留已脱敏调用栈；不记录原始异常消息、令牌、会话 ID 或目标地址。
时限耗尽或依赖暂不可用仍返回 `503 SESSION_VALIDATION_UNAVAILABLE`，远端明确否定会话才返回 `401 SESSION_INVALID`；10 秒有效结果缓存不变，不以延长缓存或绕过校验恢复登录。

## 修改约束

- 新数据库变更新增 `backend/src/main/resources/db/migration/V{n}__*.sql`。
- API 行为变化同步更新 [`../api/`](../api/)。
- 当前架构变化同步更新 [`../architecture/`](../architecture/)。
- 后续计划只更新 [`../roadmap.md`](../roadmap.md)，不在专题文档复制第二份待办列表。

## 本轮验证记录（2026-09-12）

Windows 根入口的 `Action` 为 `Start|Build|Test|Status|Stop`，`Level` 为 `L1|L2|L3`，`Component` 为 `All|Backend|Frontend`。AINovel L2 退出码为 `0`：后端 `336` 项通过、`12` 项条件跳过，前端 `34` 个 Vitest 文件/`136` 项通过并完成 lint、类型检查和构建；跳过项为外部 MySQL/Testcontainers 与付费质量回归。前后端 VS Code 断点已命中。
