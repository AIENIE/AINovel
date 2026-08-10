import { ArrowLeft, Coins, History, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/AuthContext";

const Pricing = () => {
  const { isAuthenticated } = useAuth();
  const actionHref = isAuthenticated ? "/profile" : "/login?next=%2Fprofile";

  return (
    <main className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-5">
          <Link to="/" className="font-semibold tracking-tight">AINovel</Link>
          <Link to="/" className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" />返回首页
          </Link>
        </div>
      </header>
      <section className="mx-auto max-w-5xl px-5 py-16 sm:py-24">
        <div className="max-w-2xl">
          <p className="mb-4 text-sm font-medium text-primary">积分说明</p>
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">按实际创作使用积分</h1>
          <p className="mt-5 text-lg leading-8 text-muted-foreground">
            AINovel 当前不提供月费订阅。AI 构思、生成、润色和分析按实际任务消耗项目积分，提交前会显示必要提示。
          </p>
        </div>
        <div className="mt-14 divide-y border-y">
          {[
            [Coins, "项目积分", "用于 AINovel 内的生成与分析任务，可通过兑换码或通用积分兑换获得。"],
            [Sparkles, "按任务消耗", "手写、保存和管理项目不扣除生成积分；调用 AI 能力时按实际任务记账。"],
            [History, "记录可查询", "个人中心展示余额、兑换历史和项目积分流水，方便核对每次变动。"],
          ].map(([Icon, title, description]) => (
            <div key={String(title)} className="grid gap-3 py-7 sm:grid-cols-[48px_180px_1fr] sm:items-center">
              <Icon className="h-5 w-5 text-primary" />
              <h2 className="font-medium">{String(title)}</h2>
              <p className="text-sm leading-6 text-muted-foreground">{String(description)}</p>
            </div>
          ))}
        </div>
        <div className="mt-10 flex flex-wrap items-center gap-4">
          <Button asChild size="lg"><Link to={actionHref}>{isAuthenticated ? "查看我的积分" : "登录并查看积分"}</Link></Button>
          <p className="text-sm text-muted-foreground">具体消耗以任务执行时的实际记账为准。</p>
        </div>
      </section>
    </main>
  );
};

export default Pricing;
