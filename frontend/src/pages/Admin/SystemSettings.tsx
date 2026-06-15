import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/components/ui/use-toast";
import { Save, Loader2, Wrench, ShieldCheck } from "lucide-react";

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
  const { toast } = useToast();

  useEffect(() => {
    api.admin.getSystemConfig().then(setSettings);
  }, []);

  const handleSave = async () => {
    if (!settings) return;
    setIsSaving(true);
    try {
      const updated = await api.admin.updateSystemConfig({ maintenanceMode: settings.maintenanceMode });
      setSettings(updated);
      toast({ title: "维护设置已更新" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "保存失败", description: error?.message || "请求失败" });
    } finally {
      setIsSaving(false);
    }
  };

  if (!settings) return <div className="text-zinc-500">Loading...</div>;

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold">系统维护</h1>
          <p className="text-sm text-zinc-500 mt-1">仅管理 AINovel 本地运行开关。</p>
        </div>
        <Button onClick={handleSave} disabled={isSaving} className="bg-zinc-100 text-zinc-900 hover:bg-zinc-200">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
          保存
        </Button>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Wrench className="h-5 w-5 text-amber-400" />
            本地维护模式
          </CardTitle>
          <CardDescription className="text-zinc-400">开启后仅管理员可访问核心业务页面。</CardDescription>
        </CardHeader>
        <CardContent>
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
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-emerald-400" />
            外部服务托管项
          </CardTitle>
          <CardDescription className="text-zinc-400">以下能力不在 AINovel 后台配置，避免与公共服务后台重叠。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {managedByServices.map((item) => (
            <div key={item.name} className="flex items-center justify-between rounded-md border border-zinc-800 p-3">
              <span className="text-sm text-zinc-300">{item.name}</span>
              <span className="text-xs text-zinc-500">{item.owner}</span>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
};

export default SystemSettingsPage;
