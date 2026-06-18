# 2026-06-18 v2 持久化补齐测试记录

## 范围

- 补齐 v2 上下文、分析、模型、工作台、版本控制和导出模块的数据库持久化路径。
- 保留既有控制器单元测试的内存 fallback，Spring 运行时优先使用持久化服务。
- 同步 `backend/sql/schema.sql` 与实体列名，新增知识图谱关系表。

## 已执行

- `mvn -q -Dtest=V2PersistenceServiceTest,V2ContextControllerTests,V2ModelControllerTest,V2WorkspaceControllerTests,V2AnalysisControllerTests,V2VersionControllerTests,V2ExportControllerTests test`
- `mvn -q test`
- `npm ci`
- `npm run test`
- `npm run build`
- `sudo ./build.sh`
- `curl --noproxy '*' -k -I https://ainovel.localhut.com/`
- `curl --noproxy '*' -k -I 'https://ainovel.localhut.com/api/v1/sso/login?next=%2Fworkbench&state=smoke-v2-persistence'`
- `curl --noproxy '*' -k -i https://ainovel.localhut.com/api/v2/models`

## 结果

- PASS：新增 `V2PersistenceServiceTest` 覆盖上下文、模型、工作台、分析、版本和导出元数据持久化。
- PASS：既有 v2 控制器测试继续通过，直接构造控制器时仍使用内存 fallback。
- PASS：后端完整测试通过。
- PASS：前端 25 个 Vitest 测试通过，生产构建成功。
- PASS：`build.sh` 完成 Docker Compose 构建与启动；`https://ainovel.localhut.com/` 返回 200。
- PASS：SSO 登录入口返回 302 到 `userservice.localhut.com`；未带 token 访问 `/api/v2/models` 返回 403，符合鉴权预期。

## 备注

- `npm ci` 报告依赖审计漏洞；本次未执行 `npm audit fix`，避免扩大依赖升级范围。
- Vite build 保留既有 chunk 体积和 Browserslist 数据过期提示。
