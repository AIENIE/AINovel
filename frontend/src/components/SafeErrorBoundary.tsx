import { Component, type ReactNode } from "react";
import { reportClientError } from "@/lib/client-error-reporting";

type Props = {
  children: ReactNode;
};

type State = {
  failed: boolean;
};

export class SafeErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error): void {
    reportClientError(error, "react.error-boundary");
  }

  render(): ReactNode {
    if (this.state.failed) {
      return (
        <main className="flex min-h-screen items-center justify-center bg-zinc-950 px-6 text-zinc-100">
          <section className="max-w-md text-center" role="alert">
            <h1 className="text-2xl font-semibold">页面暂时无法显示</h1>
            <p className="mt-3 text-sm text-zinc-400">错误已捕获，请刷新后重试。</p>
            <button
              type="button"
              className="mt-6 rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500"
              onClick={() => window.location.reload()}
            >
              刷新页面
            </button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}
