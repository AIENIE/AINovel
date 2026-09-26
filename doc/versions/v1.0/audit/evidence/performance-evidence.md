# 性能与稳定性审计：离线证据

2026-09-26，HEAD `2a1fb98a9e85d9de9feee6e2d820ce7961797a82`，Windows PowerShell，`Platform=Win32NT`。

## PERF-01 真实 hook 复现

- 测试：`draft-isolation.repro.test.tsx`。
- 配置：`vitest.audit.config.mjs`，限定仅运行该复现，不加入常规测试匹配范围。
- 结果：`draft-isolation-result.json`，1 test / 1 suite passed，0 failed。
- 最终证据运行使用 Node **22.23.2**、Vitest **4.0.16**。API、写作会话均 mock，未访问外部服务或真实正文。
- 测试断言当前缺陷：A 的草稿在选中 B 后仍显示，并用 B 的有效版本提交保存。修复后应更改此断言为隔离行为；当前 passed 表示缺陷已被复现。

从仓库根目录执行：

```powershell
& 'C:/Users/duwei/AppData/Local/Aienie/tools/node-v22.23.2-win-x64/node.exe' frontend/node_modules/vitest/vitest.mjs run --config doc/versions/v1.0/audit/evidence/vitest.audit.config.mjs --reporter=verbose --reporter=json --outputFile.json=../doc/versions/v1.0/audit/evidence/draft-isolation-result.json
```

最终输出摘录：

```text
✓ draft-isolation.repro.test.tsx > reproduces a cached scene draft being written into a different manuscript
Test Files  1 passed (1)
Tests       1 passed (1)
Start at    15:21:49
Duration    5.12s
```

以上时长仅为测试工具输出，不能作为应用性能基准。

## PERF-05 调度器默认值

仓库 `backend/pom.xml:12` 声明 Spring Boot 3.5.14。只读打开本机 Maven 缓存 `spring-boot-autoconfigure-3.5.14.jar`，读取 `META-INF/spring-configuration-metadata.json`：

```json
{
  "name": "spring.task.scheduling.pool.size",
  "type": "java.lang.Integer",
  "description": "Maximum allowed number of threads. Doesn't have an effect if virtual threads are enabled.",
  "sourceType": "org.springframework.boot.autoconfigure.task.TaskSchedulingProperties$Pool",
  "defaultValue": 1
}
```

源码/资源检索没有发现自定义 TaskScheduler、SchedulingConfigurer、调度池大小或虚拟线程启用配置。未读取运行时秘密配置，实际部署可能覆盖默认值。报告据此限定为仓库默认配置下的调度串行阻塞风险。
