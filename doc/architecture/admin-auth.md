# 管理员认证与会话流

## 策略入口

后端认证策略读取有效 YAML 中的 ENV 和 AUTH_MODE，不接受大小写转换或前后空格；profile 和外部 YAML 按 Spring 原生优先级加载。Windows 本地进程环境由 `Start-Local.ps1` 从 ACL 收紧的仓库外安全文件注入；非 Windows Docker 部署只能由经过门禁的 `0600` 普通文件 `env.txt` 提供，宿主同名变量不能补齐或覆盖。启动时只允许：

| ENV | AUTH_MODE | 行为 |
| --- | --- | --- |
| `local` | `password` | 账号密码成功后直接建立完整本地管理员会话；不要求 TOTP keyring；已绑定管理员获取紧急码时需要有效密钥环；高风险操作 proof 豁免 |
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

恢复码登录复用已经通过密码的 LOGIN challenge。Argon2 匹配成功后原子消费恢复码，只签发 `AUTH_LOCAL_ADMIN_RECOVERY` 受限会话。该会话只能查看自身状态、退出、开始并确认新的验证器绑定；确认重绑后撤销旧会话、挑战和操作证明，替换动态码凭据，再签发完整会话。未使用的紧急码继续有效；已经消费的码即使取消重绑也不会恢复。恢复授权最长 10 分钟。

## 写请求与高风险操作

所有管理员认证和业务写请求必须通过精确可信 Origin（无 Origin 时允许可信 Referer）检查。TOTP 模式中的高风险操作流程：

1. 业务请求缺少有效 proof，服务端把用户、session 摘要、HTTP 动作以及路径/查询/请求体 SHA-256 绑定到 120 秒 challenge，返回 428。
2. 完整本地管理员会话提交 challenge 和当前 TOTP；验证成功后原子消费 TOTP 时间步与 challenge，并签发 60 秒 proof。数据库只存 proof 摘要。
3. 客户端用 `X-Admin-Operation-Proof` 重试完全相同的请求。proof 在业务控制器执行前原子消费；会话、动作或目标不同、过期或重复使用都失败。

challenge、验证码、恢复码、原始 session 和 proof 只能短暂存在于内存或受保护 Cookie，不进入 URL、浏览器持久化、应用日志或审计详情。


## 保留紧急码（V24）

首次绑定生成 10 个 128 位紧急码；安全设置的“获取紧急码”在完整会话及当前动态码验证后返回全部可用码，并只补足失效数量。本地密码模式免动态码验证，仍要求已绑定。输入接受大小写、空格和连字符。数据库保存 Argon2 验证摘要与 AES-GCM 密文、nonce 和密钥版本，加密上下文绑定 AINovel、管理员及码记录。明文只存在当前页面内存，支持复制和下载，关闭后清除；响应禁止缓存。

获取、消费和重绑使用 `admin_emergency_subjects` 的管理员级事务锁，有效码使用锁定读取。重绑 challenge 绑定恢复会话与凭据版本。V24 通过一次性标记废弃全部历史哈希恢复码、历史恢复会话和待确认挑战，保留已绑定凭据及完整会话；迁移重跑不废弃新码和新恢复会话。密钥轮换须保留旧密钥版本，获取时重新加密未用码，码值不变。
