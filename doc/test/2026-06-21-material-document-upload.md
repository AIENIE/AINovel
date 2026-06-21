# 2026-06-21 素材文档上传扩展验证

## 目标

- 将素材上传从 TXT-only 扩展为支持 TXT、Markdown、PDF、DOC、DOCX。
- 保持上传后进入待审素材队列和导入任务轮询流程不变。
- 增加文件扩展名与解析前大小保护，默认解析上限 10MiB。

## 验证记录

- `mvn -q -Dtest=MaterialFileParserTest test`：通过。
- `mvn -q -Dtest=MaterialFileParserTest,MaterialServiceClosureTest test`：通过。
- `mvn -q test`：通过。
- `npm test`：通过，7 个测试文件、28 个测试。
- `npm run build`：通过；保留既有 Browserslist 数据过期与 chunk size 警告。
- `sudo ./build.sh`：通过；后端 Dockerfile 移除会卡住的 `dependency:go-offline` 预热层后，前后端镜像构建并启动成功。
- `docker compose --env-file env.txt -f docker-compose.yml ps`：`ainovel-backend`、`ainovel-frontend` 均为 Up。
- `curl -k -I https://ainovel.localhut.com/`：HTTP 200。
- API 冒烟：使用 `env.txt` 中本地管理员账号登录，上传 `/tmp/ainovel-material-smoke.md`，任务 `5e4a9ee1-c377-4892-8107-9bd374f696e0` 从 `processing` 轮询至 `completed`，`progress=100`。
- 浏览器冒烟：Playwright + Chromium 访问 `https://ainovel.localhut.com/materials`，进入“批量导入”Tab，页面显示 `TXT / Markdown / PDF / DOC / DOCX`，控制台错误为空；截图 `/tmp/ainovel-material-document-upload.png`。

## 备注

- 直接访问前端容器端口 `http://127.0.0.1:11040` 不适合作受保护页面验收，因为生产 `serve` 容器不代理 `/api`；真实验证应走 `https://ainovel.localhut.com` 的 Nginx 入口。
