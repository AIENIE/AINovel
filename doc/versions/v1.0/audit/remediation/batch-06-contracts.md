# 第六批：正文转换与接口契约

对应 MAINT-05、MAINT-07。`RichTextProjector` 统一正文块、实体、换行和 Unicode 处理基础，按用途固定 `quality-plain-v1`、`context-plain-v2`、`export-plain-v1`、`narrative-evidence-v1` 策略。三个质量服务共同调用质量策略；新质量任务和叙事审批/抽取存储实际转换版本。叙事预览、抽取输入和结果使用同一证据版本，历史记录不重算、不改变 code point 位置单位。

关键合并、回滚、场景保存、AI 任务取消/重试和导出接口补充默认值、冲突与错误状态说明；`GeneratedOpenApiContractTest` 从 `/v3/api-docs` 的实际生成结果检查请求 schema 和响应状态。前端版本与导出客户端按领域拆分，同时保留唯一请求传输、认证和错误处理实现。

语料与生成 OpenAPI 专项以及 L2 结果见 [统一验收记录](validation.md)。
