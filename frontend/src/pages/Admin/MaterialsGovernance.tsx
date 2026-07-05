import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { Material } from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Check, GitMerge, RefreshCcw, X } from "lucide-react";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage, matchesAdminSearch } from "./admin-list-utils";

type MaterialDuplicateCandidate = {
  sourceMaterialId: string;
  targetMaterialId: string;
  sourceTitle?: string;
  targetTitle?: string;
  score?: number;
  reasons?: string[];
};

const MaterialsGovernance = () => {
  const { toast } = useToast();
  const [pending, setPending] = useState<Material[]>([]);
  const [duplicates, setDuplicates] = useState<MaterialDuplicateCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [reviewingId, setReviewingId] = useState("");

  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const [materials, duplicateItems] = await Promise.all([
        api.admin.listPendingMaterials(),
        api.admin.findMaterialDuplicates(),
      ]);
      setPending(materials);
      setDuplicates(duplicateItems || []);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "素材治理数据加载失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const review = async (material: Material, action: "approve" | "reject") => {
    setReviewingId(material.id);
    try {
      if (action === "approve") {
        await api.admin.approveMaterial(material.id, {});
        toast({ title: "素材已通过" });
      } else {
        await api.admin.rejectMaterial(material.id, {});
        toast({ title: "素材已驳回" });
      }
      await load();
    } catch (err: unknown) {
      toast({ variant: "destructive", title: "审核失败", description: getErrorMessage(err, "请求失败") });
    } finally {
      setReviewingId("");
    }
  };

  const filteredPending = pending.filter((material) => matchesAdminSearch(material, search));
  const filteredDuplicates = duplicates.filter((item) => matchesAdminSearch(item, search));

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="素材治理"
        description="审核用户上传素材，识别重复素材并辅助合并。"
        actions={
          <Button size="sm" variant="outline" className="border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800" onClick={() => void load()}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            刷新
          </Button>
        }
      />

      <AdminSearchToolbar value={search} onChange={setSearch} placeholder="搜索素材标题、摘要、标签或重复原因" />
      {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}

      <AdminPanel title="待审素材" description="通过或驳回后列表会自动刷新。">
        <div className="space-y-3">
          {loading ? (
            <AdminLoadingState rows={3} />
          ) : filteredPending.length === 0 ? (
            <AdminEmptyState title={pending.length === 0 ? "暂无待审素材" : "没有匹配的待审素材"} />
          ) : (
            filteredPending.map((material) => (
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
                    <Button
                      size="icon"
                      variant="outline"
                      className="border-emerald-900 text-emerald-400"
                      disabled={reviewingId === material.id}
                      onClick={() => review(material, "approve")}
                    >
                      <Check className="h-4 w-4" />
                    </Button>
                    <Button
                      size="icon"
                      variant="outline"
                      className="border-rose-900 text-rose-400"
                      disabled={reviewingId === material.id}
                      onClick={() => review(material, "reject")}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </AdminPanel>

      <AdminPanel title="重复候选" description="按相似度和命中原因辅助人工判断，合并动作仍保持手动确认。">
        <div className="space-y-3">
          {loading ? (
            <AdminLoadingState rows={2} />
          ) : filteredDuplicates.length === 0 ? (
            <AdminEmptyState title={duplicates.length === 0 ? "暂无重复候选" : "没有匹配的重复候选"} />
          ) : (
            filteredDuplicates.slice(0, 20).map((item) => (
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
        </div>
      </AdminPanel>
    </div>
  );
};

export default MaterialsGovernance;
