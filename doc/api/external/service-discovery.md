# 外部微服务接口发现说明

- 版本：v1.1
- 最近验证时间：2026-02-16

## 目标
- 从注册中心发现 UserService / AiService / PayService 可用实例。
- 拉取 HTTP OpenAPI 与 gRPC 反射元数据，沉淀到文档。

## 发现流程
1. 读取注册中心入口配置：`CONSUL_HOST`、`CONSUL_PORT`、`CONSUL_SCHEME`。
2. 查询服务目录：`/v1/catalog/services`。
3. 查询健康实例：`/v1/health/service/<service-name>?passing=true`。
4. 对 HTTP 实例拉取：`http://<host>:<port>/v3/api-docs`。
5. 对 gRPC 实例拉取：
   - `grpcurl -plaintext <host>:<port> list`
   - `grpcurl -plaintext <host>:<port> describe <Service>`

## 注意事项
- 不在代码与文档中固化环境 IP/域名，统一使用环境变量和服务名。
- 若反射关闭或鉴权受限，记录阻塞项并标注已验证命令与返回状态。
- 每次版本迭代应记录获取时间与来源（注册中心 + 反射/OpenAPI）。
