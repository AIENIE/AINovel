import { useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { localizedErrorMessage } from "@/lib/error-messages";

const V2Studio = () => {
  const { t } = useTranslation();
  const [storyId, setStoryId] = useState("");
  const [manuscriptId, setManuscriptId] = useState("");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [entryName, setEntryName] = useState("");
  const [entryContent, setEntryContent] = useState("");
  const [output, setOutput] = useState("{}");

  const ensureStoryId = () => {
    if (!storyId.trim()) {
      throw new Error(t("v2Studio.storyIdRequired"));
    }
  };

  const ensureManuscriptId = () => {
    if (!manuscriptId.trim()) {
      throw new Error(t("v2Studio.manuscriptIdRequired"));
    }
  };

  const run = async (labelKey: string, action: () => Promise<any>) => {
    setBusy(true);
    try {
      const data = await action();
      setOutput(JSON.stringify({ action: t(labelKey), time: new Date().toISOString(), data }, null, 2));
    } catch (error) {
      const message = localizedErrorMessage(error, "errors.operationFailed");
      setOutput(JSON.stringify({ action: t(labelKey), error: message }, null, 2));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>{t("v2Studio.title")}</CardTitle>
          <CardDescription>{t("v2Studio.desc")}</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="storyId">Story ID</Label>
            <Input id="storyId" value={storyId} onChange={(e) => setStoryId(e.target.value)} placeholder={t("v2Studio.storyIdPlaceholder")} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="manuscriptId">Manuscript ID</Label>
            <Input
              id="manuscriptId"
              value={manuscriptId}
              onChange={(e) => setManuscriptId(e.target.value)}
              placeholder={t("v2Studio.manuscriptIdPlaceholder")}
            />
          </div>
          <div className="space-y-2 md:col-span-2">
            <Label htmlFor="entryName">{t("v2Studio.quickEntry")}</Label>
            <Input id="entryName" value={entryName} onChange={(e) => setEntryName(e.target.value)} placeholder={t("v2Studio.entryTitlePlaceholder")} />
            <Textarea value={entryContent} onChange={(e) => setEntryContent(e.target.value)} className="min-h-[90px]" />
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{t("v2Studio.contextMemory")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={busy}
              onClick={() =>
                run("v2Studio.createLorebook", async () => {
                  ensureStoryId();
                  return api.v2.context.createLorebook(storyId, {
                    displayName: entryName,
                    content: entryContent,
                    priority: 10,
                    tokenBudget: 220,
                    category: "character",
                  });
                })
              }
            >
              {t("v2Studio.newEntry")}
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("v2Studio.readLorebook", async () => {
              ensureStoryId();
              return api.v2.context.listLorebook(storyId);
            })}>
              {t("common.list")}
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("v2Studio.contextPreview", async () => {
              ensureStoryId();
              return api.v2.context.previewContext(storyId, { tokenBudget: 600 });
            })}>
              {t("common.preview")}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("v2Studio.styleVoice")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("v2Studio.createProfile", async () => {
              ensureStoryId();
              return api.v2.style.createProfile(storyId, {
                name: entryName || "Profile",
                profileType: "narrative",
                dimensions: { rhythm: 80, imagery: 75 },
              });
            })}>
              {t("v2Studio.newProfile")}
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("v2Studio.profileList", async () => {
              ensureStoryId();
              return api.v2.style.listProfiles(storyId);
            })}>
              {t("common.list")}
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("v2Studio.styleAnalysis", async () => {
              return api.v2.style.analyze({ sourceType: "uploaded_text", sourceReference: "workbench", sampleText: entryContent });
            })}>
              {t("v2Studio.styleAnalysis")}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center gap-2">
              <CardTitle>Beta Reader</CardTitle>
              <Badge variant="secondary">{t("common.notImplemented")}</Badge>
            </div>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled title={t("common.notImplemented")}>
              {t("v2Studio.triggerAnalysis")} · {t("common.notImplemented")}
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("v2Studio.analysisJobs", async () => {
              ensureStoryId();
              return api.v2.analysis.listJobs(storyId);
            })}>
              {t("common.taskList")}
            </Button>
            <Button size="sm" variant="outline" disabled title={t("common.notImplemented")}>
              {t("v2Studio.continuityCheck")} · {t("common.notImplemented")}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("v2Studio.versionControl")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("v2Studio.createVersion", async () => {
              ensureManuscriptId();
              return api.v2.version.createVersion(manuscriptId, { label: `checkpoint-${Date.now()}` });
            })}>
              {t("v2Studio.newSnapshot")}
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("v2Studio.versionList", async () => {
              ensureManuscriptId();
              return api.v2.version.listVersions(manuscriptId);
            })}>
              {t("v2Studio.versionList")}
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("v2Studio.autoSave", async () => {
              return api.v2.version.getAutoSave();
            })}>
              {t("v2Studio.autoSave")}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("v2Studio.exportSystem")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("v2Studio.createExport", async () => {
              ensureManuscriptId();
              return api.v2.export.createJob(manuscriptId, { format: "txt", config: { lineSpacing: 1.5 } });
            })}>
              {t("v2Studio.startExport")}
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("v2Studio.exportJobs", async () => {
              ensureManuscriptId();
              return api.v2.export.listJobs(manuscriptId);
            })}>
              {t("common.taskList")}
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("v2Studio.templateList", async () => api.v2.export.listTemplates())}>
              {t("v2Studio.templateList")}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("v2Studio.modelsWorkspace")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("v2Studio.modelList", async () => api.v2.models.list())}>
              {t("v2Studio.modelList")}
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("v2Studio.modelCompare", async () => {
              ensureStoryId();
              return api.v2.models.compare(storyId, { taskType: "draft_generation", prompt: entryContent });
            })}>
              {t("v2Studio.modelCompare")}
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("v2Studio.newLayout", async () => {
              return api.v2.workspace.createLayout({
                name: `${t("v2Studio.layoutPrefix")}-${new Date().toLocaleTimeString()}`,
                isActive: true,
                layout: { left: ["outline", "lorebook"], right: ["copilot", "analysis"] },
              });
            })}>
              {t("v2Studio.newLayout")}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => run("v2Studio.sessionToggle", async () => {
              ensureStoryId();
              if (!sessionId) {
                const started = await api.v2.workspace.startSession({ storyId, wordsWritten: 0, wordsDeleted: 0 });
                setSessionId(started.id);
                return started;
              }
              const ended = await api.v2.workspace.endSession(sessionId, { wordsWritten: 320, wordsDeleted: 35 });
              setSessionId(null);
              return ended;
            })}>
              {sessionId ? t("v2Studio.endSession") : t("v2Studio.startSession")}
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("v2Studio.apiOutput")}</CardTitle>
          <CardDescription>{t("v2Studio.apiOutputDesc")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea value={output} readOnly className="min-h-[260px] font-mono text-xs" />
        </CardContent>
      </Card>
    </div>
  );
};

export default V2Studio;
