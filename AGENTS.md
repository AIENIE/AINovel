- 项目名: AINovel
- 项目归属：aienie
- 项目类型：业务项目（projects）
- 前端技术栈: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端技术栈: Java Spring boot
- 本地环境域名: localainovel.testhut.top
- 预发布环境域名: ainovel.testhut.top
- 生产环境目标域名（切换完成前不得作为当前入口）: ainovel.seekerhut.com
- 前端对外端口: 11040
- 后端对外端口: 11041

- Windows 本地开发、测试和验收只使用 PowerShell 7；WSL 不再作为本项目的执行环境。
- Windows 本地入口：`scripts/windows/Start-Local.ps1`；停止入口：`scripts/windows/Stop-Local.ps1`。启动脚本要求先完成前端构建，并要求 MySQL、Redis、Qdrant 位于 `172.20.0.2`，ai-service、user-service、pay-service 已在本机对应端口启动。
- Windows 本地安全配置默认位于 `%LOCALAPPDATA%\Aienie\secrets\ainovel-localbase.env`。`secrets` 目录和配置文件的 owner 必须是当前用户或本机 Administrators；目录必须是非重解析普通目录、关闭 ACL 继承，并以 `(OI)(CI)` Full Control 只允许当前用户、SYSTEM 和本机 Administrators；配置文件不得提交，必须是非重解析普通文件、关闭 ACL 继承，并以显式 Full Control 只允许同三个主体。初始化不得自动夺取所有权。
- Windows 本地访问入口为 `http://127.0.0.1:11040`，后端为 `http://127.0.0.1:11041`；三服务通过 loopback 访问，不依赖本地域名或反向代理。
- 非 Windows Docker 部署入口仍为 `build.sh`。脚本只执行 Docker Compose 构建与部署，并要求仓库内 Git 忽略的 `env.txt` 是 `0600` 普通文件；脚本以同一文件完成 Compose `--env-file` 插值并只读挂载进后端容器，宿主同名环境变量不能补齐或覆盖该文件契约。该入口不得用于 Windows 本地开发或验收。
- 跨服务正式契约位于与 `aienie-projects` 同级的 `aienie-doc` 仓库 `interfaces/<service>/`；从本项目根目录按标准布局解析为 `..\..\aienie-doc\interfaces\<service>\`。不要硬编码用户主目录；目录缺失时先报告契约缺失，不把运行时反射当作正式契约。

## 后续分期事项 / Pending Phases

后续开发的唯一权威入口是：

**`doc/roadmap.md`**

当前已完成 G2 Step 1 和 G1-A P0。下一项主线工作是收集 G2 真实盲测数据；在同一活动达到 100 张有效票、20 个成功样本对、10 名实际评审且精雕胜率不低于 55% 前，不得启动 G2 完整方案 A。

任何专题提案、研究笔记或历史分支都不能替代 `doc/roadmap.md` 的状态与优先级。阶段完成、跳过或阻塞时，应在同一批次更新路线图。
