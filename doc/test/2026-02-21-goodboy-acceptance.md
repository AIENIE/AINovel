# 2026-02-21 Playwright 验收记录（goodboy95 账号）

## 执行信息
- 时间：2026-02-21（Asia/Shanghai）
- 部署脚本：`build_local.ps1`
- 访问地址：`http://ainovel.seekerhut.com`
- 验收账号：`goodboy95 / superhs2cr1`
- 证据目录：`artifacts/test/2026-02-21-goodboy-acceptance/`

## 验收目标
1. 在后台管理页创建 `1234` 积分兑换码。
2. 使用 `goodboy95` 登录并兑换兑换码。
3. 执行主要功能并观察积分消耗链路。

## 实际执行过程
1. `goodboy95` 账密登录 SSO 成功，回跳到 `/workbench`。
2. 在 `/admin/credits` 创建 `1234` 积分兑换码，生成并使用的兑换码：
   - `AICREDIT-585245`
3. 用户在 `/profile` 完成兑换：
   - 签到后积分：`500 -> 515`
   - 兑换后积分：`515 -> 1749`
4. 调用 `POST /api/v1/ai/chat`（`modelId` 为空）成功，扣费：
   - `1749 -> 1748`
5. 关键页面与核心操作回归：
   - 页面：`/workbench`、`/novels`、`/worlds`、`/materials`、`/settings`、`/profile`
   - 操作：新建小说、新建世界、手动创建素材、设置页保存、管理员路由鉴权（普通用户访问 `/admin/dashboard` 被拦截并回到 `/workbench`）。

## 本次发现与修复
1. 问题：SSO 登录返回 token 后，AINovel 仍出现 `/api/v1/user/profile` `403`。
2. 根因：后端仅支持本地密钥验签，无法兼容 user-service 签发的异签名 token。
3. 修复：
   - `JwtAuthFilter` 增加兼容路径：本地验签失败时，解析 payload 的 `uid/sid` 并执行远程 `validateSession`；通过后建立登录态。
   - 未通过远程会话校验的未验签 token 仍拒绝。
4. 测试：
   - `JwtAuthFilterTest`：覆盖外部 token 会话校验通过/失败与本地验签路径。

## 结论
- 指定账号登录、后台创建兑换码、兑换加分、AI 扣费链路均已通过。
- 当前仍存在外部 SSO 注册验证码 SMTP 阻塞（历史问题），不影响本次“登录-兑换-消费”闭环验收。

## 证据
- `artifacts/test/2026-02-21-goodboy-acceptance/admin-credits-code-created.png`
- `artifacts/test/2026-02-21-goodboy-acceptance/profile-final-credits.png`
- `artifacts/test/2026-02-21-goodboy-acceptance/network-requests.txt`
- `artifacts/test/2026-02-21-goodboy-acceptance/console-warning.txt`
