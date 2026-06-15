import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";

const tabs = [
  { key: "stories", label: "故事" },
  { key: "worlds", label: "世界观" },
  { key: "manuscripts", label: "稿件" },
] as const;

const AssetsAudit = () => {
  const [items, setItems] = useState<Record<string, any[]>>({});

  useEffect(() => {
    Promise.all(tabs.map((tab) => api.admin.listAssets(tab.key))).then(([stories, worlds, manuscripts]) => {
      setItems({ stories, worlds, manuscripts });
    });
  }, []);

  const renderList = (kind: string) => {
    const list = items[kind] || [];
    if (list.length === 0) return <div className="text-zinc-500">暂无数据</div>;
    return (
      <div className="space-y-2">
        {list.map((item) => (
          <div key={`${item.type}-${item.id}`} className="rounded-md border border-zinc-800 p-3">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="font-medium truncate">{item.title}</div>
                <div className="text-xs text-zinc-500 mt-1">用户 {item.owner || "-"} · {item.id}</div>
              </div>
              <Badge variant="outline" className="border-zinc-700 text-zinc-400 shrink-0">{item.status}</Badge>
            </div>
            <div className="text-xs text-zinc-600 mt-2">{item.updatedAt ? new Date(item.updatedAt).toLocaleString() : "-"}</div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">创作资产</h1>
        <p className="text-sm text-zinc-500 mt-1">只读审计故事、世界观与稿件，不直接修改用户创作内容。</p>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>资产列表</CardTitle>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="stories">
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
        </CardContent>
      </Card>
    </div>
  );
};

export default AssetsAudit;
