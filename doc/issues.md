# AINovel 当前问题跟踪（更新于 2026-03-04）

## 当前待处理

- 暂无阻塞项。

## 今日已验证并关闭

1. `build.sh` 本地部署链路
- 时间：2026-03-04 10:34-10:35（Asia/Shanghai）
- 结果：前端测试+构建、后端测试+打包、`docker compose` 重建、`nginx -t` 均通过。

2. 本地 qdrant 兼容性
- 时间：2026-03-04 10:38-10:39（Asia/Shanghai）
- 问题：`qdrant/qdrant:v1.13.4` 在 16K page size 环境持续崩溃（`jemalloc Unsupported system page size`）。
- 修复：`backend/deploy/deps-compose.yml` 切换为 `qdrant/qdrant:v1.8.3`。
- 结果：`ainovel-local-qdrant` 稳定运行。

3. 管理端用户列表接口
- 时间：2026-03-04 10:39-10:40（Asia/Shanghai）
- 验证：`GET /api/v1/admin/users` 返回 200。

4. 角色卡列表接口（故事归属校验）
- 时间：2026-03-04 10:39-10:40（Asia/Shanghai）
- 验证：`GET /api/v1/story-cards/{id}/character-cards` 返回 200。

5. 从一句话到 20 章链路
- 时间：2026-03-04 10:40-10:58（Asia/Shanghai）
- 结果：20 章/100 节大纲生成成功；正文按临时要求在第 15 节后停止（实际落库 16 节，全部 200 且字数门禁通过）。
- 产物：`/tmp/ainovel_e2e_20260304_104035`

## 说明

- 本轮完整记录见：`doc/test/2026-03-04-full-regression.md`。
- 历史阶段性问题（如旧版 SSO 回调异常、旧版容器端口错配）不再代表当前状态。
