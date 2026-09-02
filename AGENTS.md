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

- 部署入口: Linux 服务器发布只通过发版中心执行 `scripts/ci/build-release.sh`（两阶段构建与运行时契约见 `scripts/ci/README.md`）；运行时 `env.txt` 由 config-center 管理、经发版中心以 `0600` 普通文件只读挂载进后端容器，仓库、构建输入与发布产物都不携带运行时配置。本地开发在 Windows 直跑 `scripts/windows/` 入口，不使用 Docker。

## 后续分期事项 / Pending Phases

后续开发的唯一权威入口是：

**`doc/roadmap.md`**

当前已完成 G2 Step 1 和 G1-A P0。下一项主线工作是收集 G2 真实盲测数据；在同一活动达到 100 张有效票、20 个成功样本对、10 名实际评审且精雕胜率不低于 55% 前，不得启动 G2 完整方案 A。

任何专题提案、研究笔记或历史分支都不能替代 `doc/roadmap.md` 的状态与优先级。阶段完成、跳过或阻塞时，应在同一批次更新路线图。
