# 2026-06-21 素材列表筛选排序验证

## 目标

- 在素材列表页增加搜索、状态、类型、标签关键词筛选。
- 支持按创建时间或标题排序，并支持升序/降序切换。
- 关闭 `frontend/doc/page-materials.md` 中“列表筛选/排序（按状态、类型、标签、时间）”待完善项。

## 验证记录

- `npm test -- material-list-utils.test.ts`：通过，1 个测试文件、3 个测试。
- `npm test`：通过，8 个测试文件、31 个测试。
- `npm run build`：通过；保留既有 Browserslist 数据过期与 chunk size 警告。
- `mvn -q test`：通过；保留既有 Spring/Hibernate 测试日志。
- `sudo ./build.sh`：通过，前后端镜像构建并启动成功。
- `docker compose --env-file env.txt -f docker-compose.yml ps`：`ainovel-backend`、`ainovel-frontend` 均为 Up。
- `curl -k -I https://ainovel.localhut.com/`：HTTP 200。
- 浏览器冒烟：Playwright + Chromium 使用 `env.txt` 本地管理员账号登录并访问 `https://ainovel.localhut.com/materials`；桌面与 390px 窄屏均确认“全部状态”“全部类型”“标签关键词”“创建时间”“降序”可见，控制台错误和请求失败均为空。
- 截图：`/tmp/ainovel-material-list-filter-sort-final.png`、`/tmp/ainovel-material-list-filter-sort-mobile-final.png`。

## 备注

- 当前 `Material` 前端类型仅包含 `createdAt`，本轮按创建时间排序，不新增后端更新时间字段。
