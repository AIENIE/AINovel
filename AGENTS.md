- 项目名: AINovel
- 项目类型：业务项目
- 前端技术栈: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端技术栈: Java Spring boot
- 本地测试域名: ainovel.localhut.com
- 正式服域名: ainovel.aienie.com
- 前端对外端口: 11040
- 后端对外端口: 11041

在linux环境下，执行sudo的密码请从SUDO_PASSWORD环境变量获取。
- 部署脚本: 本项目仅保留 build.sh；脚本只执行 Docker Compose 构建与部署，存在 env.txt 时通过 Compose --env-file 加载。
