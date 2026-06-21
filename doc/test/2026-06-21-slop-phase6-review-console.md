# 2026-06-21 Slop Gate Phase 6 审核台测试记录

## 范围

- 新增管理员 Slop 校准样本持久化、手工录入、从运行记录沉淀和审核状态更新。
- 后台质量巡检页新增样本审核区域，保留原有质量运行记录列表。
- 本期不改变用户侧诊断、生成门禁、长篇 drift 或阈值策略。

## 已执行

- `mvn -q -Dtest=SlopReviewSampleServiceTest,AdminOperationsControllerQualityReviewTest,SlopCalibrationCorpusTest,SlopQualitySignalsTest test`
- `mvn -q -Dtest=SlopReviewSampleSchemaInitializerTest test`
- `mvn -q test`
- `npm test -- src/pages/Admin/__tests__/quality-review-utils.test.ts src/pages/Admin/__tests__/admin-list-utils.test.ts`
- `npm test`
- `npm run build`
- `sudo ./build.sh`
- `curl -k -I https://ainovel.localhut.com/`
- `curl -k -i https://ainovel.localhut.com/api/v2/models`
- 管理员 API smoke：创建 Phase 6 样本、PATCH 审核为 `APPROVED`、按 `status=APPROVED` 列表筛选。
- Playwright/Chromium 浏览器验收：登录后访问 `/admin/quality`，确认“样本审核”“巡检记录”“创建样本”可见，创建 `P6-browser-*` 样本后列表回显，未发现质量接口 4xx/5xx 或缺表/缺列错误。

## 当前结果

- PASS：手工样本创建会计算本地观测证据等级、AI review 触发策略和匹配状态。
- PASS：从 Slop 运行记录沉淀样本保持同一 run 幂等，并会提取场景正文快照。
- PASS：后台质量页样本筛选、汇总计数和生产构建通过。
- PASS：后端完整测试、前端完整测试、前端生产构建和 Docker Compose 本地部署通过。
- PASS：`https://ainovel.localhut.com/` 首页返回 200，未登录访问 `/api/v2/models` 返回 403，符合受保护接口预期。
- PASS：部署库在 `ddl-auto=none` 下可自动补齐本期 `slop_review_samples` 表，并补齐质量后台依赖的既有 `plot_quality_runs` / `plot_quality_issues` 表和 Phase 5 `slop_quality_runs` 漂移列。
