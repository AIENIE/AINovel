# AINovel

AINovel 是一个前后端分离的 AI 小说创作业务项目。

- 前端：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端：Java 25 + Spring Boot 3 + JPA + Flyway + Redis + gRPC 客户端
- 本地域名：`ainovel.localhut.com`
- 正式域名：`ainovel.aienie.com`
- 前端端口：`11040`
- 后端端口：`11041`

## 当前能力

- 统一登录：通过 `/api/v1/sso/*` 中转 user-service SSO，不提供本地普通用户登录接口。
- G1 引导创作：从一句话开始，在故事、世界、角色和大纲四步生成三候选，支持编辑、跳过、自动后台推进和草稿恢复。
- 故事与工作台：故事卡、角色卡、章节场景、大纲、富文本正文与多稿件管理。
- 世界观：模块定义、字段精修、发布预检、缺失字段生成与失败重试。
- 素材：创建、TXT 导入、混合检索、审核、查重、人工合并和稿件引用查询。
- 文本质量：生成门禁、手动诊断、长篇 drift、剧情质量、候选修订与精雕模式。
- G2 盲测：匿名快速/精雕对照样本、邀请评审、作者隔离和失败退款。
- v2 能力：Lorebook、图谱、风格、角色声音、版本分支、导出、模型偏好和工作台设置。
- 项目积分：本地项目专属积分、通用转专属、兑换码、流水和 AI 用量扣费。
- 管理后台：运营、用户、素材、资产、质量、G2、积分、运维观测和维护模式。

## 快速部署

准备根目录 `env.txt`，然后执行：

```bash
printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh
```

打开 `https://ainovel.localhut.com`。

`build.sh` 只负责 Docker Compose 构建和部署。MySQL、Redis、Qdrant、三服务、域名、证书和反向代理必须由外部环境提前提供。

## 验证

```bash
mvn -q -f backend/pom.xml test
cd frontend && npm ci --legacy-peer-deps && npm run test && npm run build
```

## 目录

- `frontend/`：React 前端。
- `backend/`：Spring Boot 后端、proto 与 Flyway 迁移。
- `doc/`：架构、API、运维、路线图、提案和研究文档。
- `user-doc/`：创作者与管理员使用手册。
- `build.sh`：唯一部署入口。
- `docker-compose.yml`：前后端容器编排。

研发文档见 [`doc/README.md`](doc/README.md)，用户手册见 [`user-doc/README.md`](user-doc/README.md)，后续工作见 [`doc/roadmap.md`](doc/roadmap.md)。
