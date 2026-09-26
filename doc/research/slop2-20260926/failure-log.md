# 故障与限制记录

1. E2-review（预算 attempt 16）客户端发生 JSONDecodeError，旧异常处理没有保存 HTTP 状态或原始错误体，故无法严谨断言是504或特定代理超时。后端账本最初仍 STARTED，紧随其后的 E3-minimal / E3-paragraph 命令在派发前被挡住，未写请求marker、未消耗预算。
2. 单次对账确认 attempt16 为 COMPLETED，credit reservation 为 COMPLETED，结算1项目积分。完整模型结果从 `ai_validation_calls.result_json` 恢复，保留 `status:null`、`elapsed:null` 和 recovery来源，未伪造客户端200响应/延迟。没有重试模型，没有重复收费。
3. 研究客户端随后增加非JSON响应/其他网络异常保存，并重新建立认证会话加载修复；提示、样本、模型和预算不变。认证不计模型调用。完整原失败HTTP体已无法恢复，明确作为可观测性缺口保留。
4. 初次离线打包对 protocol 哈希用错原始字节口径，遇到 Java 输出CRLF与Python文本通用换行差异。已改验证器按原freeze与protocol各自定义校验；冻结文件未变。
5. Windows首次读小说输出遇到终端编码显示问题，以 `python -X utf8` 重新读取全部原输出；文件完整，未触发模型重试。

上列故障与生成/编辑质量失败分开。质量失败不允许占故障备用调用去调提示。最终调用数、成本与未用额度见 `integrity.json`。
