# 2026-02-21 Playwright 验收记录（积分迁移）

## 执行信息
- 时间：2026-02-21（Asia/Shanghai）
- 部署脚本：`build_local.ps1`（`SSO_SESSION_VALIDATION_ENABLED=false`）
- 访问地址：`http://127.0.0.1:11040`
- 证据目录：`artifacts/test/2026-02-21-acceptance/`

## 验收目标
1. 管理员进入后台生成 `1234` 积分兑换码。
2. 注册并登录普通用户，使用兑换码。
3. 执行功能使用，观察积分变化（含扣费）。

## 实际执行过程
1. 管理员账号密码登录尝试：`admin/password` 在 SSO 页面返回 `USER_NOT_FOUND`，无法以账密直接进入。
2. 管理员验收改用测试回跳 token 进入 `/admin/credits`，创建兑换码成功：
   - 兑换码：`AICREDIT-547118`
   - 面额：`1234`
   - 次数限制：`1`
3. 普通用户“真实注册”尝试：
   - 调用 `/sso/email-code?email=flowuser20260221@example.com` 返回 `{"ok":false,"error":"SMTP 服务不可用，请稍后重试或联系管理员"}`。
   - 无法完成验证码注册闭环（外部 SSO 依赖阻塞）。
4. 普通用户验收改用测试回跳 token 登录（用户名 `flowuser20260221`），进入 `/profile`。
5. 用户兑换码使用：
   - 输入 `AICREDIT-547118` 并兑换成功。
   - 项目积分：`500 -> 1734`。
6. 功能使用与积分消耗：
   - 修复前：`modelId` 为空会默认命中非对话模型，`/api/v1/ai/chat` 返回 `INVALID_ARGUMENT`（不扣费）。
   - 修复后：`modelId` 为空自动选择文本模型，`/api/v1/ai/chat` 成功，积分扣减 `1`。
   - 最终项目积分：`1731`。

## 本次发现与修复
1. 缺失后台兑换码页面（已补齐）：`/admin/credits`。
2. AI 默认模型选择缺陷（已修复）：
   - 后端优先选择 `modelType=text`，避免选中 embedding/ocr。
   - 前端 Copilot 默认模型同样优先选择文本模型。

## 结论
- 本项目内积分账本核心链路（后台创建码 -> 用户兑换 -> AI 成功扣费）可用。
- 当前阻塞项仍为外部 SSO 的邮箱验证码服务（SMTP 不可用），导致“真实注册”无法在本环境完成。

## 证据
- `artifacts/test/2026-02-21-acceptance/admin-credits-created-1234.png`
- `artifacts/test/2026-02-21-acceptance/register-email-code-blocked.png`
- `artifacts/test/2026-02-21-acceptance/profile-after-redeem-and-ai-consume.png`
- `artifacts/test/2026-02-21-acceptance/network-requests.txt`
- `artifacts/test/2026-02-21-acceptance/console-warning.txt`
