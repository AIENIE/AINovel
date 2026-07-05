import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { TabsContent } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

type StatsSidebarPanelProps = {
  dailyHeatmap: any[];
  onRefresh: () => Promise<void> | void;
  workspaceStats: any;
};

export function StatsSidebarPanel({ dailyHeatmap, onRefresh, workspaceStats }: StatsSidebarPanelProps) {
  return (
    <TabsContent value="stats" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <Button size="sm" variant="outline" className="mb-2" onClick={() => void onRefresh()}>
        刷新统计
      </Button>
      <ScrollArea className="h-[calc(100%-2.5rem)]">
        <div className="grid grid-cols-2 gap-2 text-xs mb-2">
          <div className="rounded border p-2">会话数 {workspaceStats?.totalSessions ?? 0}</div>
          <div className="rounded border p-2">净字数 {workspaceStats?.totalNetWords ?? 0}</div>
        </div>
        <div className="grid grid-cols-1 gap-2 pr-1">
          <div className="h-[180px] rounded border p-2">
            <div className="text-xs text-muted-foreground mb-1">日维度</div>
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
            <div className="text-xs text-muted-foreground mb-1">周维度</div>
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
            <div className="text-xs text-muted-foreground mb-1">月维度</div>
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
            <div className="text-xs text-muted-foreground mb-2">近30天热力图</div>
            <div className="grid grid-cols-10 gap-1">
              {dailyHeatmap.map((item: any) => {
                const words = Number(item.netWords || 0);
                const level = words <= 0 ? 0 : words < 500 ? 1 : words < 1200 ? 2 : words < 2500 ? 3 : 4;
                const cls = ["bg-muted", "bg-emerald-100", "bg-emerald-200", "bg-emerald-400", "bg-emerald-600"][level];
                return <div key={item.date} title={`${item.date}: ${words} 字`} className={cn("h-4 rounded-sm border", cls)} />;
              })}
              {!dailyHeatmap.length && <div className="text-xs text-muted-foreground">暂无热力图数据</div>}
            </div>
          </div>
        </div>
      </ScrollArea>
    </TabsContent>
  );
}
