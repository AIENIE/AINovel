# 2026-06-18 Slop Gate Phase 5 校准测试记录

## 范围

- 基于研究文档的人工标注规范和测试集设计，新增后端 JSONL 校准样本。
- 校准本地规则的 E1/E2 升级条件：单点弱信号保持 E1，密度/重复/多信号共振才升级。
- 覆盖传统网文俗套、角色合理分析腔、人工低水平和平台公式化样本的误伤边界。

## 已执行

- `mvn -q -Dtest=SlopCalibrationCorpusTest,SlopQualitySignalsTest test`
- `mvn -q -Dtest=LocalSlopHeuristicsTest,SlopCalibrationCorpusTest,SlopQualitySignalsTest,SlopQualityGateTest,SlopDiagnosticServiceTest test`
- `mvn -q test`
- `sudo ./build.sh`
- `curl --noproxy '*' -k -I https://ainovel.localhut.com/`
- `curl --noproxy '*' -k -i https://ainovel.localhut.com/api/v2/models`

## 结果

- PASS：新增 Phase 5 校准样本均符合预期证据等级和 AI review 触发策略。
- PASS：单个通用模板短句保持 E1 且不触发 AI review。
- PASS：模板密集样本升级 E2；元泄漏样本保持 E4。
- PASS：生成门禁和手动诊断的既有关键测试继续通过。
- PASS：后端完整测试通过。
- PASS：`build.sh` 完成 Docker Compose 构建与启动。
- PASS：`https://ainovel.localhut.com/` 返回 200；未登录访问 `/api/v2/models` 返回 403，符合鉴权预期。

## 备注

- 本期没有新增后台标注台、数据库表或公开接口。
- 后续如需要人审闭环，可在 Phase 6 基于当前 JSONL 样本格式扩展后台录入与审核能力。
