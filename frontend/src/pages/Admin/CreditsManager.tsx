import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/use-toast";
import { Badge } from "@/components/ui/badge";
import { Loader2, Plus, RefreshCcw } from "lucide-react";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage, matchesAdminSearch } from "./admin-list-utils";

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
  const [error, setError] = useState("");
  const [items, setItems] = useState<RedeemCodeItem[]>([]);
  const [conversionItems, setConversionItems] = useState<AdminConversionItem[]>([]);
  const [ledgerItems, setLedgerItems] = useState<AdminLedgerItem[]>([]);
  const [search, setSearch] = useState("");
  const [conversionPage, setConversionPage] = useState(0);
  const [ledgerPage, setLedgerPage] = useState(0);
  const pageSize = 10;

  const [code, setCode] = useState(`AICREDIT-${Date.now().toString().slice(-6)}`);
  const [grantAmount, setGrantAmount] = useState(1234);
  const [maxUses, setMaxUses] = useState("1");
  const [enabled, setEnabled] = useState(true);
  const [stackable, setStackable] = useState(false);
  const [description, setDescription] = useState("后台创建");

  const load = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const [list, conversions, ledger] = await Promise.all([
        api.admin.listRedeemCodes(),
        api.admin.listConversionOrders(conversionPage, pageSize),
        api.admin.listCreditLedger(ledgerPage, pageSize),
      ]);
      setItems(list || []);
      setConversionItems(conversions || []);
      setLedgerItems(ledger || []);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "积分数据加载失败"));
    } finally {
      setIsLoading(false);
    }
  }, [conversionPage, ledgerPage]);

  useEffect(() => {
    void load();
  }, [load]);

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
      setDescription("后台创建");
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "创建失败", description: getErrorMessage(err, "请求失败") });
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredCodes = items.filter((item) => matchesAdminSearch(item, search));
  const canNextConversions = conversionItems.length === pageSize;
  const canNextLedger = ledgerItems.length === pageSize;

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="积分与兑换码"
        description="管理 AINovel 本地项目专属积分兑换码、兑换订单快照和项目流水。"
        actions={
          <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800" onClick={() => void load()}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            刷新
          </Button>
        }
      />
      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}

      <AdminPanel title="创建兑换码" description="用于发放本项目专属积分（本地账本）。">
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>兑换码</Label>
              <Input value={code} onChange={(e) => setCode(e.target.value)} className="bg-zinc-950 border-zinc-800" />
            </div>
            <div className="space-y-2">
              <Label>积分</Label>
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
        </div>
      </AdminPanel>

      <AdminPanel title="兑换码列表" description="可按兑换码、备注、状态和使用次数搜索。">
        <div className="space-y-4">
          <AdminSearchToolbar value={search} onChange={setSearch} placeholder="搜索兑换码、备注或状态" />
          {isLoading ? (
            <AdminLoadingState rows={4} />
          ) : filteredCodes.length === 0 ? (
            <AdminEmptyState title={items.length === 0 ? "暂无兑换码" : "没有匹配的兑换码"} />
          ) : (
            <div className="space-y-2">
              {filteredCodes.map((item) => (
                <div key={item.id} className="flex flex-col gap-3 rounded-md border border-zinc-800 bg-zinc-950/30 p-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="space-y-1">
                    <div className="font-medium">{item.code}</div>
                    <div className="text-xs text-zinc-400">
                      积分 {item.grantAmount} / 已用 {item.usedCount} / 上限 {item.maxUses ?? "不限"}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2 text-xs text-zinc-400">
                    <Badge variant="outline" className={item.enabled ? "border-emerald-900 text-emerald-400" : "border-zinc-700 text-zinc-500"}>
                      {item.enabled ? "启用" : "停用"}
                    </Badge>
                    <Badge variant="outline" className="border-zinc-700 text-zinc-400">
                      {item.stackable ? "可重复" : "单用户一次"}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </AdminPanel>

      <AdminPanel title="通用积分兑换订单" description="按后端分页参数读取兑换订单快照。">
        <div className="space-y-3">
          {isLoading ? (
            <AdminLoadingState rows={3} />
          ) : conversionItems.length === 0 ? (
            <AdminEmptyState title="暂无兑换订单" />
          ) : (
            <div className="space-y-2">
              {conversionItems.map((item) => (
                <div key={item.id} className="rounded-md border border-zinc-800 p-3 text-sm">
                  <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
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
          <div className="flex items-center justify-between border-t border-zinc-800 pt-3 text-sm text-zinc-500">
            <span>第 {conversionPage + 1} 页</span>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-950 text-zinc-300" disabled={conversionPage === 0} onClick={() => setConversionPage((value) => Math.max(0, value - 1))}>上一页</Button>
              <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-950 text-zinc-300" disabled={!canNextConversions} onClick={() => setConversionPage((value) => value + 1)}>下一页</Button>
            </div>
          </div>
        </div>
      </AdminPanel>

      <AdminPanel title="项目积分流水" description="按后端分页参数读取本地项目专属积分流水。">
        <div className="space-y-3">
          {isLoading ? (
            <AdminLoadingState rows={3} />
          ) : ledgerItems.length === 0 ? (
            <AdminEmptyState title="暂无流水" />
          ) : (
            <div className="space-y-2">
              {ledgerItems.map((item) => (
                <div key={item.id} className="flex flex-col gap-3 rounded-md border border-zinc-800 p-3 text-sm sm:flex-row sm:items-center sm:justify-between">
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
          <div className="flex items-center justify-between border-t border-zinc-800 pt-3 text-sm text-zinc-500">
            <span>第 {ledgerPage + 1} 页</span>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-950 text-zinc-300" disabled={ledgerPage === 0} onClick={() => setLedgerPage((value) => Math.max(0, value - 1))}>上一页</Button>
              <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-950 text-zinc-300" disabled={!canNextLedger} onClick={() => setLedgerPage((value) => value + 1)}>下一页</Button>
            </div>
          </div>
        </div>
      </AdminPanel>
    </div>
  );
};

export default CreditsManager;
