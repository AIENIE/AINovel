import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { Material } from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/use-toast";
import { Check, GitMerge, Link2, Loader2, RefreshCcw, X } from "lucide-react";
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
  const [actionId, setActionId] = useState("");
  const [citationResult, setCitationResult] = useState<{ title: string; items: any[] } | null>(null);

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

  const merge = async (item: MaterialDuplicateCandidate) => {
    if (!confirm(`确认将「${item.sourceTitle}」合并到「${item.targetTitle}」吗？\n\n源素材会保留但标记为已驳回，退出检索和重复候选。`)) return;
    const key = `${item.sourceMaterialId}-${item.targetMaterialId}`;
    setActionId(key);
    try {
      await api.admin.mergeMaterials({ sourceMaterialId: item.sourceMaterialId, targetMaterialId: item.targetMaterialId, mergeTags: true, mergeSummaryWhenEmpty: true, note: "管理员重复素材合并" });
      toast({ title: "素材已合并" }); await load();
    } catch (err: unknown) { toast({ variant: "destructive", title: "合并失败", description: getErrorMessage(err, "请求失败") }); }
    finally { setActionId(""); }
  };

  const loadCitations = async (id: string, title: string) => {
    setActionId(`citations-${id}`);
    try { setCitationResult({ title, items: await api.admin.listMaterialCitations(id) }); }
    catch (err: unknown) { toast({ variant: "destructive", title: "引用查询失败", description: getErrorMessage(err, "请求失败") }); }
    finally { setActionId(""); }
  };

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
                  <div className="flex shrink-0 gap-2">
                    <Button size="sm" variant="outline" className="border-zinc-700" onClick={() => void loadCitations(item.sourceMaterialId, item.sourceTitle || "源素材")} disabled={actionId === `citations-${item.sourceMaterialId}`}>{actionId === `citations-${item.sourceMaterialId}` ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : <Link2 className="mr-1 h-3.5 w-3.5" />}源引用</Button>
                    <Button size="sm" className="bg-zinc-100 text-zinc-900 hover:bg-white" onClick={() => void merge(item)} disabled={actionId === `${item.sourceMaterialId}-${item.targetMaterialId}`}>{actionId === `${item.sourceMaterialId}-${item.targetMaterialId}` ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : <GitMerge className="mr-1 h-3.5 w-3.5" />}合并</Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </AdminPanel>
      {citationResult ? <AdminPanel title={`引用查询：${citationResult.title}`} description="显示该素材在稿件场景中的引用位置。"><div className="space-y-2">{citationResult.items.length ? citationResult.items.map((item, index) => <div key={`${item.manuscriptId || index}-${item.sceneId || index}`} className="rounded border border-zinc-800 p-3 text-sm"><div className="font-medium">{item.storyTitle || item.manuscriptTitle || "创作稿件"}</div><div className="text-zinc-500">{item.chapterTitle || "未标章节"} · {item.sceneTitle || item.sceneId || "未标场景"}</div></div>) : <AdminEmptyState title="当前没有检测到引用" />}<Button size="sm" variant="outline" onClick={() => setCitationResult(null)}>关闭查询结果</Button></div></AdminPanel> : null}
    </div>
  );
};

export default MaterialsGovernance;
