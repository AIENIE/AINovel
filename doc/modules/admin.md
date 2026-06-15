# 模块说明：后台管理系统

## 入口与权限

- 路由前缀：`/admin`
- 登录页：`/admin/login`
- 访问权限：`ROLE_ADMIN`
- 管理员认证：本地 `/api/v1/admin-auth/*`，普通用户仍走 SSO。

## 第一批后台能力

- `运营概览`：真实用户、积分消耗、待审素材、创作资产和高风险质量记录。
- `项目用户`：AINovel 本地用户镜像、项目专属积分、通用积分快照、故事/世界观数量；不提供 user-service 全局账号封禁、邮箱、短信或 SSO 管理。
- `素材治理`：待审素材、通过/驳回、重复候选和素材引用查询。
- `创作资产`：故事、世界观、稿件只读审计；后台不直接编辑或删除用户创作内容。
- `质量巡检`：聚合反套路质量与剧情质量运行记录，按风险分定位待处理场景。
- `专属积分`：AINovel 本地项目专属积分账户、兑换码、兑换订单快照和项目流水。
- `系统维护`：仅保留 AINovel 本地维护模式。

## 服务边界

- AINovel 本地负责：素材、创作资产、质量巡检、项目专属积分账户/兑换码/流水/通用转专属订单。
- `user-service` 负责：账号、注册、邮箱、短信、SSO、全局用户管理。
- `ai-service` 负责：模型池、API Key、调用方密钥、AI 成本和全局调用管理。
- `pay-service` 负责：通用积分余额、通用积分扣减、充值、签到配置和全局账务后台。

## 数据来源

- 用户域：`UserRepository` 本地用户镜像。
- 专属积分域：`ProjectCreditAccount`、`ProjectCreditLedger`、`RedeemCode`、`RedeemCodeUsage`、`CreditConversionOrder`。
- 通用积分域：`BillingGrpcClient.publicBalance` 与通用转专属扣减调用。
- 素材/资产/质量域：本项目 JPA 仓库与 `MaterialService`。
