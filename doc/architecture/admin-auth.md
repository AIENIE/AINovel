# 管理员认证与会话流

## 策略入口

后端只从进程 OS 环境读取 `ENV` 和 `AUTH_MODE`，不接受 Spring profile、配置文件默认值、大小写转换、引号或前后空格。启动时只允许：

| ENV | AUTH_MODE | 行为 |
| --- | --- | --- |
| `local` | `password` | 账号密码成功后直接建立完整本地管理员会话；不要求或解析 TOTP keyring；高风险操作 proof 豁免 |
| `local` | `totp` | 密码优先，再完成绑定、TOTP 或恢复流程 |
| `test` | `totp` | 与生产相同的 TOTP 强制策略 |
| `production` | `totp` | TOTP 强制；共享限流存储不可用时失败关闭 |

## 正常登录

1. `POST /admin-auth/login` 校验固定账号和 BCrypt 密码，并应用账户、IP、组合与全局限流。
2. 密码模式直接生成 256-bit 随机 session token；TOTP 模式生成 256-bit 随机 challenge，TTL 120 秒、最多尝试 5 次。
3. 首次使用由密码 challenge 换取加密的绑定 challenge；确认 SHA1/6 位/30 秒/前后一个时间步的 TOTP 后落库密钥和 Argon2 恢复码。
4. 日常 TOTP 登录在数据库中原子推进 `last_accepted_timestep`，同一时间步不能重复使用。
5. 浏览器只接收 `HttpOnly; Secure; SameSite=Strict` Cookie；数据库只存 session token 的 SHA-256 摘要和认证强度。请求解析为 `AUTH_LOCAL_ADMIN`，普通 SSO `ROLE_ADMIN` 不具备该权限。

## 恢复与重绑

恢复码登录复用已经通过密码的 LOGIN challenge。Argon2 匹配成功后原子消费恢复码，只签发 `AUTH_LOCAL_ADMIN_RECOVERY` 受限会话。该会话只能查看自身状态、退出、开始并确认新的验证器绑定；确认重绑后撤销旧会话、替换密钥和全部恢复码，再签发完整会话。

## 写请求与高风险操作

所有管理员认证和业务写请求必须通过精确可信 Origin（无 Origin 时允许可信 Referer）检查。TOTP 模式中的高风险操作流程：

1. 业务请求缺少有效 proof，服务端把用户、session 摘要、HTTP 动作以及路径/查询/请求体 SHA-256 绑定到 120 秒 challenge，返回 428。
2. 完整本地管理员会话提交 challenge 和当前 TOTP；验证成功后原子消费 TOTP 时间步与 challenge，并签发 60 秒 proof。数据库只存 proof 摘要。
3. 客户端用 `X-Admin-Operation-Proof` 重试完全相同的请求。proof 在业务控制器执行前原子消费；会话、动作或目标不同、过期或重复使用都失败。

challenge、验证码、恢复码、原始 session 和 proof 只能短暂存在于内存或受保护 Cookie，不进入 URL、浏览器持久化、应用日志或审计详情。
