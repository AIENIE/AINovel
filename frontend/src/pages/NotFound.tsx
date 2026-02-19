import { Link } from "react-router-dom";
import { buildSsoUrl, issueSsoState } from "@/lib/sso";

const NotFound = () => (
  <main className="min-h-screen flex flex-col items-center justify-center bg-background text-center gap-6 p-6">
    <div className="space-y-2">
      <p className="text-sm text-muted-foreground tracking-widest">404</p>
      <h1 className="text-3xl font-semibold">页面不存在</h1>
      <p className="text-muted-foreground max-w-md">
        抱歉，您访问的页面走丢了。请检查链接或返回首页继续创作。
      </p>
    </div>
    <div className="flex flex-wrap gap-3 justify-center">
      <Link
        to="/"
        className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-white hover:opacity-90 transition"
      >
        返回首页
      </Link>
      <button
        type="button"
        onClick={() => {
          const state = issueSsoState();
          window.location.href = buildSsoUrl("login", "/", state);
        }}
        className="inline-flex items-center rounded-md border border-border px-4 py-2 text-sm hover:bg-muted transition"
      >
        去登录
      </button>
    </div>
  </main>
);

export default NotFound;
