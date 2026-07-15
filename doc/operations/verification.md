# 验证与验收

## 标准验证链

```bash
mvn -q -f backend/pom.xml test
cd frontend && npm run test && npm run build
printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh
curl --noproxy '*' -k https://ainovel.localhut.com/api/actuator/health
```

普通用户验收必须通过真实 SSO 会话，管理员验收必须通过 `/admin/login`，不得临时绕过身份验证。

## 最近一次验证

2026-07-15，G1-A P0 交付与文档架构重构收口：

- 后端全量测试通过，Testcontainers 在 MySQL 8 上验证 V1 到 V5 迁移。
- 前端 23 个测试文件、79 项测试通过，生产构建通过。
- `build.sh` 部署成功；后端、前端容器运行正常。
- 运行库 Flyway schema 为 V5。
- 首页、`/novels/quick-create` 和 `/api/actuator/health` 均返回 200。
- SSO 授权码交换返回 200，但 `UserSessionValidator` 调用当前环境的 `userservice.localhut.com:10001` 超时，`GET /api/v1/user/profile` 返回 403。因此本轮未宣称登录后的 G1 浏览器闭环通过。

## G1 引导创作验收

1. 访问 `/novels/quick-create`，分别创建逐步选择和自动模式草稿。
2. 确认每步恰好 3 个候选、角色数和章节/场景数满足约束。
3. 关闭页面后重新进入，确认草稿和后台进度可恢复。
4. 确认故事、可选世界、角色和大纲写入标准实体。
5. 检查 `async_jobs` 状态、唯一幂等键、`RECOVERY_REQUIRED` 恢复和 `G1_WORKFLOW` 账本引用。
6. 在 1440x900 与 390x844 视口检查布局、候选编辑、错误和完成状态。

## G2 盲测验收

1. 管理员创建活动、邀请评审并推进活动状态。
2. 作者提交自己的场景，确认隔离文本不覆盖正文、版本或剧情质量记录。
3. 不同受邀账号只能看到匿名 A/B；作者不能评审自己的样本。
4. A、B 和中性票统计符合规则。
5. 失败样本的 `G2_EVALUATION` 扣费与退款在本地账本相互抵销。
