# 2026-03-04 本地部署与全链路回归

## 1. 环境与部署

- 时间：2026-03-04（Asia/Shanghai）
- 部署命令：`sudo -E bash build.sh`
- 访问域名：`https://ainovel.seekerhut.com`
- 前后端端口：`11040 / 11041`

部署结果：

- 前端测试通过（`11 passed`），构建成功。
- 后端测试通过，打包成功并重启容器。
- Nginx `/api` 反向代理超时为 `300s`（已生效）。

## 2. 关键问题与修复

1. 本地 qdrant 容器反复重启
- 现象：`<jemalloc>: Unsupported system page size`
- 原因：当前机器 page size 为 `16384`（16K），`qdrant/qdrant:v1.13.4` 不兼容。
- 处理：`backend/deploy/deps-compose.yml` 将 qdrant 镜像切换为 `qdrant/qdrant:v1.8.3`。
- 结果：`ainovel-local-qdrant` 稳定 `Up`。

2. 管理员用户列表链路
- 验证接口：`GET /api/v1/admin/users`
- 结果：`200`（恢复正常）。

3. 角色列表懒加载链路
- 验证接口：`GET /api/v1/story-cards/{id}/character-cards`
- 结果：`200`（恢复正常）。

## 3. API 回归

烟测结果：

- `POST /api/v1/admin-auth/login` -> `200`
- `GET /api/v1/admin/users` -> `200`
- `GET /api/v1/story-cards/b15f6de9-8509-4713-b392-c88a45ea6f6e/character-cards` -> `200`
- `POST /api/v1/conception` -> `200`

## 4. 小说生成链路回归

### 4.1 完整 20 章/100 节基线（已完成）

- 产物目录：`/tmp/ainovel_e2e_20260304_084734`
- 结果：
  - `chapterCount=20`
  - `sceneCount=100`
  - `finalSectionCount=100`
  - `failedScenes=0`

### 4.2 临时变更后复测（按用户要求在第 15 节停止）

- 产物目录：`/tmp/ainovel_e2e_20260304_104035`
- 对应 ID：
  - `storyId=fcc6b6db-246c-4684-be3e-739c32e26957`
  - `outlineId=7357e32b-f206-4a4b-bbf9-3701b16d234b`
  - `manuscriptId=39725686-ac26-4c00-a134-46e83f090d55`
- 大纲结果：20 章，100 节。
- 正文结果：
  - 按临时指令在第 15 节后停止；第 16 节请求已在途并成功落库。
  - 实际生成 `16` 节，全部 `HTTP 200`。
  - 汉字数区间：`2869-3200`（满足 `2800-3200` 门禁）。
- 明细：`/tmp/ainovel_e2e_20260304_104035/scene_report.tsv`

## 5. Playwright 回归说明

- 管理后台登录与主页面导航可用。
- 普通用户（`goodboy95 / superhs2cr1`）登录与工作台主要页面可达。
- 本轮文档提交前未新增阻塞级 UI 缺陷。
