import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/use-toast";
import { Save, Loader2, Wrench, ShieldCheck } from "lucide-react";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

type AdminSystemConfig = {
  maintenanceMode: boolean;
};

const managedByServices = [
  { name: "账号、注册、邮箱、短信、SSO", owner: "user-service" },
  { name: "模型池、API Key、调用方密钥、AI 成本报表", owner: "ai-service" },
  { name: "通用积分、充值、签到配置、全局账务后台", owner: "pay-service" },
];

const SystemSettingsPage = () => {
  const [settings, setSettings] = useState<AdminSystemConfig | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const { toast } = useToast();

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      setSettings(await api.admin.getSystemConfig());
    } catch (err: unknown) {
      setError(getErrorMessage(err, "系统配置加载失败"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const handleSave = async () => {
    if (!settings) return;
    setIsSaving(true);
    try {
      const updated = await api.admin.updateSystemConfig({ maintenanceMode: settings.maintenanceMode });
      setSettings(updated);
      toast({ title: "维护设置已更新" });
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "保存失败", description: getErrorMessage(err, "请求失败") });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="max-w-4xl space-y-6">
      <AdminPageHeader
        title="系统维护"
        description="仅管理 AINovel 本地运行开关。"
        actions={
        <Button onClick={handleSave} disabled={isSaving} className="bg-zinc-100 text-zinc-900 hover:bg-zinc-200">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
          保存
        </Button>
        }
      />

      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
      {isLoading ? <AdminLoadingState rows={2} /> : null}
      {!isLoading && !error && !settings ? <AdminEmptyState title="暂无系统配置" /> : null}

      {!isLoading && settings ? (
        <AdminPanel
          title="本地维护模式"
          description="开启后仅管理员可访问核心业务页面。"
          actions={<Wrench className="h-5 w-5 text-amber-400" />}
        >
          <div className="flex items-center justify-between rounded-md border border-zinc-800 bg-zinc-950 p-4">
            <div className="space-y-1">
              <Label className="text-base">维护模式</Label>
              <p className="text-sm text-zinc-500">用于 AINovel 本项目停机维护，不影响统一登录或公共服务。</p>
            </div>
            <Switch
              checked={settings.maintenanceMode}
              onCheckedChange={(checked) => setSettings({ ...settings, maintenanceMode: checked })}
            />
          </div>
        </AdminPanel>
      ) : null}

      <AdminPanel
        title="外部服务托管项"
        description="以下能力不在 AINovel 后台配置，避免与公共服务后台重叠。"
        actions={<ShieldCheck className="h-5 w-5 text-emerald-400" />}
      >
        <div className="space-y-3">
          {managedByServices.map((item) => (
            <div key={item.name} className="flex items-center justify-between rounded-md border border-zinc-800 p-3">
              <span className="text-sm text-zinc-300">{item.name}</span>
              <span className="text-xs text-zinc-500">{item.owner}</span>
            </div>
          ))}
        </div>
      </AdminPanel>
    </div>
  );
};

export default SystemSettingsPage;
