# 第二批：依赖与验证门禁

对应 SEC-01、SEC-02、SEC-06、MAINT-06、MAINT-08。前端 Vite/Vitest 等解析版本、后端 Protobuf/gRPC 等组件已更新并固定；PDFBox 使用 3.0.8。`pnpm-lock.yaml` 与实际 JAR 的依赖均进入扫描输入。

首次扫描最终 JAR 检出 Tomcat 10.1.55 与普通 Netty 4.1.135 的 Critical/High，因而没有把首次扫描记为通过。兼容升级到 Tomcat 10.1.60、普通 Netty 4.1.138，并把 Jackson 2.21.7、Commons Lang 3.20.0、Log4j API 2.26.1 固定在 Spring Boot 3.5.16 管理的相同主版本内。复扫剩余 3 项均来自 gRPC 1.84.0 内置的 shaded Netty 4.2.16：2 项 Moderate 和 1 项具备有期限适用性例外的条件性 SNI 命中，阻断项为 0。PNPM 审计 0 项。

`scripts/ci/dependency-security.py` 在联网 resolve 阶段执行 PNPM 审计和打包 JAR 组件查询，包括 shaded 组件；报告绑定源码提交、锁文件、JAR 哈希与扫描时间。离线 build 会重算绑定并拒绝缺失、失败或不匹配的证据。Critical/High 默认阻断，例外文件必须有具体可复核记录。当前唯一的有期限例外为 [gRPC 客户端内 shaded Netty 的服务端 SNI 条件命中](security-exception-shaded-netty.md)；其他 Critical/High 均已升级消除。`scripts/ci/test_dependency_security.py` 与 CI 契约测试检查失败关闭路径。

迁移测试固定 target 时明确指定版本，最新测试由 Flyway 待迁移列表计算数量并继续核对历史数据。Windows 入口精确要求 Node 22.23.2、pnpm 11.22.0；包管理器和阶段入口说明以 `doc/roadmap.md` 为准。

最终报告与扫描库时间、组件清单、例外、JAR 哈希见同目录 `evidence/`。L2 与隔离 MySQL 结果见 [统一验收记录](validation.md)。
