# 资料检索选型工具

`create-material-evidence-fixtures.py` 冻结本轮构造数据；已有文件不可覆盖。共 100 条资料、200 条查询，按来源分为 120 开发与 80 留出；它们不代表自然小说语料或作者评分。

`collect-material-selection.py` 显式运行隔离 MySQL 专项，调用生产实体/全文检索与 RRF，不解码任何答案标签。默认 `basic` 不构造 AI 客户端；`--live --user-id <已授权网关用户 ID>` 才启用 develop TLS 网关，预算必须由既有配置中心预先登记。脚本不创建、不重置预算，不写业务数据库或复制密钥。只接受已授权的 `aienie_novel_audit_test_*` 隔离库，保留历史数据。

```powershell
python scripts/verification/collect-material-selection.py `
  --environment-file ${AIENIE_RUNTIME_ROOT}/private/app-secrets/ainovel.env `
  --schema aienie_novel_audit_test_creator_20261002 `
  --run retrieval-evidence-20261003-v1 --split development `
  --output artifacts/retrieval-evidence-20261003/mysql-basic-development.json
```

真实五组共享每个查询的标准/Flash 向量，各执行一次重排，最多 800 个查询 RPC，再加分模型的资料索引批次。所有索引和请求编号绑定运行、资料哈希、模型、1024 维、切分与输入模板；两种模型不混用集合。实际外发仍由网关持久账本同时限制 100 元和 1000 次，次数预估不能代替账本。

采集器保留原数据库 UUID 的排序语义，只在结果输出时映射为固定资料片段 ID。每一步落检查点，重启复用原请求；遇到供应商结果未知立即停止，保留失败标记待对账，不改键继续另一组。向量检查点与五组结果位于本轮 artifacts，基础检索及异常路径先用模拟供应商和隔离库验证。

`score-material-selection.py` 先核对固定配置和全部文件哈希，再读取指定集合的答案评分。未执行的组显示 `missingQueries`，质量、失败率与延迟为 `null`，不能把未执行记为供应商失败。只有完整留出结果和网关权威用量台账才能用于选型；人工灵感评分单独保留。

```powershell
python scripts/verification/score-material-selection.py `
  artifacts/retrieval-evidence-20261003/mysql-basic-holdout.json `
  --split holdout --output artifacts/retrieval-evidence-20261003/mysql-basic-holdout-score-final.json
python -m unittest discover -s scripts/verification -p test_material_selection_scorer.py
```
