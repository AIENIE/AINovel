# Admin API（需管理员权限）

## 管理员本地登录

- `POST /api/v1/admin-auth/login`
- `GET /api/v1/admin-auth/me`
- `POST /api/v1/admin-auth/logout`

## 运营概览

- `GET /api/v1/admin/dashboard`
  - 返回用户数、今日新增、项目积分消耗、API 错误率、待审素材数。
- `GET /api/v1/admin/assets/summary`
  - 返回故事、世界观、稿件、素材、待审素材、高风险质量记录数量。

## 项目用户

- `GET /api/v1/admin/users?search=demo`
  - 返回 AINovel 本地用户镜像、项目专属积分、通用积分快照、故事/世界观数量。
  - 不提供全局封禁、邮箱、短信、SSO 配置；这些由 user-service 管理。

## 素材治理

- `GET /api/v1/admin/materials/pending`
- `POST /api/v1/admin/materials/{id}/approve`
- `POST /api/v1/admin/materials/{id}/reject`
- `POST /api/v1/admin/materials/duplicates`
- `POST /api/v1/admin/materials/merge`
- `GET /api/v1/admin/materials/{id}/citations`

## 创作资产

- `GET /api/v1/admin/assets/stories`
- `GET /api/v1/admin/assets/worlds`
- `GET /api/v1/admin/assets/manuscripts`
  - 只读审计接口；后台不直接修改用户创作内容。

## 质量巡检

- `GET /api/v1/admin/quality/runs`
  - 聚合 `slop_quality_runs` 与 `plot_quality_runs`。

## 项目专属积分

- `POST /api/v1/admin/credits/grant`
  - 本地给用户增加 AINovel 项目专属积分，写入本地账户与流水。
- `GET /api/v1/admin/redeem-codes`
- `POST /api/v1/admin/redeem-codes`
- `GET /api/v1/admin/credits/conversions?page=0&size=20`
  - 查询通用积分兑换为 AINovel 专属积分的本地订单快照。
- `GET /api/v1/admin/credits/ledger?page=0&size=20`
  - 查询 AINovel 本地项目专属积分流水。

## 系统维护

- `GET /api/v1/admin/system-config`
- `PUT /api/v1/admin/system-config`
  - 请求/响应字段：`{maintenanceMode:boolean}`。
  - SMTP、注册、签到、AI 模型池/API Key 不在 AINovel 后台配置。
