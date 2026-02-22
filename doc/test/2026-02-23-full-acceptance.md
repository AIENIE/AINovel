# 2026-02-23 本地部署与全链路验收

- 时间：2026-02-23 03:11-03:20（Asia/Shanghai）
- 环境：`/home/pi/aienie-projects/AINovel`
- 验收目标：
  - `build.sh` 本地部署前后端
  - 测试域名与 HTTPS 可访问
  - 真实 SSO 登录
  - 通用积分兑换项目积分（100）
  - 兑换历史/积分流水展示
  - 新增 admin + 兑换码链路

## 1. 部署验证

- 执行命令：

```bash
sudo -E bash build.sh
```

- 结果：
  - 前端 `npm run test` 通过（11 tests passed）
  - 前端 `npm run build` 通过
  - 后端 `mvn -q test` 通过
  - 后端 `mvn -q -Dmaven.test.skip=true clean package` 通过
  - `docker compose down/up` 成功
  - `nginx -t` 成功

## 2. 域名与接口可用性

- `https://ainovel.seekerhut.com`：200
- `https://ainovel.seekerhut.com/api/v3/api-docs`：200

> 注意：本机命令行若启用代理，访问本地域名需要加 `--noproxy '*'`。

## 3. API 验收（真实 SSO + 积分链路）

- 使用 userservice 登录：
  - 账号：`goodboy95`
  - 密码：`superhs2cr1`
  - 通过 `Location` 片段提取 `access_token`
- 验证接口：
  - `GET /api/v1/user/profile`
  - `POST /api/v1/user/credits/convert`（`amount=100` + `idempotencyKey`）
  - `GET /api/v1/user/credits/conversions`
  - `GET /api/v1/user/credits/ledger`
  - `GET /api/v1/admin/dashboard`
  - `POST /api/v1/admin/redeem-codes`
  - `POST /api/v1/user/redeem`
  - `POST /api/v1/user/check-in`

- 关键结果（节选）：
  - `convert` 返回成功：
    - `orderNo=CVT-09E79F11C1D444E29E48`
    - `publicBefore=999897300 -> publicAfter=999897200`
    - `projectBefore=1451 -> projectAfter=1551`
  - `conversions` 顶部记录状态：`SUCCESS`
  - `ledger` 顶部记录类型：`CONVERT_IN`
  - `admin/redeem-codes` 创建成功；用户 `redeem` 返回 `REDEEM_SUCCESS`
  - `check-in` 当天已签返回 `TODAY_ALREADY_CHECKED_IN`（符合幂等预期）

## 4. Playwright 端到端验收

- 访问：`https://ainovel.seekerhut.com`
- 流程：
  - 首页点击“登录”
  - 跳转 userservice，输入 `goodboy95 / superhs2cr1`
  - 回跳至 `/workbench` 成功
  - 进入 `/profile`
  - 输入 `100` 执行通用积分兑换
  - 确认 toast 成功文案、余额变化、兑换历史新增、项目积分流水新增

- 结果：
  - SSO 登录成功
  - 100 通用积分兑换成功
  - 用户侧记录展示完整（兑换历史 + 项目流水）

- 证据截图：
  - `doc/test/artifacts/2026-02-23-profile-playwright-final.png`
  - `doc/test/artifacts/2026-02-23-profile-final.png`

## 5. 本轮修复确认

- 修复内容：旧数据库 `redeem_codes` 表结构兼容（legacy `amount` / `used`）
- 文件：`backend/src/main/java/com/ainovel/app/economy/model/RedeemCode.java`
- 验证：管理员创建兑换码 + 用户兑换链路成功通过。

## 6. 结论

- 本轮要求的部署、SSO 对接、通用积分兑换、项目积分账本、admin 兑换码链路均已通过验收。
