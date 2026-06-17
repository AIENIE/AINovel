# Docker env 加载规范化验证记录

日期：2026-06-15

## 变更内容

- `build.sh` 继续在存在 `env.txt` 时通过 Compose `--env-file` 做部署变量插值。
- `docker-compose.yml` 移除后端大段重复 `environment`，改为只读挂载 `env.txt`。
- 后端容器启动时通过 `docker/load-env-file.sh` 加载 `/app/env.txt`，再启动 Spring Boot。
- `USER_GRPC_ADDR` 测试服默认改为 `static://userservice.seekerhut.com:443`，与当前 Nginx gRPC TLS 入口一致。
- 补充后端容器内运行目录变量：`PORT`、`APP_LOG_DIR`、`APP_RECORD_DIR`、`LOGGING_FILE_NAME`。

## 已执行验证

```bash
bash -n build.sh
sh -n docker/load-env-file.sh
docker compose --env-file env.txt -f docker-compose.yml config >/tmp/ainovel-compose-config.yml
git diff --check
sudo -E bash build.sh
curl --noproxy '*' -k https://ainovel.localhut.com/actuator/health
curl --noproxy '*' -k https://ainovel.localhut.com/admin/ops
```

## 验收结果

- `docker compose config` 通过。
- `https://ainovel.localhut.com/actuator/health` 返回 200。
- `https://ainovel.localhut.com/admin/ops` 返回 200。
- 管理员登录返回 200。
- 管理员 token 调用 `/api/v1/admin/ops/summary` 返回 200。
- 管理员 token 调用 `/api/v1/admin/ops/diagnostics` 返回 200，`recordDir=/app/records`。
- 后端 Java 主进程环境包含：
  - `PORT=11041`
  - `APP_RECORD_DIR=/app/records`
  - `USER_GRPC_ADDR=static://userservice.seekerhut.com:443`
  - `EXTERNAL_GRPC_TLS_ENABLED=true`
  - `EXTERNAL_GRPC_PLAINTEXT_ENABLED=false`
