# 2026-06-17 Slop Gate Phase 3 测试记录

## 范围

- 新增长篇 drift 巡检：按稿件章节/字数窗口比较中后段断层、角色漂移、事件传送带和伏笔遗忘。
- 巡检结果保存到 `slop_drift_runs`，只输出趋势、证据、替代解释和 rewrite tasks，不修改正文，不判断作者是否使用 AI。
- 工作台质量分析页新增故事/大纲/稿件选择和长篇 drift 巡检结果展示。

## 已覆盖

- `SlopDriftServiceTest`：LLM 窗口对比 JSON 可持久化；短稿不足窗口时保存 `INSUFFICIENT_TEXT` 且不调用 AI。
- `SlopDriftControllerTest`：列表和触发接口会校验稿件归属并返回 DTO。
- `OpenApiAnnotationCoverageTest`：新增 drift Controller 仍满足 v2 OpenAPI 注解覆盖。
- `mock-api.test.ts`：前端 API mapper 可解析 drift 结果中的窗口摘要、指标曲线、断层点、证据、替代解释和 rewrite tasks。

## 验证命令

```bash
cd backend
mvn -q -Dtest=SlopDriftServiceTest,SlopDriftControllerTest,OpenApiAnnotationCoverageTest test

cd frontend
npm run test -- src/lib/__tests__/mock-api.test.ts
```

结果：新增测试通过。完整回归和本地部署验收以最终交付记录为准。
