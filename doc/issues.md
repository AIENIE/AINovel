# 已解决事项（2026-01-03）
- 补齐 `deploy/nginx/ainovel.conf` 与 `deploy/nginx/ainovel_prod.conf`，`build.sh --init` 与 `build_prod.sh --init` 可安装宿主机 Nginx 配置。
- `build_prod.sh`/`build.sh` 修复为显式 `bash` 调用子脚本，避免因脚本未加可执行位导致 `Permission denied`。
- 后端新增邮件验证码注册（SMTP）、AI Copilot（OpenAI 兼容）、积分/签到/兑换码、后台管理接口，并已接入前端 `src/lib/mock-api.ts` 作为真实 API 适配层。
- `docker-compose.yml` 已注入 `SMTPandLLM.txt` 的 SMTP 与大模型配置，`build_prod.sh --init` 已成功签发并部署 `ainovel.aienie.com` 证书（有效期至 2026-04-03）。

# 待处理事项（2026-01-03）
- Playwright MCP 当前提示 `Browser is already in use`，无法执行浏览器端到端自动化测试（已改用接口级回归测试验证核心功能）。建议待浏览器资源释放后再次运行 Playwright 测试，目标地址：`https://ainovel.aienie.com/`。

# 待处理事项（2026-02-03）
- 在 Windows 环境下无法写入 `C:\\Windows\\System32\\drivers\\etc\\hosts`（权限不足），因此无法将 `ainovel.seekerhut.com` 指向 `127.0.0.1` 以满足“必须访问 http://ainovel.seekerhut.com” 的 Playwright MCP 测试要求。可选解决方案：
  - 以管理员权限手动修改 hosts：添加 `127.0.0.1 ainovel.seekerhut.com`，再执行 `./build.sh --init`（Linux）或按项目说明配置系统 Nginx 反代。
  - 若在 Linux 服务器上执行：使用 `./build.sh --init` 自动补齐 hosts 与系统 Nginx 配置，然后按要求用 Playwright MCP 访问 `http://ainovel.seekerhut.com` 验证。
- 兜底：在 Windows/Docker Desktop 本地环境使用 `http://127.0.0.1:10010` 进行 Playwright 端到端验证（本次已验证工作台与个人中心关键流程）。

# 已解决事项（2026-02-03）
- Windows/Docker Desktop 下前端 `/api/*` 反向代理需要使用 `frontend/nginx.windows.conf`（容器内转发到 `backend:10011`）；Linux 环境同样统一转发到后端 `10011`。

# 待处理事项（2026-02-20）
- 时间：2026-02-20 22:36-22:38（Asia/Shanghai）
- 域名/地址：`http://127.0.0.1:11040/profile`（SSO 跳转至 `http://192.168.1.3:10002/sso/login`）
- 结论：本地部署成功，首页可访问；但 `/profile` 关键路径受外部 SSO 账号阻塞，无法完成登录后双余额页面验收。
- 失败阶段：Web 自测登录步骤（Playwright）。
- 关键错误：SSO 页面返回 `USER_NOT_FOUND`（使用 `admin/password` 登录失败），导致无法进入项目内个人中心页面。
- 证据目录：`artifacts/test/2026-02-20-local-billing/`
  - 截图：`homepage.png`、`profile-login-blocked.png`
  - 日志：`console.log`、`network.log`、`backend.err.tail.log`、`frontend.err.tail.log`

# 已解决事项（2026-02-21）
- 时间：2026-02-21（Asia/Shanghai）
- 问题：`/api/v1/ai/chat` 在 `modelId` 为空时默认选中模型列表第一项，若该模型不是文本对话模型会返回 `INVALID_ARGUMENT` 且无法扣费。
- 修复：
  - 后端 `AiService` 默认模型选择优先 `modelType=text`，并跳过 embedding/ocr 候选。
  - 前端 Copilot 默认模型同步优先文本对话模型。
- 验证：空 `modelId` 调用成功，项目积分按 `1` 积分扣减（`1732 -> 1731`）。

# 待处理事项（2026-02-21）
- 时间：2026-02-21（Asia/Shanghai）
- 结论：外部 SSO 的邮箱验证码发送接口返回 `{\"ok\":false,\"error\":\"SMTP 服务不可用，请稍后重试或联系管理员\"}`，导致无法完成真实注册流程。
- 影响：本地 Playwright 仅能通过测试 token 回跳完成用户登录验收，不能覆盖真实“注册-验证码-登录”闭环。
- 证据目录：`artifacts/test/2026-02-21-acceptance/`
  - 截图：`register-email-code-blocked.png`
  - 网络：`network-requests.txt`
  - 验收记录：`doc/test/2026-02-21-playwright-acceptance.md`
