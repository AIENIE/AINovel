# 2026-06-15 后台管理界面优化验证记录

## 范围

- 本次只优化 AINovel 本地后台管理界面体验，不新增 user-service、pay-service、ai-service 的全局后台能力。
- 覆盖页面：`/admin/dashboard`、`/admin/users`、`/admin/materials`、`/admin/assets`、`/admin/quality`、`/admin/credits`、`/admin/settings`。

## 变更点

- 后台布局支持桌面侧栏和窄屏抽屉导航。
- 管理页面统一标题、面板、加载、空状态、错误重试和搜索工具栏。
- 项目用户查询使用 `/api/v1/admin/users?search=`。
- 兑换订单与项目积分流水使用 `/api/v1/admin/credits/conversions?page=&size=` 和 `/api/v1/admin/credits/ledger?page=&size=`。
- 素材、资产、质量、兑换码列表增加前端搜索/筛选与分页展示。

## 验证结果

- `cd frontend && npm run test`：PASS，6 个测试文件、22 个测试通过。
- `cd frontend && npm run build`：PASS，Vite 生产构建成功；保留既有 chunk size 与 Browserslist 数据提示。
- `cd frontend && npx eslint <本次触及文件>`：PASS，本轮改动文件无 ESLint 错误。
- `cd frontend && npm run lint`：FAIL，仓库既有大量 `no-explicit-any`、hooks 依赖和 `tailwind.config.ts` require 风格问题仍存在；本轮 touched files 已单独验证通过。
- `sudo -E ./build.sh`：PASS，Docker Compose 完成前后端镜像构建并启动 `ainovel-frontend`、`ainovel-backend`。
- `curl --noproxy '*' -k -I https://ainovel.localhut.com/admin/login`：PASS，返回 `200`。
- `curl --noproxy '*' -k -I https://ainovel.localhut.com/admin/dashboard`：PASS，SPA 路由返回 `200`。
- `POST https://ainovel.localhut.com/api/v1/admin-auth/login`：PASS，管理员登录返回 token。
- Headless Chrome CDP smoke：PASS。
  - 1366x900 `/admin/dashboard`：包含 `业务运营概览` 和桌面 `Admin Panel`，横向溢出为 `0`。
  - 1366x900 `/admin/users`：包含 `项目用户` 和搜索框，横向溢出为 `0`。
  - 390x844 `/admin/dashboard`：包含移动端 `AINovel Admin` 顶栏和 `业务运营概览`，横向溢出为 `0`。

## 验证命令

```bash
cd frontend && npm run test
cd frontend && npx eslint src/components/layout/AdminLayout.tsx src/pages/Admin/Dashboard.tsx src/pages/Admin/UserManager.tsx src/pages/Admin/MaterialsGovernance.tsx src/pages/Admin/AssetsAudit.tsx src/pages/Admin/QualityInspection.tsx src/pages/Admin/CreditsManager.tsx src/pages/Admin/SystemSettings.tsx src/pages/Admin/components/AdminChrome.tsx src/pages/Admin/admin-list-utils.ts src/pages/Admin/__tests__/admin-list-utils.test.ts src/lib/__tests__/mock-api.test.ts
cd frontend && npm run build
sudo -E ./build.sh
```

## 验收关注点

- 未登录访问 `/admin/*` 仍跳转 `/admin/login`。
- 管理员登录后各后台页面可加载，并在接口失败时显示错误重试状态。
- 1366px 桌面宽度下侧栏和表格不遮挡内容。
- 390px 移动宽度下顶部栏、抽屉导航、列表卡片和按钮不溢出。
