import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { WorldPromptMetadata, WorldPromptTemplates } from "@/types";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { useToast } from "@/components/ui/use-toast";
import { Loader2, HelpCircle, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";
import { localizedErrorMessage } from "@/lib/error-messages";

const WorldPrompts = () => {
  const [prompts, setPrompts] = useState<WorldPromptTemplates | null>(null);
  const [metadata, setMetadata] = useState<WorldPromptMetadata | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const { toast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    Promise.all([api.prompts.getWorld(), api.prompts.getWorldMetadata()]).then(([templates, nextMetadata]) => {
      setPrompts(templates);
      setMetadata(nextMetadata);
    });
  }, []);

  const handleSave = async () => {
    if (!prompts) return;
    setIsSaving(true);
    try {
      await api.prompts.updateWorld(prompts);
      toast({ title: t("worldPrompts.saved") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = async () => {
    setIsResetting(true);
    try {
      const defaults = await api.prompts.resetWorld();
      setPrompts(defaults);
      toast({ title: t("worldPrompts.resetDone") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("worldPrompts.resetFailed"), description: localizedErrorMessage(error, "worldPrompts.resetFailed") });
    } finally {
      setIsResetting(false);
    }
  };

  if (!prompts) return <div>{t("common.loading")}</div>;

  const moduleLabels = new Map(metadata?.modules.map((module) => [module.key, module.label]) ?? []);
  const moduleFieldLabels = new Map(
    metadata?.modules.map((module) => [module.key, module.fields.map((field) => field.label).join(" / ")]) ?? [],
  );

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button variant="outline" size="sm" asChild>
          <Link to="/settings/world-prompts/help">
            <HelpCircle className="mr-2 h-4 w-4" />
            {t("worldPrompts.viewVariables")}
          </Link>
        </Button>
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="outline" size="sm" disabled={isResetting}>
              {isResetting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RotateCcw className="mr-2 h-4 w-4" />}
              {t("worldPrompts.restoreDefault")}
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{t("worldPrompts.resetTitle")}</AlertDialogTitle>
              <AlertDialogDescription>{t("worldPrompts.resetDesc")}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
              <AlertDialogAction onClick={handleReset}>{t("worldPrompts.restoreDefault")}</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>

      <Tabs defaultValue="modules">
        <TabsList>
          <TabsTrigger value="modules">{t("worldPrompts.tabModules")}</TabsTrigger>
          <TabsTrigger value="final">{t("worldPrompts.tabFinal")}</TabsTrigger>
          <TabsTrigger value="refine">{t("worldPrompts.tabRefine")}</TabsTrigger>
        </TabsList>
        
        <TabsContent value="modules" className="space-y-4 mt-4">
          {Object.entries(prompts.modules).map(([key, template]) => (
            <Card key={key}>
              <CardHeader>
                <CardTitle>{moduleLabels.get(key) ?? key}</CardTitle>
                {moduleFieldLabels.get(key) ? <CardDescription>{moduleFieldLabels.get(key)}</CardDescription> : null}
              </CardHeader>
              <CardContent>
                <Textarea 
                  value={template}
                  onChange={(e) => setPrompts({
                    ...prompts,
                    modules: { ...prompts.modules, [key]: e.target.value }
                  })}
                  className="min-h-[150px] font-mono text-sm"
                />
              </CardContent>
            </Card>
          ))}
        </TabsContent>

        <TabsContent value="final" className="space-y-4 mt-4">
          {Object.entries(prompts.finalTemplates).length > 0 ? (
            Object.entries(prompts.finalTemplates).map(([key, template]) => (
              <Card key={key}>
                <CardHeader>
                  <CardTitle>{moduleLabels.get(key) ?? key}</CardTitle>
                </CardHeader>
                <CardContent>
                  <Textarea
                    value={template}
                    onChange={(e) => setPrompts({
                      ...prompts,
                      finalTemplates: { ...prompts.finalTemplates, [key]: e.target.value }
                    })}
                    className="min-h-[150px] font-mono text-sm"
                  />
                </CardContent>
              </Card>
            ))
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>{t("worldPrompts.finalTemplateTitle")}</CardTitle>
                <CardDescription>{t("worldPrompts.finalTemplateDesc")}</CardDescription>
              </CardHeader>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="refine" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{t("worldPrompts.fieldRefineTitle")}</CardTitle>
              <CardDescription>{t("worldPrompts.fieldRefineDesc")}</CardDescription>
            </CardHeader>
            <CardContent>
              <Textarea 
                value={prompts.fieldRefine}
                onChange={(e) => setPrompts({ ...prompts, fieldRefine: e.target.value })}
                className="min-h-[150px] font-mono text-sm"
              />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <div className="sticky bottom-6 flex justify-end bg-background/80 backdrop-blur p-4 border rounded-lg shadow-lg">
        <Button onClick={handleSave} disabled={isSaving} size="lg">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
          {t("worldPrompts.saveAll")}
        </Button>
      </div>
    </div>
  );
};

export default WorldPrompts;
