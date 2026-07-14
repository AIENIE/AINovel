- 项目名: AINovel
- 项目归属：aienie
- 项目类型：业务项目（projects）
- 前端技术栈: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端技术栈: Java Spring boot
- 本地测试域名: ainovel.localhut.com
- 正式服域名: ainovel.aienie.com
- 前端对外端口: 11040
- 后端对外端口: 11041

在linux环境下，执行sudo的密码请从SUDO_PASSWORD环境变量获取。
- 部署脚本: 本项目仅保留 build.sh；脚本只执行 Docker Compose 构建与部署，存在 env.txt 时通过 Compose --env-file 做 Compose 插值，并挂载进后端容器由启动脚本加载运行时环境变量。

## 后续分期事项 / Pending Phases

已确认但暂缓实施的功能计划统一维护在：

**`design-doc/v3-proposals/PENDING-PHASES.md`**

当前正在实施：G2 Step 1（带约束起草 — 负面模式注入 + 人味要素注入，单候选，无节拍规划）。

已明确排队的后续计划（详见上述文件）：
- G2 完整方案 A：节拍规划 + 多候选竞技场（触发条件：Step 1 盲测 ≥55%）
- AI 网关采样参数透传（min-p，与完整方案 A 同期排入）
- Manual 模式（用户介入节拍/候选选择，前置：完整方案 A 上线）
- 题材分库（统一库积累数据后拆分）
- 长篇故事上下文检索增强（SCORE 方向，独立排期）
- G2 方案 B/C（长期，前置条件见文件）
