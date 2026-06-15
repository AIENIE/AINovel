import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { AdminDashboardStats } from "@/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Users, Zap, AlertTriangle, FileClock, BookOpen, Globe2, ScrollText, ShieldAlert } from "lucide-react";

const statCardClass = "bg-zinc-900 border-zinc-800 text-zinc-100";

const Dashboard = () => {
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [assets, setAssets] = useState<any | null>(null);

  useEffect(() => {
    Promise.all([api.admin.getDashboardStats(), api.admin.getAssetSummary()]).then(([dashboard, summary]) => {
      setStats(dashboard);
      setAssets(summary);
    });
  }, []);

  if (!stats || !assets) return <div className="text-zinc-500">Loading stats...</div>;

  const cards = [
    { label: "总用户数", value: stats.totalUsers, note: `今日新增 +${stats.todayNewUsers}`, icon: Users },
    { label: "今日积分消耗", value: stats.todayCreditsConsumed.toLocaleString(), note: `总消耗 ${stats.totalCreditsConsumed.toLocaleString()}`, icon: Zap },
    { label: "API 错误率", value: `${(stats.apiErrorRate * 100).toFixed(1)}%`, note: stats.apiErrorRate < 0.05 ? "状态良好" : "需要关注", icon: AlertTriangle },
    { label: "待审素材", value: stats.pendingReviews, note: "进入素材治理处理", icon: FileClock },
    { label: "故事", value: assets.stories ?? 0, note: "本地创作资产", icon: BookOpen },
    { label: "世界观", value: assets.worlds ?? 0, note: "本地创作资产", icon: Globe2 },
    { label: "稿件", value: assets.manuscripts ?? 0, note: "本地创作资产", icon: ScrollText },
    { label: "高风险质量记录", value: assets.highRiskQualityRuns ?? 0, note: "风险分 >= 70", icon: ShieldAlert },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold">业务运营概览</h1>
        <p className="text-sm text-zinc-500 mt-1">AINovel 本地业务对象与项目专属积分运营状态。</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        {cards.map((item) => (
          <Card key={item.label} className={statCardClass}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-zinc-400">{item.label}</CardTitle>
              <item.icon className="h-4 w-4 text-zinc-400" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{item.value}</div>
              <p className="text-xs text-zinc-500">{item.note}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className={statCardClass}>
        <CardHeader>
          <CardTitle>后台职责边界</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm">
          <div className="rounded-md border border-zinc-800 p-3">
            <div className="font-medium">AINovel</div>
            <div className="text-zinc-500 mt-1">素材、创作资产、质量巡检、项目专属积分</div>
          </div>
          <div className="rounded-md border border-zinc-800 p-3">
            <div className="font-medium">user-service</div>
            <div className="text-zinc-500 mt-1">账号、注册、邮箱、短信、SSO</div>
          </div>
          <div className="rounded-md border border-zinc-800 p-3">
            <div className="font-medium">ai-service / pay-service</div>
            <div className="text-zinc-500 mt-1">模型池、调用方、通用积分、全局账务</div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default Dashboard;
