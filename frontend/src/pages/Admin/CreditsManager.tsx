import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/use-toast";
import { Loader2, Plus } from "lucide-react";

type RedeemCodeItem = {
  id: string;
  code: string;
  grantAmount: number;
  maxUses?: number | null;
  usedCount: number;
  enabled: boolean;
  stackable: boolean;
  expiresAt?: string | null;
  startsAt?: string | null;
  description?: string | null;
};

type AdminConversionItem = {
  id: string;
  orderNo: string;
  username: string;
  convertedAmount: number;
  projectBefore: number;
  projectAfter: number;
  publicBefore: number;
  publicAfter: number;
  status: string;
  createdAt: string;
};

type AdminLedgerItem = {
  id: string;
  username: string;
  type: string;
  delta: number;
  balanceAfter: number;
  description?: string;
  createdAt: string;
};

const CreditsManager = () => {
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [items, setItems] = useState<RedeemCodeItem[]>([]);
  const [conversionItems, setConversionItems] = useState<AdminConversionItem[]>([]);
  const [ledgerItems, setLedgerItems] = useState<AdminLedgerItem[]>([]);

  const [code, setCode] = useState(`AICREDIT-${Date.now().toString().slice(-6)}`);
  const [grantAmount, setGrantAmount] = useState(1234);
  const [maxUses, setMaxUses] = useState("1");
  const [enabled, setEnabled] = useState(true);
  const [stackable, setStackable] = useState(false);
  const [description, setDescription] = useState("后台验收创建");

  const load = async () => {
    setIsLoading(true);
    try {
      const [list, conversions, ledger] = await Promise.all([
        api.admin.listRedeemCodes(),
        api.admin.listConversionOrders(),
        api.admin.listCreditLedger(),
      ]);
      setItems(list || []);
      setConversionItems(conversions || []);
      setLedgerItems(ledger || []);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleCreate = async () => {
    if (!code.trim()) return;
    if (!Number.isFinite(grantAmount) || grantAmount <= 0) return;

    setIsSubmitting(true);
    try {
      await api.admin.createRedeemCode({
        code: code.trim(),
        grantAmount,
        maxUses: maxUses.trim() ? Number(maxUses) : null,
        enabled,
        stackable,
        description: description.trim() || undefined,
      });
      toast({ title: "兑换码创建成功" });
      setCode(`AICREDIT-${Date.now().toString().slice(-6)}`);
      setDescription("后台验收创建");
      await load();
    } catch (error: any) {
      toast({ variant: "destructive", title: "创建失败", description: error?.message || "请求失败" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">积分与兑换码</h1>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>创建兑换码</CardTitle>
          <CardDescription className="text-zinc-400">用于发放本项目专属积分（本地账本）。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>兑换码</Label>
              <Input value={code} onChange={(e) => setCode(e.target.value)} className="bg-zinc-950 border-zinc-800" />
            </div>
            <div className="space-y-2">
              <Label>积分（本次请填 1234）</Label>
              <Input
                type="number"
                min={1}
                value={grantAmount}
                onChange={(e) => setGrantAmount(Number(e.target.value))}
                className="bg-zinc-950 border-zinc-800"
              />
            </div>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>最大使用次数（留空=不限）</Label>
              <Input
                type="number"
                min={1}
                value={maxUses}
                onChange={(e) => setMaxUses(e.target.value)}
                className="bg-zinc-950 border-zinc-800"
              />
            </div>
            <div className="space-y-2">
              <Label>备注</Label>
              <Input value={description} onChange={(e) => setDescription(e.target.value)} className="bg-zinc-950 border-zinc-800" />
            </div>
          </div>
          <div className="flex items-center gap-8">
            <div className="flex items-center gap-2">
              <Switch checked={enabled} onCheckedChange={setEnabled} />
              <Label>启用</Label>
            </div>
            <div className="flex items-center gap-2">
              <Switch checked={stackable} onCheckedChange={setStackable} />
              <Label>允许同一用户重复领取</Label>
            </div>
          </div>
          <Button onClick={handleCreate} disabled={isSubmitting} className="bg-zinc-100 text-zinc-900 hover:bg-zinc-200">
            {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Plus className="mr-2 h-4 w-4" />}
            创建兑换码
          </Button>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>兑换码列表</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="text-zinc-400">加载中...</div>
          ) : items.length === 0 ? (
            <div className="text-zinc-400">暂无兑换码</div>
          ) : (
            <div className="space-y-2">
              {items.map((item) => (
                <div key={item.id} className="rounded-md border border-zinc-800 p-3 flex items-center justify-between">
                  <div className="space-y-1">
                    <div className="font-medium">{item.code}</div>
                    <div className="text-xs text-zinc-400">
                      积分 {item.grantAmount} / 已用 {item.usedCount} / 上限 {item.maxUses ?? "不限"}
                    </div>
                  </div>
                  <div className="text-xs text-zinc-400">
                    {item.enabled ? "启用" : "停用"} · {item.stackable ? "可重复" : "单用户一次"}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>通用积分兑换订单</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="text-zinc-400">加载中...</div>
          ) : conversionItems.length === 0 ? (
            <div className="text-zinc-400">暂无兑换订单</div>
          ) : (
            <div className="space-y-2">
              {conversionItems.slice(0, 12).map((item) => (
                <div key={item.id} className="rounded-md border border-zinc-800 p-3 text-sm">
                  <div className="flex items-center justify-between">
                    <div className="font-medium">{item.orderNo}</div>
                    <div className="text-xs text-zinc-400">{item.status}</div>
                  </div>
                  <div className="text-xs text-zinc-400">
                    用户 {item.username} · 通用 {item.publicBefore} -&gt; {item.publicAfter} · 项目 {item.projectBefore} -&gt; {item.projectAfter}
                  </div>
                  <div className="text-xs text-zinc-500">
                    兑换 {item.convertedAmount} · {new Date(item.createdAt).toLocaleString()}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>项目积分流水</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="text-zinc-400">加载中...</div>
          ) : ledgerItems.length === 0 ? (
            <div className="text-zinc-400">暂无流水</div>
          ) : (
            <div className="space-y-2">
              {ledgerItems.slice(0, 12).map((item) => (
                <div key={item.id} className="rounded-md border border-zinc-800 p-3 text-sm flex items-center justify-between">
                  <div>
                    <div className="font-medium">{item.username} · {item.type}</div>
                    <div className="text-xs text-zinc-500">{item.description || "-"}</div>
                  </div>
                  <div className="text-right">
                    <div className={item.delta >= 0 ? "text-emerald-400 font-semibold" : "text-rose-400 font-semibold"}>
                      {item.delta >= 0 ? "+" : ""}{item.delta}
                    </div>
                    <div className="text-xs text-zinc-500">余额 {item.balanceAfter}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default CreditsManager;
