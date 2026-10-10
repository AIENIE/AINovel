- 项目名: AINovel
- 项目归属：aienie
- 项目类型：业务项目（projects）
- 前端技术栈: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端技术栈: Java Spring boot

| 本地（develop） | 预生产（stag） | 生产（prod） |
| --- | --- | --- |
| `localainovel.testhut.top` | `ainovel.testhut.top` | `ainovel.seekerhut.com` |

域名为约定入口；生产启用状态以实际发布配置和验收记录为准。本地（develop）域名可指向 LAN 服务。

- 前端内部监听端口: 11040
- 后端内部监听端口: 11041

- 部署入口: Linux 服务器发布只通过发版中心执行。当前平台按 catalog 构建并推送固定源码的镜像，控制面负责部署和健康检查；仓库保留的两阶段 `scripts/ci/build-release.sh` 契约与当前平台差异见 `scripts/ci/README.md`。运行时 `env.txt` 由 config-center 管理、经发版中心以 `0600` 普通文件只读挂载进后端容器，仓库、构建输入与发布产物都不携带运行时配置。本地开发在 Windows 直跑 `scripts/windows/` 入口，不使用 Docker。
- 本段描述仓库提供的技术入口，实际目标环境和操作范围按当前任务及适用运行规范确定，不因文档列出脚本而自动执行。

## Anti Slop 专题必读入口

涉及正文生成、Slop 检测、自动修订、风格和质量提示的分析、开发、审查或验收时，必须先阅读根目录的 **[AINovel Anti Slop 机制审计与改进依据](ANTI-SLOP-AUDIT.md)**。

该文档记录现有机制、已复现问题和改进依据，不替代 `doc/roadmap.md`；阶段状态、排期与前置门槛继续以路线图为唯一权威。

## 后续分期事项 / Pending Phases

后续开发的唯一权威入口是：

**`doc/roadmap.md`**

H1–H6 的当前完成状态、验收缺口与下一阶段入口仅在 `doc/roadmap.md` 维护；本文件不复制阶段完成清单，开始任务前必须读取路线图。

G2 真实盲测保留为运营验证，不阻塞 H 系列；在同一活动达到 100 张有效票、20 个成功样本对、10 名实际评审且精雕胜率不低于 55% 前，仍不得启动 G2 完整方案 A。G1-A P1、Manual 的既有依赖不解除；SCORE、单开发者质量基础的未完成验收与 G6 延迟状态继续保留。

任何专题提案、研究笔记或历史分支都不能替代 `doc/roadmap.md` 的状态与优先级。阶段完成、跳过或阻塞时，应在同一批次更新路线图。
