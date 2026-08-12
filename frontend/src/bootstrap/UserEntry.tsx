import type { Root } from "react-dom/client";
import { I18nextProvider } from "react-i18next";
import i18n from "@/i18n";
import App from "@/App";
import { SafeErrorBoundary } from "@/components/SafeErrorBoundary";
import { installGlobalErrorHandlers } from "@/lib/client-error-reporting";

installGlobalErrorHandlers();

/**
 * 用户侧 bootstrap：初始化 i18n（静态资源）后再渲染。
 * 仅用户入口导入 i18n/locale 模块，admin chunk 不包含翻译资源。
 */
export function mountUser(root: Root): void {
  root.render(
    <SafeErrorBoundary
      renderFallback={() => {
        const { t } = i18n;
        return (
          <main className="flex min-h-screen items-center justify-center bg-zinc-950 px-6 text-zinc-100">
            <section className="max-w-md text-center" role="alert">
              <h1 className="text-2xl font-semibold">{t("errorBoundary.title")}</h1>
              <p className="mt-3 text-sm text-zinc-400">{t("errorBoundary.desc")}</p>
              <button
                type="button"
                className="mt-6 rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500"
                onClick={() => window.location.reload()}
              >
                {t("errorBoundary.reload")}
              </button>
            </section>
          </main>
        );
      }}
    >
      <I18nextProvider i18n={i18n}>
        <App />
      </I18nextProvider>
    </SafeErrorBoundary>,
  );
}
