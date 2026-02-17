import { useState } from "react";
import { api } from "@/lib/mock-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

const V2Studio = () => {
  const [storyId, setStoryId] = useState("");
  const [manuscriptId, setManuscriptId] = useState("");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [entryName, setEntryName] = useState("主角设定");
  const [entryContent, setEntryContent] = useState("主角在第一章失去记忆，需要通过线索逐步恢复身份。");
  const [output, setOutput] = useState("{}");

  const ensureStoryId = () => {
    if (!storyId.trim()) {
      throw new Error("请先填写 Story ID");
    }
  };

  const ensureManuscriptId = () => {
    if (!manuscriptId.trim()) {
      throw new Error("请先填写 Manuscript ID");
    }
  };

  const run = async (label: string, action: () => Promise<any>) => {
    setBusy(true);
    try {
      const data = await action();
      setOutput(JSON.stringify({ action: label, time: new Date().toISOString(), data }, null, 2));
    } catch (error) {
      const message = error instanceof Error ? error.message : "未知错误";
      setOutput(JSON.stringify({ action: label, error: message }, null, 2));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>v2 工作台联调面板</CardTitle>
          <CardDescription>按 design-doc/v2 对应接口做关键路径联调与结果观察。</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="storyId">Story ID</Label>
            <Input id="storyId" value={storyId} onChange={(e) => setStoryId(e.target.value)} placeholder="填入故事 ID" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="manuscriptId">Manuscript ID</Label>
            <Input
              id="manuscriptId"
              value={manuscriptId}
              onChange={(e) => setManuscriptId(e.target.value)}
              placeholder="填入稿件 ID"
            />
          </div>
          <div className="space-y-2 md:col-span-2">
            <Label htmlFor="entryName">Lorebook 快速条目</Label>
            <Input id="entryName" value={entryName} onChange={(e) => setEntryName(e.target.value)} placeholder="条目标题" />
            <Textarea value={entryContent} onChange={(e) => setEntryContent(e.target.value)} className="min-h-[90px]" />
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>上下文记忆</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={busy}
              onClick={() =>
                run("创建 Lorebook", async () => {
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
              新建条目
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("读取 Lorebook", async () => {
              ensureStoryId();
              return api.v2.context.listLorebook(storyId);
            })}>
              列表
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("上下文预览", async () => {
              ensureStoryId();
              return api.v2.context.previewContext(storyId, 600);
            })}>
              预览
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>风格与角色声音</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("创建风格画像", async () => {
              ensureStoryId();
              return api.v2.style.createProfile(storyId, {
                name: "叙事冷峻风",
                profileType: "narrative",
                dimensions: { rhythm: 80, imagery: 75 },
              });
            })}>
              新建画像
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("画像列表", async () => {
              ensureStoryId();
              return api.v2.style.listProfiles(storyId);
            })}>
              列表
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("风格分析", async () => {
              return api.v2.style.analyze({ sourceType: "uploaded_text", sourceReference: "workbench", sampleText: entryContent });
            })}>
              风格分析
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Beta Reader</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("触发 Beta Reader", async () => {
              ensureStoryId();
              return api.v2.analysis.triggerBetaReader(storyId, { scope: "full" });
            })}>
              触发分析
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("查询分析任务", async () => {
              ensureStoryId();
              return api.v2.analysis.listJobs(storyId);
            })}>
              任务列表
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("连续性检查", async () => {
              ensureStoryId();
              return api.v2.analysis.triggerContinuity(storyId, { text: entryContent });
            })}>
              连续性检查
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>版本控制</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("创建版本", async () => {
              ensureManuscriptId();
              return api.v2.version.createVersion(manuscriptId, { label: `checkpoint-${Date.now()}` });
            })}>
              新建快照
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("版本列表", async () => {
              ensureManuscriptId();
              return api.v2.version.listVersions(manuscriptId);
            })}>
              版本列表
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("自动保存配置", async () => {
              return api.v2.version.getAutoSave();
            })}>
              自动保存
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>导出系统</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("创建导出任务", async () => {
              ensureManuscriptId();
              return api.v2.export.createJob(manuscriptId, { format: "txt", config: { lineSpacing: 1.5 } });
            })}>
              发起导出
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("导出任务列表", async () => {
              ensureManuscriptId();
              return api.v2.export.listJobs(manuscriptId);
            })}>
              任务列表
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("模板列表", async () => api.v2.export.listTemplates())}>
              模板列表
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>多模型与工作台</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => run("模型列表", async () => api.v2.models.list())}>
              模型列表
            </Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => run("模型对比", async () => {
              ensureStoryId();
              return api.v2.models.compare(storyId, { taskType: "draft_generation", prompt: entryContent });
            })}>
              模型对比
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => run("新建布局", async () => {
              return api.v2.workspace.createLayout({
                name: `写作布局-${new Date().toLocaleTimeString()}`,
                isActive: true,
                layout: { left: ["outline", "lorebook"], right: ["copilot", "analysis"] },
              });
            })}>
              新建布局
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => run("会话开始/结束", async () => {
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
              {sessionId ? "结束会话" : "开始会话"}
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>接口返回</CardTitle>
          <CardDescription>最近一次操作结果（便于联调排错）。</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea value={output} readOnly className="min-h-[260px] font-mono text-xs" />
        </CardContent>
      </Card>
    </div>
  );
};

export default V2Studio;
