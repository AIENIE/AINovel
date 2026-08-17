# 管理后台

## 入口与认证

- 登录页：`/admin/login`；后台前缀：`/admin/*`。
- 管理员使用本地 `/api/v1/admin-auth/*` 和服务端不透明会话，不复用普通用户 SSO token。浏览器只持有 `HttpOnly`、`Secure`、`SameSite=Strict` Cookie；数据库只保存 token 的 SHA-256 摘要，响应 JSON 和浏览器存储都不保存会话 token。
- `ENV` 与 `AUTH_MODE` 从进程环境读取且必须精确匹配。Windows 本地由 `Start-Local.ps1` 从 ACL 收紧的仓库外安全文件注入；非 Windows Docker 部署只由经过门禁的 `0600` 普通文件 `env.txt` 注入，宿主同名变量不能补齐或覆盖。只允许 `local/password`、`local/totp`、`test/totp`、`production/totp`；测试和生产不能降级到单密码模式。
- 固定单一管理员由 `ADMIN_USERNAME` 标识；`ADMIN_PASSWORD_HASH` 保存 BCrypt 摘要。TOTP 模式的日常登录也先校验账号密码，再签发 120 秒、最多 5 次尝试的 TOTP 挑战。
- TOTP 密钥以 AES-GCM 加密保存，使用版本化 `ADMIN_TOTP_ENCRYPTION_KEYS` 密钥环和 `ADMIN_TOTP_ACTIVE_KEY_VERSION`。
- 后端普通管理接口只接受独立权限 `AUTH_LOCAL_ADMIN`。普通 SSO 的 `ROLE_ADMIN` 无法进入本地后台；恢复码登录只授予 `AUTH_LOCAL_ADMIN_RECOVERY`，仅允许查询会话、退出和重新绑定验证器。
- 所有后台写请求必须来自 `ADMIN_TRUSTED_ORIGINS`。TOTP 模式中的删除、维护配置、积分发放、兑换码创建、素材合并、模型路由更新和 G2 状态推进还需要操作级证明：首次请求返回 428，动态码验证后得到最长 60 秒、一次消费并绑定用户/会话/动作/请求目标的 proof。`local/password` 模式豁免该二次验证。
- 恢复码只在生成或重置成功时显示一次；丢失验证器和全部恢复码时只能通过受控运维操作重置。

完整状态与信任边界见 [`admin-auth.md`](admin-auth.md)。

## 当前能力

| 区域 | 当前边界 |
| --- | --- |
| 运营概览 | 用户、积分消耗、待审素材、创作资产和高风险质量记录 |
| 项目用户 | AINovel 本地用户镜像、积分与资产统计 |
| 素材治理 | 审核、重复候选、合并和引用查询 |
| 创作资产 | 故事、世界观和稿件只读审计 |
| 质量巡检 | 文本与剧情质量运行记录及风险筛选 |
| G2 活动 | 活动、邀请、样本、投票和准入门槛 |
| 专属积分 | 本地账户、兑换码、转换订单和流水 |
| 运维观测 | 请求指标、依赖健康、派生告警、记录检索和脱敏诊断 |
| 系统维护 | AINovel 本地维护模式 |

## 服务边界

- AINovel 管理本项目素材、创作资产、质量和项目专属积分。
- user-service 管理账号、注册、SSO 与全局用户能力。
- ai-service 管理模型池、调用凭据与全局 AI 成本。
- pay-service 管理通用积分、充值和全局账务。

运维观测只展示 AINovel 调用视角，不修改外部服务后台配置。接口见 [`../api/admin.md`](../api/admin.md)，用户操作见 [`../../user-doc/08-管理员手册.md`](../../user-doc/08-管理员手册.md)。
