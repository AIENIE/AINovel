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
