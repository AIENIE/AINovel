import type { Root } from "react-dom/client";
import AdminApp from "@/AdminApp";
import { SafeErrorBoundary } from "@/components/SafeErrorBoundary";
import { installGlobalErrorHandlers } from "@/lib/client-error-reporting";

installGlobalErrorHandlers();

/**
 * Admin 侧 bootstrap：不导入任何 i18n / locale 模块，界面固定简体中文。
 * 从用户英语页直接进入 admin 时，强制把 html lang 复位为 zh-CN。
 */
export function mountAdmin(root: Root): void {
  document.documentElement.lang = "zh-CN";
  root.render(
    <SafeErrorBoundary>
      <AdminApp />
    </SafeErrorBoundary>,
  );
}
