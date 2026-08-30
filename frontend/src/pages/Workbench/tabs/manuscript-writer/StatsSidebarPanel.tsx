import type { NetworkObject } from "@/lib/api-client";
import { useTranslation } from "react-i18next";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { TabsContent } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

type StatsSidebarPanelProps = {
  dailyHeatmap: NetworkObject[];
  onRefresh: () => Promise<unknown> | void;
  workspaceStats: NetworkObject | null;
};

export function StatsSidebarPanel({ dailyHeatmap, onRefresh, workspaceStats }: StatsSidebarPanelProps) {
  const { t } = useTranslation();
  return (
    <TabsContent value="stats" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <Button size="sm" variant="outline" className="mb-2" onClick={() => void onRefresh()}>
        {t("statsPanel.refresh")}
      </Button>
      <ScrollArea className="h-[calc(100%-2.5rem)]">
        <div className="grid grid-cols-2 gap-2 text-xs mb-2">
          <div className="rounded border p-2">{t("statsPanel.sessions", { count: workspaceStats?.totalSessions ?? 0 })}</div>
          <div className="rounded border p-2">{t("statsPanel.netWords", { count: workspaceStats?.totalNetWords ?? 0 })}</div>
        </div>
        <div className="grid grid-cols-1 gap-2 pr-1">
          <div className="h-[180px] rounded border p-2">
            <div className="text-xs text-muted-foreground mb-1">{t("statsPanel.daily")}</div>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={workspaceStats?.dailySeries || []}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 10 }} />
                <Tooltip />
                <Line type="monotone" dataKey="netWords" stroke="#2f855a" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="h-[160px] rounded border p-2">
            <div className="text-xs text-muted-foreground mb-1">{t("statsPanel.weekly")}</div>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={workspaceStats?.weeklySeries || []}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="weekStart" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 10 }} />
                <Tooltip />
                <Line type="monotone" dataKey="netWords" stroke="#8b6f4e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="h-[160px] rounded border p-2">
            <div className="text-xs text-muted-foreground mb-1">{t("statsPanel.monthly")}</div>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={workspaceStats?.monthlySeries || []}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 10 }} />
                <Tooltip />
                <Line type="monotone" dataKey="netWords" stroke="#3b82f6" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="rounded border p-2">
            <div className="text-xs text-muted-foreground mb-2">{t("statsPanel.heatmap30")}</div>
            <div className="grid grid-cols-10 gap-1">
              {dailyHeatmap.map((item: NetworkObject) => {
                const words = Number(item.netWords || 0);
                const level = words <= 0 ? 0 : words < 500 ? 1 : words < 1200 ? 2 : words < 2500 ? 3 : 4;
                const cls = ["bg-muted", "bg-emerald-100", "bg-emerald-200", "bg-emerald-400", "bg-emerald-600"][level];
                return <div key={item.date} title={`${item.date}: ${words} ${t("statsPanel.words")}`} className={cn("h-4 rounded-sm border", cls)} />;
              })}
              {!dailyHeatmap.length && <div className="text-xs text-muted-foreground">{t("statsPanel.noHeatmap")}</div>}
            </div>
          </div>
        </div>
      </ScrollArea>
    </TabsContent>
  );
}
