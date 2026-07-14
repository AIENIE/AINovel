# 当前验证说明

## 本地测试

后端：

```bash
mvn -q -f backend/pom.xml test
```

前端：

```bash
cd frontend
npm run test
npm run build
```

## 数据库迁移

应用启动由 Flyway 顺序执行 `V1` 至当前版本。旧库必须先有正确的 V1 baseline；不要手工复制表定义或向 `backend/sql/schema.sql` 追加 DDL。

`V4__g2_blind_evaluation.sql` 会创建 G2 盲测相关表和项目积分账本引用索引。升级后应确认 Flyway history 已记录版本 `4`。

## 部署与健康检查

```bash
printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh
curl --noproxy '*' -k https://ainovel.localhut.com/api/actuator/health
```

部署前确认 MySQL、Redis、Qdrant 和外部三服务配置可用。普通用户验证必须通过实际 SSO 会话，管理员验证必须通过 `/admin/login`；不得临时绕过身份验证。

## G2 盲测验收

1. 使用管理员账号创建活动并填入两个以上已绑定 SSO 的评审用户名，依次打开投稿和评审状态。
2. 用作者 SSO 会话在工作台提交一个自己的场景，确认稿件正文、版本和剧情质量记录未被写入评估文本。
3. 用不同的受邀 SSO 评审账号访问 `/g2-evaluations/{活动ID}/review`，确认只能看到匿名 A/B 文本；作者提交的样本不可由作者自己评审。
4. 投出 A、B 和中性票，确认中性票增加有效票数但不增加精雕胜场。
5. 人为观察失败样本时，核对 `project_credit_ledger` 中同一 `G2_EVALUATION` 引用的 `AI_DEBIT` 与一次 `AI_EVALUATION_REFUND` 相互抵销。
