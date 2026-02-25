# 2026-02-25 安全加固联调与验收记录

## 1. 目标
- 验证三服务安全加固后，AINovel 对接链路可用。
- 验证统一时区（UTC+8）后，鉴权与会话校验稳定。
- 验证 `build.sh` 本地部署、关键 API、Playwright 页面全链路。

## 2. 部署
- 命令：`sudo -E bash build.sh`
- 结果：
  - 前端：`npm test` + `vite build` 成功
  - 后端：`mvn test` + 打包成功
  - `docker compose` 启动成功
  - `nginx -t` 通过

## 3. 关键 API 验收（新登录 token）
- 环境：`http://127.0.0.1:11041`
- 结果：
  - `GET /api/v1/user/profile` -> 200
  - `GET /api/v1/user/summary` -> 200
  - `GET /api/v1/ai/models` -> 200
  - `GET /api/v2/users/me/model-preferences` -> 200
  - `POST /api/v1/user/credits/convert`：
    - `goodboy95`（有通用积分）-> 200
    - `queueuser26`（通用积分 0）-> 400 `INSUFFICIENT_PUBLIC_BALANCE`（符合预期）

## 4. Playwright 端到端
- 账号：`queueuser26`
- 流程：
  1. 登录（SSO）
  2. 工作台
  3. 小说管理
  4. 世界构建
  5. 素材库
  6. 设置（模型偏好、工作台体验）
  7. 个人中心
  8. 退出登录
  9. 再登录回到工作台
- 结果：通过

## 5. 日志观察
- 未观察到：
  - `Userservice session validation RPC failed`
  - `UNAUTHENTICATED` / `Invalid token`
- 观察到：
  - Consul 发现失败后回退 `USER_GRPC_ADDR`（环境网络导致），回退链路可用。

## 6. 结论
- 三服务安全加固后的对接已可用。
- 统一时区（UTC+8）生效。
- 本轮部署、接口、页面主流程验收通过。
