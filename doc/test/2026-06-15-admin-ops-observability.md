# 后台运维观测第二批验证记录

日期：2026-06-15

## 开发内容

- 新增 `/admin/ops` 运维观测页。
- 新增 `/api/v1/admin/ops/*` 只读接口：
  - `summary`
  - `dependencies`
  - `events`
  - `audit`
  - `alerts`
  - `diagnostics`
- 扩展当前进程请求指标：状态码分布、5xx 错误率、p95/p99 延迟和最近错误。
- 新增 AINovel 结构化记录写入：
  - `ainovel-admin-audit-YYYY-MM-DD.ndjson`
  - `ainovel-ops-events-YYYY-MM-DD.ndjson`
  - `ainovel-dependency-probes-YYYY-MM-DD.ndjson`
- 审计覆盖管理员登录、维护模式、素材治理、积分发放和兑换码创建。
- ES 查询仅使用 AINovel 自有索引前缀，默认 `aienie-local-ainovel-*`。

## 服务边界

- 不管理 user-service 的账号、SMTP、短信、SSO 或全局用户配置。
- 不管理 ai-service 的模型、API Key、调用方密钥、成本或全局告警。
- 不管理 pay-service 的充值、签到、通用账务、额度或全局告警。
- 外部依赖只展示 AINovel 调用视角的健康状态和脱敏 endpoint。

## 已执行验证

```bash
cd backend && mvn -q -Dtest=ApiRequestMetricsSnapshotTest,OpsRecordFileSinkTest,AdminDashboardStatsTest test
cd backend && mvn -q test
cd frontend && npm test -- --run src/lib/__tests__/mock-api.test.ts
cd frontend && npm test -- --run
cd frontend && npm run build
git diff --check
sudo -E bash build.sh
```

## 本地部署验收结果

- `https://ainovel.localhut.com/actuator/health` 返回 200。
- `https://ainovel.localhut.com/admin/ops` 返回 200。
- 未登录调用 `/api/v1/admin-auth/me` 返回 403。
- 未登录调用 `/api/v1/admin/ops/summary` 返回 403。
- 管理员登录返回 200。
- 管理员 token 调用 `/api/v1/admin/ops/summary`、`/dependencies`、`/events`、`/audit` 均返回 200。
- 当前本地 ES 查询不可用，`events/audit` 返回 `available=false`，页面显示记录检索不可用且业务接口不受影响。
- `/home/duwei/aienie-runtime/observability/data/ainovel/records/` 已生成：
  - `ainovel-admin-audit-2026-06-15.ndjson`
  - `ainovel-dependency-probes-2026-06-15.ndjson`
