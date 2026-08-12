import { createRoot } from "react-dom/client";
import "./globals.css";

/**
 * 入口：根据精确 /admin 路径隔离加载 admin 与 user bootstrap。
 * - /admin* → AdminEntry（不含 i18n / locale 模块，固定简中）
 * - 其余    → UserEntry（初始化 i18n 静态资源）
 */
const isAdminPath = window.location.pathname === "/admin" || window.location.pathname.startsWith("/admin/");

async function bootstrap(): Promise<void> {
  const container = document.getElementById("root");
  if (!container) return;
  const root = createRoot(container);

  if (isAdminPath) {
    const { mountAdmin } = await import("@/bootstrap/AdminEntry");
    mountAdmin(root);
    return;
  }

  const { mountUser } = await import("@/bootstrap/UserEntry");
  mountUser(root);
}

void bootstrap();
