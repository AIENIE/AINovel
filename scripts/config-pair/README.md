# 配置文件管理

AINovel 使用 `application-yaml-env-v1`。YAML 存放数据库地址、用户名、端点、ENV/AUTH_MODE、模型和目录；env 只放密码、哈希、令牌和密钥。秘密按字面值读取，校验普通文件、权限、重复键和必需值。test/production 沿用 TOTP 与各环境 TLS 边界。

Windows 标准入口读取仓库 `application-local.yml`。可通过 `-EnvFile` / `-EnvironmentFile` 指定仓库外私密文件；同目录 `<私密文件名>.application.yml` 作为外部覆盖。启动进程可以继承运行选项，不按变量名称拒绝或清洗环境；旧认证变量不再阻止启动，也不作为现行协议输入。

Linux 由现行 catalog 构建镜像，使用 `scripts/ci/ainovel-runtime-compose.yml`。配置中心管理 YAML/env，发布控制面只读挂载；本仓库不携带实际运行配置。`load-env-file.sh` 是容器唯一秘密加载器，Compose 将它挂到既有 `/app/bin/staging-load-env-file.sh` 路径。格式标记和 `mapping.json` 继续由控制面消费。

`read_configuration.py` 与 `config_pair.py` 由标准 Windows 入口 ConfigurationPair.ps1 调用，读取非秘密 YAML 参数。现行发版中心的 `config_pair_guard.sh` 还从产品包导入字面值 env 解析、YAML 重复键遍历和 `shell_configuration.configuration`；后者调用 `grpc_shared.validate_secret` 校验当前共享密钥格式。这些 S3 助手保留实际消费的实现，旧变量名称拦截、固定部署值限制及迁移、回滚、发布 CLI 已删除。

配置迁移工具由 `aienie-infra/control-plane/tools` 维护。产品配置契约测试使用 `python -m unittest discover -s scripts/config-pair/tests`，标准 Windows 测试入口同时执行这些测试。2026-11-01 已核对当前控制面 `1c869f0`／v65：通用部署引擎仍调用上述产品助手，也仍直接导入已删除的旧凭据名称检查。适配新引擎的配套补丁已准备，平台变更待明确授权。产品侧完整 Windows L2、构建及现有 staging 配置的只读校验通过。
