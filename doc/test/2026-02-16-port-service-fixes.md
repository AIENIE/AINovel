# 2026-02-16 联调问题与修复记录

## 1. 端口与调用地址不一致
- 现象：
  - 前端开发端口仍是 `8080`，Nginx/Compose 仍是 `10001/20001`。
  - 后端默认端口 `8080`，与 AGENTS 基线（`10010/10011`）不一致。
- 修复：
  - 前端：`vite.config.ts` 改为 `10010`，并配置 `/api -> http://127.0.0.1:10011`。
  - 后端：`application.yml` 默认端口改为 `10011`。
  - 部署：`docker-compose*.yml`、`frontend/nginx*.conf`、`build.sh`、`build_prod.sh`、`Dockerfile` 全量同步到 `10010/10011`。

## 2. 后端默认数据库地址与编码导致启动失败
- 现象：
  - 启动时出现 JDBC 连接异常，历史默认值为固定 IP，且字符集参数不兼容当前驱动组合。
- 修复：
  - `application.yml` 改为优先 `DB_URL`，否则由 `MYSQL_HOST/MYSQL_PORT/MYSQL_DB` 组装。
  - 默认 JDBC 参数统一为 `useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false`。
  - 用户名密码改为支持 `DB_USERNAME/DB_PASSWORD` 与 `MYSQL_USER/MYSQL_PASSWORD` 双通道回退。

## 3. 会话远程校验在微服务不可达时阻塞
- 现象：
  - Consul/用户服务不可达时，请求可能等待过久。
- 修复：
  - 新增 `ConsulUserGrpcEndpointResolver` + `UserSessionValidationProperties`。
  - `UserSessionValidator` 改为：
    1. Consul 发现 userservice gRPC；
    2. 失败回退 `USER_GRPC_ADDR`；
    3. 增加短超时 TCP 可达性检查，快速失败避免接口长时间阻塞。
  - 新增单测：`UserSessionValidatorInfrastructureTests`（覆盖 Consul 解析缓存、地址解析与异常输入）。

## 4. 本地 SSO 域名不可解析
- 现象：
  - Playwright 点击“注册/登录”后跳转到 `ainovel.seekerhut.com`，报 `DNS_PROBE_FINISHED_NXDOMAIN`。
- 修复：
  - `frontend/src/lib/sso.ts` 增加开发环境回退策略（开发机默认使用测试域名入口，测试/生产域名保持同源）。
  - 本地功能验收阶段采用 `SSO_SESSION_VALIDATION_ENABLED=false` 进行链路验证（避免因外部 userservice/Consul 不可达阻塞功能验证）。

## 5. 验证结果（本机）
- 后端：`http://127.0.0.1:10011/api/v3/api-docs` 返回 `200`。
- 前端：`http://127.0.0.1:10010/` 可访问。
- MySQL：后端日志出现 `HikariPool-1 - Added connection`。
- Redis：对公共 Redis `192.168.1.4:6379` 发送 `PING` 返回 `+PONG`。
- 注册/登录与功能：
  - 通过 `/sso/callback#access_token=...` 完成登录回跳；
  - 工作台“故事构思”生成成功；
  - 个人中心“每日签到”成功，积分由 `500` 增加到 `528`。
