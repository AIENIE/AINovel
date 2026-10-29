# Slop 2026-09-26 研究工具

本目录只用于可追溯研究，不由应用业务代码加载。证据入口：`doc/architecture/language-quality.md`（当前机制）；冻结输入仍位于对应研究夹具目录。

## 不花费模型额度的复核

在仓库根目录、Python 3 下运行：

```powershell
python scripts/research/verify-slop-study.py
```

仅标准库，验证冻结哈希、逐例指标、调用记录、全文覆盖和补丁范围。会按相同数据重写 `sample-results.md`，不改标签/样本。JSON 文件哈希采用 LF 归一化，正文哈希是 UTF-8 原文。

重新执行 Java 探针需安装本仓库 JDK 并通过 `scripts/windows/Build-Local.ps1` 生成 backend target/classes 和 Spring Boot jar。将 jar 的 `BOOT-INF/lib/*.jar` 解到忽略目录 `artifacts/slop-20260926/lib`（本轮已完成），随后：

```powershell
javac -encoding UTF-8 -cp 'backend/target/classes;artifacts/slop-20260926/lib/*' -d artifacts/slop-20260926/classes scripts/research/SlopStudy.java scripts/research/SlopProbes.java
java --class-path 'artifacts/slop-20260926/classes;backend/target/classes;artifacts/slop-20260926/lib/*' com.ainovel.app.quality.SlopStudy doc/research/slop-20260926/samples.json artifacts/slop-20260926/recheck-samples.json
java --class-path 'artifacts/slop-20260926/classes;backend/target/classes;artifacts/slop-20260926/lib/*' com.ainovel.app.quality.SlopProbes artifacts/slop-20260926/recheck-probes.json
```

与归档 JSON 作语义比较，不覆盖冻结结果。Java 适配器不联网、不启动 Spring、不连接 DB；探针反射私有方法，未来方法签名变化会显式失败，须人工更新研究适配器。

## 付费客户端边界

`prepare-slop-study.py` 只用于首次冻结，当前有文件时拒绝覆盖。`slop-experiment.py` 的 seed 会创建本轮独立预算，重复执行会失败；snapshot 为只读；call 每次最多调用一次，无批量或自动重试；已有 started marker 拒绝再次发送。当前实验已关闭，不继续运行 call/seed。

`slop-session.py` 用正式 SSO 交互登录，getpass 输入密码，令牌只留在进程内；依赖 PyMySQL 和应用环境。它与 `slop-experiment.py` 绑定本轮外部实验 env、runId、20 次持久化上限及本地服务，不能直接用于别的项目/环境。退出命令为 `{"action":"exit"}`。未知失败先核对账本和幂等键，不能删除 started 文件盲重试。

`archive-slop-study.py archive` 读取忽略目录中的账本和完整响应，只导出白名单字段，不保存余额、token、cookie。`corpus` 重读历史素材并核对已有阅读语料，不增加模型调用。未来实验必须新建 run 和预算，不能复用本轮余量或调换本轮样本。
