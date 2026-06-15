import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Material } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Check, GitMerge, Search, X } from "lucide-react";

const MaterialsGovernance = () => {
  const { toast } = useToast();
  const [pending, setPending] = useState<Material[]>([]);
  const [duplicates, setDuplicates] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const [materials, duplicateItems] = await Promise.all([
        api.admin.listPendingMaterials(),
        api.admin.findMaterialDuplicates(),
      ]);
      setPending(materials);
      setDuplicates(duplicateItems || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const review = async (material: Material, action: "approve" | "reject") => {
    try {
      if (action === "approve") {
        await api.admin.approveMaterial(material.id, {});
        toast({ title: "素材已通过" });
      } else {
        await api.admin.rejectMaterial(material.id, {});
        toast({ title: "素材已驳回" });
      }
      await load();
    } catch (error: any) {
      toast({ variant: "destructive", title: "审核失败", description: error?.message || "请求失败" });
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">素材治理</h1>
        <p className="text-sm text-zinc-500 mt-1">审核用户上传素材，识别重复素材并辅助合并。</p>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle>待审素材</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? (
            <div className="text-zinc-500">加载中...</div>
          ) : pending.length === 0 ? (
            <div className="text-zinc-500">暂无待审素材</div>
          ) : (
            pending.map((material) => (
              <div key={material.id} className="rounded-md border border-zinc-800 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <div className="font-medium truncate">{material.title}</div>
                      <Badge variant="outline" className="border-zinc-700 text-zinc-400">{material.type}</Badge>
                    </div>
                    <p className="text-sm text-zinc-500 mt-1 line-clamp-2">{material.summary || material.content}</p>
                    <div className="flex flex-wrap gap-1 mt-2">
                      {material.tags.map((tag) => (
                        <span key={tag} className="text-xs rounded border border-zinc-800 px-2 py-0.5 text-zinc-500">{tag}</span>
                      ))}
                    </div>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <Button size="icon" variant="outline" className="border-emerald-900 text-emerald-400" onClick={() => review(material, "approve")}>
                      <Check className="h-4 w-4" />
                    </Button>
                    <Button size="icon" variant="outline" className="border-rose-900 text-rose-400" onClick={() => review(material, "reject")}>
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-100">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Search className="h-5 w-5 text-blue-400" />
            重复候选
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {duplicates.length === 0 ? (
            <div className="text-zinc-500">暂无重复候选</div>
          ) : (
            duplicates.slice(0, 20).map((item) => (
              <div key={`${item.sourceMaterialId}-${item.targetMaterialId}`} className="rounded-md border border-zinc-800 p-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="font-medium">{item.sourceTitle} / {item.targetTitle}</div>
                    <div className="text-zinc-500">相似度 {Number(item.score ?? 0).toFixed(2)} · {(item.reasons || []).join(", ")}</div>
                  </div>
                  <GitMerge className="h-4 w-4 text-zinc-500" />
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default MaterialsGovernance;
