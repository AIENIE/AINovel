# 模块说明：后台管理系统

## 入口与权限

- 路由前缀：`/admin`
- 登录页：`/admin/login`
- 访问权限：`ROLE_ADMIN`

## 认证模型

- 管理员链路使用本地账号密码登录（`/api/v1/admin-auth/login`），不依赖 SSO 页面。
- 登录成功后前端将 token 存在 `localStorage.admin_token`。
- 每次进入管理路由时会调用 `/api/v1/admin-auth/me` 校验会话；失效则清理 token 并跳回 `/admin/login`。

## 页面与能力

- `仪表盘`：用户规模、新增、待审素材、消费指标概览
- `用户管理`：用户检索、封禁/解封（透传 user-service）
- `积分与兑换码`：
  - 创建/查看本地兑换码
  - 查看通用积分兑换订单（含前后余额快照）
  - 查看全站项目积分流水
- `系统设置`：注册开关、维护开关、签到区间、SMTP 参数

## 数据来源

- 用户域：`UserAdminRemoteClient`（user-service HTTP）
- 积分域：`EconomyService`（AINovel 本地项目账本 + pay-service 兑换结果）
