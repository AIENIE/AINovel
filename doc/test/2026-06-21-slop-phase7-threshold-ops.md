# 2026-06-21 Slop Gate Phase 7 阈值运营测试记录

## 范围

- 后台质量巡检页新增校准报表和 JSONL 样本导入。
- 管理员接口新增审核样本报表和批量导入。
- 本期不让阈值在线调整生效，不改变手动诊断、生成门禁或长篇 drift 行为。

## 已执行

- `mvn -q -Dtest=SlopReviewSamplePhase7ServiceTest test`
- `mvn -q -Dtest=SlopReviewSamplePhase7ServiceTest,AdminOperationsControllerQualityReviewTest test`
- `npm test -- src/pages/Admin/__tests__/quality-review-utils.test.ts`
- `npm test -- src/lib/__tests__/mock-api.test.ts`
- `npm test -- src/pages/Admin/__tests__/quality-review-utils.test.ts src/lib/__tests__/mock-api.test.ts`
- `mvn -q test`
- `npm test`
- `npm run build`
- `sudo ./build.sh`
- `curl -k -I https://ainovel.localhut.com/`
- `curl -k -i https://ainovel.localhut.com/api/v2/models`
- 管理员 API smoke：读取审核样本报表、导入 `P7-api-*` JSONL 样本、再次读取报表确认总数增加。
- Playwright/Chromium 浏览器验收：登录后访问 `/admin/quality`，确认“校准报表”“证据等级矩阵”“批量导入 JSONL”可见，导入 `P7-browser-*` 样本后在审核样本列表搜索回显。

## 当前结果

- PASS：报表会统计已审核样本、证据等级矩阵、AI review 混淆、当前 heuristic 策略快照和需复核样本。
- PASS：导入接口支持 JSONL，重复 `sampleId` 会跳过，有效样本会重新计算观测结果并保持待审核。
- PASS：前端工具函数能稳定生成矩阵行和 JSONL 导入文本。
- PASS：前端 API wrapper 会以管理员 token 调用报表和导入接口。
- PASS：完整后端测试、完整前端测试、前端生产构建和 Docker Compose 本地部署通过。
- PASS：`https://ainovel.localhut.com/` 首页返回 200，未登录访问 `/api/v2/models` 返回 403，符合受保护接口预期。
- PASS：本地浏览器最终验收截图已保存到 `/tmp/ainovel-phase7-quality-final.png`。
