import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { AdminDashboardStats } from "@/types";
import { Users, Zap, AlertTriangle, FileClock, BookOpen, Globe2, ScrollText, ShieldAlert } from "lucide-react";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel, adminPanelClass } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

type AssetSummary = {
  stories?: number;
  worlds?: number;
  manuscripts?: number;
  highRiskQualityRuns?: number;
};

const Dashboard = () => {
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [assets, setAssets] = useState<AssetSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      const [dashboard, summary] = await Promise.all([api.admin.getDashboardStats(), api.admin.getAssetSummary()]);
      setStats(dashboard);
      setAssets(summary);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "运营概览加载失败"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const cards = stats && assets ? [
    { label: "总用户数", value: stats.totalUsers, note: `今日新增 +${stats.todayNewUsers}`, icon: Users },
    { label: "今日积分消耗", value: stats.todayCreditsConsumed.toLocaleString(), note: `总消耗 ${stats.totalCreditsConsumed.toLocaleString()}`, icon: Zap },
    { label: "API 错误率", value: `${(stats.apiErrorRate * 100).toFixed(1)}%`, note: stats.apiErrorRate < 0.05 ? "状态良好" : "需要关注", icon: AlertTriangle },
    { label: "待审素材", value: stats.pendingReviews, note: "进入素材治理处理", icon: FileClock },
    { label: "故事", value: assets.stories ?? 0, note: "本地创作资产", icon: BookOpen },
    { label: "世界观", value: assets.worlds ?? 0, note: "本地创作资产", icon: Globe2 },
    { label: "稿件", value: assets.manuscripts ?? 0, note: "本地创作资产", icon: ScrollText },
    { label: "高风险质量记录", value: assets.highRiskQualityRuns ?? 0, note: "风险分 >= 70", icon: ShieldAlert },
  ] : [];

  return (
    <div className="space-y-6">
      <AdminPageHeader title="业务运营概览" description="AINovel 本地业务对象与项目专属积分运营状态。" />

      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
      {isLoading ? <AdminLoadingState rows={4} /> : null}
      {!isLoading && !error && cards.length === 0 ? <AdminEmptyState title="暂无运营数据" /> : null}
      {!isLoading && !error && cards.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          {cards.map((item) => (
            <div key={item.label} className={`${adminPanelClass} rounded-lg border p-4`}>
              <div className="flex items-center justify-between gap-3">
                <div className="text-sm font-medium text-zinc-400">{item.label}</div>
                <item.icon className="h-4 w-4 shrink-0 text-zinc-500" />
              </div>
              <div className="mt-3 text-2xl font-semibold text-zinc-50">{item.value}</div>
              <p className="mt-1 text-xs text-zinc-500">{item.note}</p>
            </div>
          ))}
        </div>
      ) : null}

      <AdminPanel title="后台职责边界">
        <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-3">
          <div className="rounded-md border border-zinc-800 bg-zinc-950/40 p-3">
            <div className="font-medium">AINovel</div>
            <div className="mt-1 text-zinc-500">素材、创作资产、质量巡检、项目专属积分</div>
          </div>
          <div className="rounded-md border border-zinc-800 bg-zinc-950/40 p-3">
            <div className="font-medium">user-service</div>
            <div className="mt-1 text-zinc-500">账号、注册、邮箱、短信、SSO</div>
          </div>
          <div className="rounded-md border border-zinc-800 bg-zinc-950/40 p-3">
            <div className="font-medium">ai-service / pay-service</div>
            <div className="mt-1 text-zinc-500">模型池、调用方、通用积分、全局账务</div>
          </div>
        </div>
      </AdminPanel>
    </div>
  );
};

export default Dashboard;
