# 2026-06-06 文档与代码收口验收

## 范围

- 管理看板：积分消耗统计与 API 错误率从固定值改为真实统计源。
- 素材库：查重候选和引用历史从空实现改为可用逻辑。
- 世界观：模块自动生成失败时记录失败状态和错误，不再写入占位内容。
- 设置页：提示词恢复默认、metadata 变量说明和帮助页接口驱动展示。
- 文档：同步 API、模块总览、前端页面文档、问题跟踪和 v2 当前状态。

## 自动化验证

| 命令 | 结果 |
| --- | --- |
| `cd backend && mvn -q -Dtest=MaterialServiceClosureTest,WorldPublishFlowTests test` | 通过 |
| `cd backend && mvn -q test` | 通过 |
| `cd backend && mvn -q -DskipTests package` | 通过 |
| `cd frontend && npm run test` | 通过，4 个测试文件 / 16 个用例 |
| `cd frontend && npm run build` | 通过；保留 Vite chunk size 与 Browserslist 数据提示 |
| `sudo timeout 600 ./build.sh` | 通过；前后端镜像重建并重启 |

## 部署与 smoke

- 首次 `build.sh` 在后端 Docker `mvn -q -e -DskipTests clean package` 阶段超过约 10 分钟无输出；已清理该构建进程。
- 随后补充 `backend/.dockerignore`、`frontend/.dockerignore`，并在 `backend/Dockerfile` 增加 Maven `dependency:go-offline` 缓存层后重跑通过。
- `docker ps`：`ainovel-backend`、`ainovel-frontend`、`ainovel-filebeat` 均处于 Up。
- `curl --noproxy '*' -k -I https://ainovel.localhut.com`：返回 `200 OK`。
- `curl --noproxy '*' -k -I 'https://ainovel.localhut.com/api/v1/sso/login?next=%2Fworkbench&state=smoke-test'`：返回 `302 Found`，Location 指向 `https://userservice.localhut.com/sso/login?...`。
- 未登录访问 `https://ainovel.localhut.com/api/v2/stories/00000000-0000-0000-0000-000000000000/plot-quality`：返回 `403 Forbidden`。
- Chromium headless 渲染首页成功，DOM 包含 `Novel Studio`、登录/注册和能力区块。
- 截图：`doc/test/artifacts/2026-06-06-home-smoke.png`。
