# 有期限的 shaded Netty 适用性复核

扫描项：`GHSA-c4c3-7fpv-j4q5`，`pkg:maven/io.netty/netty-handler@4.2.16.Final`，位于最终 JAR 的 `BOOT-INF/lib/grpc-netty-shaded-1.84.0.jar`。原项目以 Spring Boot 3.5.16 管理的 Tomcat 10.1.55、Lettuce 引入的 Netty 4.1.135 及 gRPC shaded Netty 4.2.16 为扫描命中。前两者已升级到 Tomcat 10.1.60、Netty 4.1.138；gRPC 1.84.0 仍打包 Netty 4.2.16，不能通过修改普通 Netty BOM 替换其 shaded 字节码。

[Netty 官方公告](https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5)限定了升级为实际 mTLS 绕过所需的条件：服务端以 `SslClientHelloHandler` 作 SNI 路由，仅在每个 SNI `SslContext` 强制客户端证书，而默认上下文宽松，且应用没有第二层证书校验。当前 AINovel 仅声明 `grpc-client-spring-boot-starter` 和 `grpc-netty-shaded` 客户端，源码没有 `NettyServerBuilder`、`@GrpcService`、`SniHandler` 或服务端 per-SNI mTLS 配置；`GeneratedOpenApiContractTest.applicationDoesNotHostAGrpcNettyServer` 还在完整 Spring 上下文断言无 `io.grpc.Server` bean。外部 HTTP 入口为 Tomcat。由此判定这一个**特定条件性命中**对当前应用路径不适用，而非认为旧 Netty 字节码已修复。

例外仅匹配上述 GHSA 与上述确切 PURL，保留原始扫描发现，`scripts/ci/security-exceptions.json` 的到期日为 **2026-10-15**；到期后扫描失败关闭。若加入 gRPC 服务端、Netty SNI 路由或 gRPC 发布含修订版 Netty 的新版本，应立即取消例外并升级/复测。其他 Critical/High 不在例外内。最终扫描证据记录产物 SHA-256、组件来源、OSV 原始响应和政策判定。
