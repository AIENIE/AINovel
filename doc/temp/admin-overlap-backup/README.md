# AINovel 后台重叠功能移除备份

日期：2026-06-15

本目录记录本次后台增强中从 AINovel 移除或停止暴露的重叠能力。这里不是运行时代码，只用于后续审计、回滚评估或人工数据库清理参考。

## 已移除/停止暴露

- SMTP 配置与测试邮件服务：归属 user-service。
- 注册开关、邮箱、短信、SSO 配置：归属 user-service。
- 签到功能与签到区间配置：归属 pay-service 全局后台；AINovel 用户侧签到入口已移除。
- user-service 管理端用户查询/封禁/解封适配：AINovel 后台改为项目用户只读运营视角。
- AI 模型池、API Key、调用方密钥配置：归属 ai-service。
- pay-service 项目积分权威账本口径：AINovel 专属积分按 pdfToWord 模式改回本地账户/流水/兑换码，pay-service 只处理通用积分余额与扣减。

## 保留边界

- AINovel 本地保留项目专属积分账户、兑换码、领取记录、流水和通用转专属订单快照。
- 通用积分余额和通用积分扣减仍通过 pay-service。
- 现有数据库中的旧列不会由 Hibernate 自动删除；如需清理，先备份数据并人工执行本目录 SQL。
