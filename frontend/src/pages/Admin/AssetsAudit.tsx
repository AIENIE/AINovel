import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPager, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage, matchesAdminSearch, pageCountFor, paginateItems } from "./admin-list-utils";

const tabs = [
  { key: "stories", label: "故事" },
  { key: "worlds", label: "世界观" },
  { key: "manuscripts", label: "稿件" },
] as const;

type AdminAssetItem = {
  type: string;
  id: string;
  title: string;
  status: string;
  owner?: string;
  updatedAt?: string | null;
};

const AssetsAudit = () => {
  const [items, setItems] = useState<Record<string, AdminAssetItem[]>>({});
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState<(typeof tabs)[number]["key"]>("stories");
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async () => {
    setIsLoading(true);
    setError("");
    try {
      const [stories, worlds, manuscripts] = await Promise.all(tabs.map((tab) => api.admin.listAssets(tab.key)));
      setItems({ stories, worlds, manuscripts });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "创作资产加载失败"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    setPage(0);
  }, [activeTab, search]);

  const renderList = (kind: string) => {
    const list = (items[kind] || []).filter((item) => matchesAdminSearch(item, search));
    const pageCount = pageCountFor(list.length, 8);
    const visible = paginateItems(list, page, 8);
    if (isLoading) return <AdminLoadingState rows={5} />;
    if (list.length === 0) return <AdminEmptyState title={(items[kind] || []).length === 0 ? "暂无资产数据" : "没有匹配的资产"} />;
    return (
      <div className="space-y-3">
        {visible.map((item) => (
          <div key={`${item.type}-${item.id}`} className="rounded-md border border-zinc-800 bg-zinc-950/30 p-3">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0 space-y-1">
                <div className="truncate font-medium">{item.title}</div>
                <div className="text-xs text-zinc-500">用户 {item.owner || "-"} · {item.id}</div>
                <div className="text-xs text-zinc-600">{item.updatedAt ? new Date(item.updatedAt).toLocaleString() : "-"}</div>
              </div>
              <Badge variant="outline" className="border-zinc-700 text-zinc-400 shrink-0">{item.status}</Badge>
            </div>
          </div>
        ))}
        <AdminPager page={Math.min(page, pageCount - 1)} pageCount={pageCount} total={list.length} onPageChange={setPage} />
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <AdminPageHeader title="创作资产" description="只读审计故事、世界观与稿件，不直接修改用户创作内容。" />

      <AdminPanel title="资产列表" description="本页只展示最近资产，用于运营审计和问题定位。">
        <div className="space-y-4">
          <AdminSearchToolbar value={search} onChange={setSearch} placeholder="搜索标题、用户、状态或 ID" />
          {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
          <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as typeof activeTab)}>
            <TabsList className="bg-zinc-950 border border-zinc-800">
              {tabs.map((tab) => (
                <TabsTrigger key={tab.key} value={tab.key}>{tab.label}</TabsTrigger>
              ))}
            </TabsList>
            {tabs.map((tab) => (
              <TabsContent key={tab.key} value={tab.key} className="mt-4">
                {renderList(tab.key)}
              </TabsContent>
            ))}
          </Tabs>
        </div>
      </AdminPanel>
    </div>
  );
};

export default AssetsAudit;
