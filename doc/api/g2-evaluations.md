# G2 盲测 API

所有接口均要求 Bearer 令牌。`/v1/admin/g2-evaluations/*` 使用管理员令牌；其余接口使用普通 SSO 用户会话。

## 管理员

- `GET /v1/admin/g2-evaluations`：查询活动、样本和盲测门槛进度。
- `POST /v1/admin/g2-evaluations`：创建草稿活动。

```json
{"title":"G2 第一轮","reviewerUsernames":["reviewer_a","reviewer_b"]}
```

- `POST /v1/admin/g2-evaluations/{id}/status`：仅允许 `DRAFT -> COLLECTING -> REVIEWING -> CLOSED`。

```json
{"status":"COLLECTING"}
```

创建时会验证每个受邀用户已存在且绑定 SSO。进入 `REVIEWING` 至少要有一个成功的样本对。

## 作者投稿

- `GET /v1/g2-evaluations/open`：列出可投稿活动。
- `POST /v1/g2-evaluations/{id}/samples`：提交自己的稿件场景。

```json
{"manuscriptId":"uuid","sceneId":"uuid"}
```

服务器异步生成快速和精雕文本。它们只保存到盲测样本，不覆盖稿件；任一文本对失败时，系统按样本引用退回所有关联的本地项目积分扣费。

## 评审

- `GET /v1/g2-evaluations/{id}/review/next`：返回下一条匿名 A/B 样本；未受邀、未进入评审阶段或没有剩余样本时拒绝或返回空结果。
- `POST /v1/g2-evaluations/{id}/review/votes`：提交一次选择。

```json
{"sampleId":"uuid","choice":"LEFT"}
```

`choice` 可为 `LEFT`、`RIGHT` 或 `NEUTRAL`。服务端根据每位评审的稳定匿名顺序映射为快速或精雕；作者不可评自己的样本，单个样本只能投一次。中性票进入有效票分母，但不计入精雕胜场。
