import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { PromptMetadata, PromptTemplates } from "@/types";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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

const WorkspacePrompts = () => {
  const [prompts, setPrompts] = useState<PromptTemplates | null>(null);
  const [metadata, setMetadata] = useState<PromptMetadata | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const { toast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    Promise.all([api.prompts.getWorkspace(), api.prompts.getWorkspaceMetadata()]).then(([templates, nextMetadata]) => {
      setPrompts(templates);
      setMetadata(nextMetadata);
    });
  }, []);

  const handleSave = async () => {
    if (!prompts) return;
    setIsSaving(true);
    try {
      await api.prompts.updateWorkspace(prompts);
      toast({ title: t("workspacePrompts.saved") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = async () => {
    setIsResetting(true);
    try {
      const defaults = await api.prompts.resetWorkspace();
      setPrompts(defaults);
      toast({ title: t("workspacePrompts.resetDone") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("workspacePrompts.resetFailed"), description: localizedErrorMessage(error, "workspacePrompts.resetFailed") });
    } finally {
      setIsResetting(false);
    }
  };

  if (!prompts) return <div>{t("common.loading")}</div>;

  const templateCards: Array<{ key: keyof PromptTemplates; titleKey: string; descriptionKey: string }> = [
    { key: "storyCreation", titleKey: "workspacePrompts.storyCreation", descriptionKey: "workspacePrompts.storyCreationDesc" },
    { key: "outlineChapter", titleKey: "workspacePrompts.outlineChapter", descriptionKey: "workspacePrompts.outlineChapterDesc" },
    { key: "manuscriptSection", titleKey: "workspacePrompts.manuscriptSection", descriptionKey: "workspacePrompts.manuscriptSectionDesc" },
    { key: "refineWithInstruction", titleKey: "workspacePrompts.refineWithInstruction", descriptionKey: "workspacePrompts.refineWithInstructionDesc" },
    { key: "refineWithoutInstruction", titleKey: "workspacePrompts.refineWithoutInstruction", descriptionKey: "workspacePrompts.refineWithoutInstructionDesc" },
  ];

  const variablesByTemplate = new Map(metadata?.templates.map((item) => [item.key, item.variables]) ?? []);

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button variant="outline" size="sm" asChild>
          <Link to="/settings/prompt-guide">
            <HelpCircle className="mr-2 h-4 w-4" />
            {t("workspacePrompts.viewVariables")}
          </Link>
        </Button>
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="outline" size="sm" disabled={isResetting}>
              {isResetting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RotateCcw className="mr-2 h-4 w-4" />}
              {t("workspacePrompts.restoreDefault")}
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{t("workspacePrompts.resetTitle")}</AlertDialogTitle>
              <AlertDialogDescription>{t("workspacePrompts.resetDesc")}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
              <AlertDialogAction onClick={handleReset}>{t("workspacePrompts.restoreDefault")}</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>

      {templateCards.map((card) => (
        <Card key={card.key}>
          <CardHeader>
            <CardTitle>{t(card.titleKey)}</CardTitle>
            <CardDescription>{t(card.descriptionKey)}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Textarea
              value={prompts[card.key]}
              onChange={(e) => setPrompts({ ...prompts, [card.key]: e.target.value })}
              className="min-h-[150px] font-mono text-sm"
            />
            {(variablesByTemplate.get(card.key) ?? []).length > 0 ? (
              <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                {(variablesByTemplate.get(card.key) ?? []).map((variable) => (
                  <span key={variable.name} className="rounded border px-2 py-1 font-mono text-foreground">
                    {"{"}{variable.name}{"}"}
                  </span>
                ))}
              </div>
            ) : null}
          </CardContent>
        </Card>
      ))}

      <div className="sticky bottom-6 flex justify-end bg-background/80 backdrop-blur p-4 border rounded-lg shadow-lg">
        <Button onClick={handleSave} disabled={isSaving} size="lg">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
          {t("workspacePrompts.saveAll")}
        </Button>
      </div>
    </div>
  );
};

export default WorkspacePrompts;
