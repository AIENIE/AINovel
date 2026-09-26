# 本地 DNS 与登录恢复（2026-09-26）

用户明确授权在 192.168.1.3 的 DNS 登记正确解析，随后授权排查本地账密登录失败。本次只恢复 Windows 本地实验入口，没有发布 Linux 预发/生产。

## 观察与处理

1. `localainovel.testhut.top` 缺解析，本机 Windows LAN 地址为 `192.168.1.4`，本地 Nginx 443 已监听。通过现有 config-center DNS API 读取完整配置、携带 baseRevision 更新，仅新增 `A localainovel.testhut.top -> 192.168.1.4`、TTL 300；原有 19 条保留。主 DNS `.3` 与备 DNS `.6` 应用后均 inSync，版本 `cf156952307fe3ab4b550caa1ab9bdf1154784bd025216dbcbfb467ff4a33493`。两台定向解析与普通 HTTPS 请求通过。原始前后结果留在被忽略的 `artifacts/slop-20260926/dns-{before,after}.json`。
2. 首次 SSO 账密认证后，应用的 `/api/v1/sso/session` 交换返回 503；这不是凭据错误的证据。Java 独立 HTTPS 探针访问 user-service 健康接口，默认 JSSE 信任库报 `SSLHandshakeException: PKIX path building failed`；添加 `-Djavax.net.ssl.trustStoreType=Windows-ROOT` 后 HTTP 200。
3. 本次修改 `scripts/windows/Invoke-Local.ps1` 的 Backend JVM 启动参数，使用 Windows 已信任根证书，并与已有 JDWP 参数合并。未禁用 TLS 或主机名验证，未导入私钥，未更改全局 JDK truststore。
4. 工作区另一并行任务同时迁移了 local gRPC 到 `.6` 的 22001/22011/22021 并配置显式 gRPC 根证书。该变更与 JSSE HTTPS 是两个独立通道；本次保留这些工作，但不把其端口/证书改动归为本次登录修复提交。

## 本次实际验证

- Windows 标准入口 `Test-LocalContract.ps1`、`Test-RootEntry.ps1` 通过（后者包含 6 个 endpoint mock 用例）。这是本次重跑；没有重跑前后端全量业务测试。
- 使用标准 Stop/Start 入口重启后端，前后端健康通过。
- 应用内浏览器真实 SSO 登录进入工作台；同账号经正式 SSO 流程取得会话，模型列表 HTTP 200，返回 `deepseek-flash`；之后首个真实实验请求成功。凭据与会话令牌没有写入仓库/实验文件。
- Chrome 扩展浏览器曾报连接中止；本次成功证据来自应用内浏览器与 HTTPS API，不宣称所有浏览器通道已恢复。
- 实验使用独立外部 env 文件，将 runId 固定为 `slop-20260926-v1`、最大调用 20、local-quality-only=true，未改正常运行时 env，未沿用 H2 剩余额度。实验结束恢复情况见研究报告。

风险与范围：Windows-ROOT 依赖本机可信根配置；证书轮换仍应按公共 PKI 流程维护。此修复证明本次 PKIX 阻塞已解除，不证明历史所有 `SESSION_INVALID` 已根治。DNS 若 Windows 地址再次变化，须更新该 A 记录。
