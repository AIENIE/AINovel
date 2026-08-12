import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { WorldDetail, WorldModuleDefinition } from "@/types";
import { Button } from "@/components/ui/button";
import { Loader2, ArrowLeft, Save, UploadCloud } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage } from "@/lib/error-messages";
import WorldMetadataForm from "@/pages/WorldBuilder/components/WorldMetadataForm";
import WorldModuleEditor from "@/pages/WorldBuilder/components/WorldModuleEditor";
import { useAuth } from "@/contexts/AuthContext";

const WorldEditor = () => {
  const [params] = useSearchParams();
  const id = params.get("id");
  const { toast } = useToast();
  const { t } = useTranslation();
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
      toast({ title: t("worlds.saved") });
    } catch (e: any) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(e, "errors.saveFailed") });
    } finally {
      setIsSaving(false);
    }
  };

  const handleAutoGenerate = async (moduleKey: string) => {
    if (!worldDetail) return;
    try {
      await runTrackedAiOperation(api.worlds.startGenerateModule(worldDetail.id, moduleKey));
      await load();
      toast({ title: t("worlds.moduleGenerated", { module: moduleKey }) });
    } catch (e: any) {
      toast({ variant: "destructive", title: t("errors.generateFailed"), description: localizedErrorMessage(e, "errors.generateFailed") });
    }
  };

  const handleRefineField = async (moduleKey: string, fieldKey: string, text: string) => {
    if (!worldDetail) return null;
    try {
      const res = await api.worlds.refineField(worldDetail.id, moduleKey, fieldKey, text, "");
      await refreshProfile();
      return res?.result || res?.content || res?.resultText || null;
    } catch (e: any) {
      toast({ variant: "destructive", title: t("worlds.refineFailed"), description: localizedErrorMessage(e, "worlds.refineFailed") });
      return null;
    }
  };

  const canRender = useMemo(() => !!id, [id]);

  if (!canRender) {
    return (
      <div className="p-6">
        <div className="text-muted-foreground">{t("worlds.missingId")}</div>
        <Link to="/worlds">
          <Button className="mt-4">{t("worlds.backToList")}</Button>
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
          <span className="font-semibold">{worldDetail?.name || t("worlds.editorTitle")}</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleSave} disabled={isSaving || !worldDetail}>
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            {t("common.save")}
          </Button>
          <Button
            onClick={async () => {
              if (!worldDetail) return;
              try {
                const preview = await api.worlds.publishPreview(worldDetail.id);
                const modules: string[] = preview?.modulesToGenerate || [];
                if (modules.length === 0) {
                  if (!confirm(t("worlds.publishReady"))) return;
                  await api.worlds.publish(worldDetail.id);
                  await load();
                  toast({ title: t("worlds.publishDone"), description: t("worlds.publishReadyDesc") });
                  return;
                }
                if (!confirm(t("worlds.publishNeeds", { count: modules.length, modules: modules.join(", ") }))) return;
                await runTrackedAiOperation(api.worlds.startPublish(worldDetail.id));
                await load();
                toast({ title: t("worlds.publishDone"), description: t("worlds.publishDoneDesc") });
              } catch (e: any) {
                toast({ variant: "destructive", title: t("worlds.publishCheckFailed"), description: localizedErrorMessage(e, "worlds.publishCheckFailed") });
              }
            }}
            disabled={!worldDetail}
          >
            <UploadCloud className="mr-2 h-4 w-4" />
            {t("worlds.publishCheck")}
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
          <div className="text-muted-foreground">{t("worlds.notFound")}</div>
        )}
      </div>
    </div>
  );
};

export default WorldEditor;
