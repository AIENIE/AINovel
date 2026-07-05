import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "@/lib/api-client";
import { WorldDetail, WorldModuleDefinition } from "@/types";
import { Button } from "@/components/ui/button";
import { Loader2, ArrowLeft, Save, UploadCloud } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import WorldMetadataForm from "@/pages/WorldBuilder/components/WorldMetadataForm";
import WorldModuleEditor from "@/pages/WorldBuilder/components/WorldModuleEditor";
import { useAuth } from "@/contexts/AuthContext";

const WorldEditor = () => {
  const [params] = useSearchParams();
  const id = params.get("id");
  const { toast } = useToast();
  const { refreshProfile } = useAuth();

  const [definitions, setDefinitions] = useState<WorldModuleDefinition[]>([]);
  const [worldDetail, setWorldDetail] = useState<WorldDetail | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    api.worlds.getDefinitions().then(setDefinitions).catch(() => setDefinitions([]));
  }, []);

  const load = async () => {
    if (!id) return;
    setIsLoading(true);
    try {
      const detail = await api.worlds.getDetail(id);
      setWorldDetail(detail);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleUpdateMetadata = (field: keyof WorldDetail, value: any) => {
    if (!worldDetail) return;
    setWorldDetail({ ...worldDetail, [field]: value });
  };

  const handleUpdateModule = (moduleKey: string, fieldKey: string, value: string) => {
    if (!worldDetail) return;
    const newModules = [...(worldDetail.modules || [])];
    const moduleIndex = newModules.findIndex((m) => m.key === moduleKey);
    if (moduleIndex >= 0) {
      newModules[moduleIndex] = {
        ...newModules[moduleIndex],
        fields: {
          ...newModules[moduleIndex].fields,
          [fieldKey]: value,
        },
      };
    } else {
      newModules.push({ key: moduleKey, fields: { [fieldKey]: value } });
    }
    setWorldDetail({ ...worldDetail, modules: newModules });
  };

  const handleSave = async () => {
    if (!worldDetail) return;
    setIsSaving(true);
    try {
      await api.worlds.update(worldDetail.id, worldDetail);
      toast({ title: "保存成功" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "保存失败", description: e.message });
    } finally {
      setIsSaving(false);
    }
  };

  const handleAutoGenerate = async (moduleKey: string) => {
    if (!worldDetail) return;
    try {
      await api.worlds.publish(worldDetail.id);
      await api.worlds.generateModule(worldDetail.id, moduleKey);
      await load();
      toast({ title: `已生成模块：${moduleKey}` });
    } catch (e: any) {
      toast({ variant: "destructive", title: "生成失败", description: e.message });
    }
  };

  const handleRefineField = async (moduleKey: string, fieldKey: string, text: string) => {
    if (!worldDetail) return null;
    try {
      const res = await api.worlds.refineField(worldDetail.id, moduleKey, fieldKey, text, "");
      await refreshProfile();
      return res?.result || res?.content || res?.resultText || res?.result || null;
    } catch (e: any) {
      toast({ variant: "destructive", title: "润色失败", description: e.message });
      return null;
    }
  };

  const canRender = useMemo(() => !!id, [id]);

  if (!canRender) {
    return (
      <div className="p-6">
        <div className="text-muted-foreground">缺少 world id，请从“我的世界”进入。</div>
        <Link to="/worlds">
          <Button className="mt-4">返回世界列表</Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="h-14 border-b flex items-center justify-between px-4 lg:px-8 bg-background/95 backdrop-blur sticky top-0 z-10">
        <div className="flex items-center gap-3">
          <Link to="/worlds">
            <Button variant="ghost" size="icon" className="mr-1">
              <ArrowLeft className="h-4 w-4" />
            </Button>
          </Link>
          <span className="font-semibold">{worldDetail?.name || "世界编辑器"}</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleSave} disabled={isSaving || !worldDetail}>
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            保存
          </Button>
          <Button
            onClick={async () => {
              if (!worldDetail) return;
              try {
                const preview = await api.worlds.publishPreview(worldDetail.id);
                const modules: string[] = preview?.modulesToGenerate || [];
                if (modules.length === 0) {
                  await api.worlds.publish(worldDetail.id);
                  await load();
                  toast({ title: "发布完成", description: "无需生成模块，已更新发布状态/版本。" });
                  return;
                }
                if (!confirm(`预检结果：需要生成 ${modules.length} 个模块：\n${modules.join(", ")}\n\n现在开始生成并发布？`)) return;
                await api.worlds.publish(worldDetail.id);
                for (const key of modules) {
                  await api.worlds.generateModule(worldDetail.id, key);
                }
                await load();
                toast({ title: "发布完成", description: "已生成并更新世界模块内容" });
              } catch (e: any) {
                toast({ variant: "destructive", title: "预检失败", description: e.message });
              }
            }}
            disabled={!worldDetail}
          >
            <UploadCloud className="mr-2 h-4 w-4" />
            预检/发布
          </Button>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {isLoading ? (
          <div className="flex items-center justify-center h-[60vh]">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        ) : worldDetail ? (
          <>
            <WorldMetadataForm data={worldDetail} onChange={handleUpdateMetadata} />
            <WorldModuleEditor
              worldId={worldDetail.id}
              definitions={definitions}
              moduleData={worldDetail.modules || []}
              onUpdateModule={handleUpdateModule}
              onAutoGenerate={handleAutoGenerate}
              onRefineField={handleRefineField}
            />
          </>
        ) : (
          <div className="text-muted-foreground">世界不存在或无权限访问</div>
        )}
      </div>
    </div>
  );
};

export default WorldEditor;
