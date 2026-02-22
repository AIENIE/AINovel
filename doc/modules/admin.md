# 模块说明：后台管理系统

## 入口与权限

- 路由前缀：`/admin`
- 访问权限：`ROLE_ADMIN`

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
- 积分域：`EconomyService`（AINovel 本地项目账本 + pay-service兑换结果）

