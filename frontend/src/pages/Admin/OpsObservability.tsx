import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api-client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Activity, AlertTriangle, Bell, ClipboardList, Database, RefreshCcw, ServerCog, ShieldCheck } from "lucide-react";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel, adminPanelClass } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

type OpsRecordPage = {
  available?: boolean;
  message?: string;
  items?: Array<Record<string, unknown>>;
  total?: number;
};

type Dependency = {
  key: string;
  name: string;
  category: string;
  protocol: string;
  endpoint: string;
  status: "UP" | "DEGRADED" | "DOWN" | string;
  latencyMs?: number;
  message?: string;
  checkedAt?: string;
};

type AlertItem = {
  key: string;
  severity: string;
  title: string;
  detail?: unknown;
  generatedAt?: string;
};

const severityClass = (severity?: string) => {
  if (severity === "CRITICAL" || severity === "ERROR") return "border-rose-800 text-rose-300";
  if (severity === "WARN") return "border-amber-800 text-amber-300";
  return "border-zinc-700 text-zinc-300";
};

const statusClass = (status?: string) => {
  if (status === "UP") return "border-emerald-800 text-emerald-300";
  if (status === "DEGRADED") return "border-amber-800 text-amber-300";
  return "border-rose-800 text-rose-300";
};

const fmt = (value: unknown) => {
  if (value === undefined || value === null || value === "") return "-";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
};

const OpsObservability = () => {
  const [summary, setSummary] = useState<any>(null);
  const [dependencies, setDependencies] = useState<Dependency[]>([]);
  const [events, setEvents] = useState<OpsRecordPage | null>(null);
  const [audit, setAudit] = useState<OpsRecordPage | null>(null);
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [diagnostics, setDiagnostics] = useState<Record<string, unknown> | null>(null);
  const [activeView, setActiveView] = useState<"events" | "audit">("events");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      const [summaryData, dependencyData, alertData, diagnosticsData, eventData, auditData] = await Promise.all([
        api.admin.getOpsSummary(),
        api.admin.listDependencies(),
        api.admin.listOpsAlerts(),
        api.admin.getOpsDiagnostics(),
        api.admin.listOpsEvents({ page: 0, size: 20 }),
        api.admin.listAuditRecords({ page: 0, size: 20 }),
      ]);
      setSummary(summaryData);
      setDependencies(dependencyData || []);
      setAlerts(alertData || []);
      setDiagnostics(diagnosticsData || null);
      setEvents(eventData || null);
      setAudit(auditData || null);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "运维观测数据加载失败"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const requestStats = summary?.requests || {};
  const issueCount = useMemo(() => dependencies.filter((item) => item.status !== "UP").length, [dependencies]);
  const visibleRecords = activeView === "events" ? events : audit;

  const cards = [
    { label: "总请求", value: Number(requestStats.totalRequests ?? 0).toLocaleString(), note: "当前进程滚动累计", icon: Activity },
    { label: "5xx 错误率", value: `${((Number(requestStats.errorRate ?? 0)) * 100).toFixed(1)}%`, note: `${requestStats.errorRequests ?? 0} 次错误`, icon: AlertTriangle },
    { label: "依赖异常", value: issueCount, note: `${dependencies.length} 个依赖探测`, icon: ServerCog },
    { label: "派生告警", value: alerts.length, note: summary?.maintenanceMode ? "维护模式已开启" : "只读告警流", icon: Bell },
  ];

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="运维观测"
        description="AINovel 本项目运行状态、依赖健康、结构化记录和派生告警。"
        actions={
          <Button onClick={() => void load()} variant="outline" className="border-zinc-800 bg-zinc-900 text-zinc-200 hover:bg-zinc-800">
            <RefreshCcw className="mr-2 h-4 w-4" />
            刷新
          </Button>
        }
      />

      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
      {isLoading ? <AdminLoadingState rows={5} /> : null}

      {!isLoading && !error ? (
        <>
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

          <AdminPanel title="依赖健康" description="仅展示 AINovel 调用视角，不管理外部服务配置。" actions={<Database className="h-5 w-5 text-emerald-400" />}>
            <div className="space-y-3">
              {dependencies.length === 0 ? <AdminEmptyState title="暂无依赖探测结果" /> : null}
              {dependencies.map((item) => (
                <div key={item.key} className="grid gap-3 rounded-md border border-zinc-800 bg-zinc-950/50 p-3 md:grid-cols-[1fr_auto] md:items-center">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium text-zinc-100">{item.name}</span>
                      <Badge variant="outline" className={statusClass(item.status)}>{item.status}</Badge>
                      <span className="text-xs text-zinc-600">{item.protocol}</span>
                    </div>
                    <div className="mt-1 truncate text-sm text-zinc-500">{item.endpoint}</div>
                    <div className="mt-1 text-xs text-zinc-600">{item.message || "OK"}</div>
                  </div>
                  <div className="text-sm text-zinc-400">{Number(item.latencyMs ?? 0)} ms</div>
                </div>
              ))}
            </div>
          </AdminPanel>

          <AdminPanel title="派生告警" description="由 AINovel 当前指标和依赖状态实时派生，不提供确认或关闭操作。" actions={<Bell className="h-5 w-5 text-amber-400" />}>
            <div className="space-y-3">
              {alerts.length === 0 ? <AdminEmptyState title="暂无派生告警" description="当前错误率、依赖状态和记录检索未触发告警。" /> : null}
              {alerts.map((item) => (
                <div key={item.key} className="flex flex-col gap-2 rounded-md border border-zinc-800 bg-zinc-950/50 p-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <div className="font-medium text-zinc-100">{item.title}</div>
                    <div className="mt-1 text-xs text-zinc-500">{fmt(item.detail)}</div>
                  </div>
                  <Badge variant="outline" className={severityClass(item.severity)}>{item.severity}</Badge>
                </div>
              ))}
            </div>
          </AdminPanel>

          <AdminPanel
            title="结构化记录"
            description="从 AINovel 自有 ES 索引读取；ES 不可用时业务运行不受影响。"
            actions={<ClipboardList className="h-5 w-5 text-sky-400" />}
          >
            <div className="space-y-4">
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="outline" className={activeView === "events" ? "border-sky-800 bg-sky-950/40 text-sky-200" : "border-zinc-800 bg-zinc-950 text-zinc-400"} onClick={() => setActiveView("events")}>
                  运维事件
                </Button>
                <Button size="sm" variant="outline" className={activeView === "audit" ? "border-sky-800 bg-sky-950/40 text-sky-200" : "border-zinc-800 bg-zinc-950 text-zinc-400"} onClick={() => setActiveView("audit")}>
                  操作审计
                </Button>
              </div>
              {!visibleRecords?.available ? (
                <AdminEmptyState title="记录检索不可用" description={visibleRecords?.message || "Elasticsearch 未配置或暂不可达。"} />
              ) : visibleRecords.items?.length ? (
                <div className="space-y-2">
                  {visibleRecords.items.map((item, index) => (
                    <div key={`${fmt(item.recordId)}-${index}`} className="rounded-md border border-zinc-800 bg-zinc-950/50 p-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className={severityClass(String(item.severity || "INFO"))}>{fmt(item.severity || "INFO")}</Badge>
                        <span className="font-medium text-zinc-100">{fmt(item.action || item.dependencyName || item.category)}</span>
                        <span className="text-xs text-zinc-600">{fmt(item.createdAt)}</span>
                      </div>
                      <div className="mt-2 grid gap-2 text-xs text-zinc-500 md:grid-cols-3">
                        <span>actor: {fmt(item.actor)}</span>
                        <span>target: {fmt(item.targetType)} / {fmt(item.targetId)}</span>
                        <span>result: {fmt(item.result || item.status)}</span>
                      </div>
                      {item.message ? <div className="mt-2 text-sm text-zinc-500">{fmt(item.message)}</div> : null}
                    </div>
                  ))}
                </div>
              ) : (
                <AdminEmptyState title="暂无结构化记录" />
              )}
            </div>
          </AdminPanel>

          <AdminPanel title="脱敏诊断" description="用于确认记录目录、ES 查询开关和运行状态，不返回任何密钥明文。" actions={<ShieldCheck className="h-5 w-5 text-emerald-400" />}>
            <div className="grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-3">
              {Object.entries(diagnostics || {}).map(([key, value]) => (
                <div key={key} className="rounded-md border border-zinc-800 bg-zinc-950/50 p-3">
                  <div className="text-xs uppercase text-zinc-600">{key}</div>
                  <div className="mt-1 break-words text-zinc-300">{fmt(value)}</div>
                </div>
              ))}
            </div>
          </AdminPanel>
        </>
      ) : null}
    </div>
  );
};

export default OpsObservability;
