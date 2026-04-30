# 2026-04-22 backend 宿主机调试与本地直启验收

## 1. 目标

1. `backend/` 作为 VSCode 工作区时，可通过 `F5` 直接启动 `com.ainovel.app.AiNovelApplication`。
2. 新增 Linux 宿主机后端启动脚本 `backend/deploy_local.sh`。
3. 运行时环境统一按 `ENV_FILE` -> `backend/env.txt` -> 根 `env.txt` 解析；找不到环境文件时，调试预处理和 `start/restart` 直接失败。

## 2. 执行记录

1. 打包验证：
   - `cd backend && mvn -q -DskipTests clean package`
2. 测试验证：
   - `cd backend && mvn -q test`
3. VSCode 调试环境预处理：
   - `cd backend && bash .vscode/prepare-env.sh`
   - 结果：成功生成 `backend/target/vscode.env`
   - 环境源：默认回退到仓库根 `env.txt`
   - 关键校验：`PORT=11041` 已写入；MySQL/Redis/Qdrant/Consul/三服务 fallback 使用根 `env.txt` 当前值；占位的 `EXTERNAL_PAY_SERVICE_JWT` 已派生为可启动的 JWT
   - 边界校验：`ENV_FILE=/tmp/...` 会覆盖根 `env.txt`；临时创建 `backend/env.txt` 时会优先于根 `env.txt`；`ENV_FILE` 指向缺失文件时会直接失败
4. Linux 宿主机后端启动：
   - `bash backend/deploy_local.sh start`
   - 结果：`11041` 端口监听成功，生成 `tmp/local-run/backend.out.log`、`tmp/local-run/backend.err.log`
5. 启动后接口烟测：
   - `curl -I 'http://127.0.0.1:11041/api/v1/sso/login?next=%2Fworkbench&state=host-debug-check'`
   - 结果：返回 `302`，`Location` 指向 `USER_HTTP_ADDR` 生成的 SSO 登录入口，不是连接失败
   - 补充：裸请求 `/api/v1/sso/login` 会按接口设计返回 `400 STATE_REQUIRED`
6. 生命周期命令：
   - `bash backend/deploy_local.sh status`
   - `bash backend/deploy_local.sh logs`
   - `bash backend/deploy_local.sh stop`
   - 结果：状态查询、日志查看、停服均可用

## 3. 结论

- 后端宿主机直启链路已打通，`deploy_local.sh` 可独立完成构建、启动、状态检测、日志查看与停服。
- VSCode `launch.json` / `tasks.json` / `prepare-env.sh` 已就位，F5 会先生成 `target/vscode.env`，不再依赖过期的 `application.yml` 旧默认值。
- `application.yml` 默认值已对齐当前 `env.txt`/`build.sh` 口径，避免在缺少显式环境变量时连到错误端口或错误上游地址。

## 4. 备注

- 本次在 CLI 内完成了 `.vscode` 产物与 `prepare-env.sh` 的等价验证；交互式 VSCode `F5` 点击本身未在终端环境中执行。
