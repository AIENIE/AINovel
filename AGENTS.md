- 项目名: AINovel
- 项目归属：aienie
- 项目类型：业务项目（projects）
- 前端技术栈: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端技术栈: Java Spring boot
- 本地测试域名: ainovel.localhut.com
- 正式服域名: ainovel.aienie.com
- 前端对外端口: 11040
- 后端对外端口: 11041

在 linux 环境下，执行 sudo 的密码请从 `SUDO_PASSWORD` 环境变量获取。

- 部署脚本: 本项目仅保留 `build.sh`；脚本只执行 Docker Compose 构建与部署，存在 `env.txt` 时通过 Compose `--env-file` 做 Compose 插值，并挂载进后端容器由启动脚本加载运行时环境变量。

## 后续分期事项 / Pending Phases

后续开发的唯一权威入口是：

**`doc/roadmap.md`**

当前已完成 G2 Step 1 和 G1-A P0。下一项主线工作是收集 G2 真实盲测数据；在同一活动达到 100 张有效票、20 个成功样本对、10 名实际评审且精雕胜率不低于 55% 前，不得启动 G2 完整方案 A。

任何专题提案、研究笔记或历史分支都不能替代 `doc/roadmap.md` 的状态与优先级。阶段完成、跳过或阻塞时，应在同一批次更新路线图。
