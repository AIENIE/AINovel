import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/components/ui/use-toast";
import { Save, Loader2 } from "lucide-react";

type AdminSystemConfig = {
  registrationEnabled: boolean;
  maintenanceMode: boolean;
  checkInMinPoints: number;
  checkInMaxPoints: number;
  smtpHost?: string;
  smtpPort?: number;
  smtpUsername?: string;
  smtpPasswordIsSet?: boolean;
};

const SystemSettingsPage = () => {
  const [settings, setSettings] = useState<AdminSystemConfig | null>(null);
  const [smtpPassword, setSmtpPassword] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    api.admin.getSystemConfig().then(setSettings);
  }, []);

  const handleSave = async () => {
    if (!settings) return;
    setIsSaving(true);
    try {
      const payload: any = {
        registrationEnabled: settings.registrationEnabled,
        maintenanceMode: settings.maintenanceMode,
        checkInMinPoints: settings.checkInMinPoints,
        checkInMaxPoints: settings.checkInMaxPoints,
        smtpHost: settings.smtpHost,
        smtpPort: settings.smtpPort,
        smtpUsername: settings.smtpUsername,
        ...(smtpPassword ? { smtpPassword } : {}),
      };
      const updated = await api.admin.updateSystemConfig(payload);
      setSettings(updated);
      setSmtpPassword("");
      toast({ title: "系统设置已更新" });
    } finally {
      setIsSaving(false);
    }
  };

  if (!settings) return <div>Loading...</div>;

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold">系统设置</h1>
        <Button onClick={handleSave} disabled={isSaving} className="bg-zinc-100 text-zinc-900 hover:bg-zinc-200">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
          保存更改
        </Button>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>签到奖励配置</CardTitle>
          <CardDescription className="text-zinc-400">设置用户每日签到可获得的随机积分范围。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>最小积分</Label>
              <Input 
                type="number" 
                value={settings.checkInMinPoints}
                onChange={(e) => setSettings({ ...settings, checkInMinPoints: parseInt(e.target.value) })}
                className="bg-zinc-950 border-zinc-800"
              />
            </div>
            <div className="space-y-2">
              <Label>最大积分</Label>
              <Input 
                type="number" 
                value={settings.checkInMaxPoints}
                onChange={(e) => setSettings({ ...settings, checkInMaxPoints: parseInt(e.target.value) })}
                className="bg-zinc-950 border-zinc-800"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>SMTP 配置（全局）</CardTitle>
          <CardDescription className="text-zinc-400">用于注册验证码与测试邮件。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Host</Label>
              <Input value={settings.smtpHost || ""} onChange={(e) => setSettings({ ...settings, smtpHost: e.target.value })} className="bg-zinc-950 border-zinc-800" />
            </div>
            <div className="space-y-2">
              <Label>Port</Label>
              <Input
                type="number"
                value={settings.smtpPort ?? 587}
                onChange={(e) => setSettings({ ...settings, smtpPort: parseInt(e.target.value) })}
                className="bg-zinc-950 border-zinc-800"
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label>Username</Label>
            <Input value={settings.smtpUsername || ""} onChange={(e) => setSettings({ ...settings, smtpUsername: e.target.value })} className="bg-zinc-950 border-zinc-800" />
          </div>
          <div className="space-y-2">
            <Label>Password</Label>
            <Input
              type="password"
              value={smtpPassword}
              onChange={(e) => setSmtpPassword(e.target.value)}
              className="bg-zinc-950 border-zinc-800"
              placeholder={settings.smtpPasswordIsSet ? "已设置（输入新密码覆盖）" : "请输入密码"}
            />
          </div>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>功能开关</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label className="text-base">开放注册</Label>
              <p className="text-sm text-zinc-400">关闭后新用户将无法注册账号。</p>
            </div>
            <Switch 
              checked={settings.registrationEnabled}
              onCheckedChange={(c) => setSettings({ ...settings, registrationEnabled: c })}
            />
          </div>
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label className="text-base">维护模式</Label>
              <p className="text-sm text-zinc-400">开启后仅管理员可访问系统。</p>
            </div>
            <Switch 
              checked={settings.maintenanceMode}
              onCheckedChange={(c) => setSettings({ ...settings, maintenanceMode: c })}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default SystemSettingsPage;
