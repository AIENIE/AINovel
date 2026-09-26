# 本轮验证记录

2026-09-26，本轮研究工具与文档的实际验证；不把历史业务测试写成本轮重跑。

- Aienie系统矩阵校验通过（37组件）；本地标准Windows入口运行，无Docker/临时应用服务器。
- Java研究提示适配器编译成功，经生产PromptAssemblyService生成18份冻结请求；旧SlopStudy调现有规则完成72片段离线测量。Python研究脚本语法编译通过。
- `slop2-package.py` 最终通过：72条、48/24、类别/正反例平衡、同源分组不泄漏、真实摘录属于原始场景、冻结源文件与请求哈希不变、实际派发请求等于冻结请求、模型策略deepseek-flash、代理先读早于模型复核、全部候选源哈希/UTF-16范围/不重叠/重建结果匹配。
- 28个模型尝试与持久预算及积分预留记录逐一对账，最终used28/limit40、结算28积分；12候选/18补丁、2空补丁。一次客户端响应异常从完成账本恢复，不额外调用。没有正文版本或业务报告落库写入。
- 本地真实SSO→模型目录→认证chat→预算/计费→离线补丁/复核完成，研究用外部env运行结束后通过标准Stop/Start Backend恢复默认 `ainovel.env`；前端11040、后端11041状态均healthy。认证材料只在会话内存，客户端会话已退出。
- 文档JSON均可解析；归档排除账号余额、令牌、密码、私钥；`git diff --check`通过。git会将少量CRLF规范为LF；输入文件的原始字节哈希与protocol文本换行归一哈希分别按其定义验证，未改冻结内容。

本轮没有修改业务功能，因此未重新执行前后端全量业务L2套件，也未把研究API对照宣称为编辑器产品L4。后续产品L2/本地L4的待执行项列于 `implementation-revision.md`。没有真人盲评、没有跨模型训练或部署验证。

保留其他任务工作区改动：`doc/operations/verification.md`、`env.example`、`scripts/windows/Invoke-Local.ps1`、`scripts/windows/tests/Test-LocalContract.ps1`、`scripts/windows/local-trust/`；本次研究提交不包含这些内容。
