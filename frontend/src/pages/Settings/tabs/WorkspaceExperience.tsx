import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/components/ui/use-toast";
import { Textarea } from "@/components/ui/textarea";
import { DEFAULT_SHORTCUTS, detectShortcutConflicts } from "@/lib/shortcuts";
import { localizedErrorMessage } from "@/lib/error-messages";

const WorkspaceExperience = () => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [layouts, setLayouts] = useState<any[]>([]);
  const [shortcuts, setShortcuts] = useState<any[]>([]);
  const [goals, setGoals] = useState<any[]>([]);
  const [stories, setStories] = useState<Story[]>([]);

  const [layoutName, setLayoutName] = useState(t("workspaceExperience.defaultLayoutName"));
  const [layoutJson, setLayoutJson] = useState('{"left":["outline"],"right":["copilot","context","version"]}');
  const [goalStoryId, setGoalStoryId] = useState("");
  const [goalType, setGoalType] = useState("daily_words");
  const [goalTarget, setGoalTarget] = useState(2000);
  const [shortcutAction, setShortcutAction] = useState("focus_mode");
  const [shortcutKey, setShortcutKey] = useState("Ctrl+Shift+F");
  const [shortcutConflicts, setShortcutConflicts] = useState<Array<{ shortcut: string; actions: string[] }>>([]);

  const loadData = async () => {
    const [layoutList, shortcutList, goalList, storyList] = await Promise.all([
      api.v2.workspace.listLayouts(),
      api.v2.workspace.listShortcuts(),
      api.v2.workspace.listGoals(),
      api.stories.list(),
    ]);
    setLayouts(layoutList);
    setShortcuts(shortcutList);
    setShortcutConflicts(detectShortcutConflicts(shortcutList.map((item) => ({ action: item.action, shortcut: item.shortcut }))));
    setGoals(goalList);
    setStories(storyList);
    if (!goalStoryId && storyList.length > 0) {
      setGoalStoryId(storyList[0].id);
    }
  };

  useEffect(() => {
    loadData().catch((error: any) => toast({ variant: "destructive", title: t("workspaceExperience.loadFailed"), description: localizedErrorMessage(error, "workspaceExperience.loadFailed") }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const createLayout = async () => {
    try {
      const parsed = JSON.parse(layoutJson);
      await api.v2.workspace.createLayout({ name: layoutName, layout: parsed, isActive: true });
      await loadData();
      toast({ title: t("workspaceExperience.layoutCreated") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("workspaceExperience.layoutCreateFailed"), description: localizedErrorMessage(error, "workspaceExperience.layoutCreateFailed") });
    }
  };

  const updateShortcut = async () => {
    const candidateList = shortcuts
      .filter((item) => item.action !== shortcutAction)
      .map((item) => ({ action: item.action, shortcut: item.shortcut }))
      .concat([{ action: shortcutAction, shortcut: shortcutKey }]);
    const conflicts = detectShortcutConflicts(candidateList);
    if (conflicts.some((item) => item.actions.includes(shortcutAction))) {
      toast({
        variant: "destructive",
        title: t("workspaceExperience.shortcutConflict"),
        description: t("workspaceExperience.shortcutConflictDesc", { detail: conflicts.map((item) => `${item.shortcut} -> ${item.actions.join(",")}`).join("; ") }),
      });
      return;
    }
    try {
      await api.v2.workspace.updateShortcuts([{ action: shortcutAction, shortcut: shortcutKey }]);
      await loadData();
      window.dispatchEvent(new Event("ainovel-shortcuts-updated"));
      toast({ title: t("workspaceExperience.shortcutUpdated") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("workspaceExperience.shortcutUpdateFailed"), description: localizedErrorMessage(error, "workspaceExperience.shortcutUpdateFailed") });
    }
  };

  const createGoal = async () => {
    try {
      await api.v2.workspace.createGoal({
        storyId: goalStoryId || null,
        goalType,
        targetValue: goalTarget,
        currentValue: 0,
        status: "active",
      });
      await loadData();
      toast({ title: t("workspaceExperience.goalCreated") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("workspaceExperience.goalCreateFailed"), description: localizedErrorMessage(error, "workspaceExperience.goalCreateFailed") });
    }
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("workspaceExperience.layoutTitle")}</CardTitle>
            <CardDescription>{t("workspaceExperience.layoutDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-2">
              <Label>{t("workspaceExperience.layoutName")}</Label>
              <Input value={layoutName} onChange={(event) => setLayoutName(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t("workspaceExperience.layoutJson")}</Label>
              <Textarea className="min-h-[100px] font-mono text-xs" value={layoutJson} onChange={(event) => setLayoutJson(event.target.value)} />
            </div>
            <Button onClick={createLayout}>{t("workspaceExperience.createActivateLayout")}</Button>
            <div className="space-y-2">
              {layouts.map((layout) => (
                <div key={layout.id} className="rounded border p-2 flex items-center justify-between text-sm">
                  <div>
                    <div className="font-medium">{layout.name}</div>
                    <div className="text-muted-foreground">{layout.isActive ? t("workspaceExperience.active") : t("workspaceExperience.inactive")}</div>
                  </div>
                  <div className="flex gap-2">
                    <Button size="sm" variant="outline" onClick={() => api.v2.workspace.activateLayout(layout.id).then(loadData)}>
                      {t("workspaceExperience.activate")}
                    </Button>
                    <Button size="sm" variant="destructive" onClick={() => api.v2.workspace.deleteLayout(layout.id).then(loadData)}>
                      {t("common.delete")}
                    </Button>
                  </div>
                </div>
              ))}
              {!layouts.length && <p className="text-sm text-muted-foreground">{t("workspaceExperience.noLayouts")}</p>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("workspaceExperience.shortcutTitle")}</CardTitle>
            <CardDescription>{t("workspaceExperience.shortcutDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid md:grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>{t("workspaceExperience.action")}</Label>
                <Select value={shortcutAction} onValueChange={setShortcutAction}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.keys(DEFAULT_SHORTCUTS).map((action) => (
                      <SelectItem key={action} value={action}>
                        {action}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t("workspaceExperience.shortcut")}</Label>
                <Input value={shortcutKey} onChange={(event) => setShortcutKey(event.target.value)} />
              </div>
            </div>
            {!!shortcutConflicts.length && (
              <div className="rounded border border-amber-400 bg-amber-50 px-2 py-1 text-xs text-amber-700">
                {t("workspaceExperience.conflictDetected", { detail: shortcutConflicts.map((item) => `${item.shortcut}(${item.actions.join(",")})`).join("；") })}
              </div>
            )}
            <Button onClick={updateShortcut}>{t("workspaceExperience.updateShortcut")}</Button>
            <div className="space-y-2">
              {shortcuts.map((shortcut) => (
                <div key={shortcut.id} className="rounded border p-2 text-sm flex items-center justify-between">
                  <span>{shortcut.action}</span>
                  <span className="font-mono">{shortcut.shortcut}</span>
                </div>
              ))}
              {!shortcuts.length && <p className="text-sm text-muted-foreground">{t("workspaceExperience.noShortcuts")}</p>}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("workspaceExperience.goalTitle")}</CardTitle>
          <CardDescription>{t("workspaceExperience.goalDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-3">
            <div className="space-y-2">
              <Label>{t("workspaceExperience.story")}</Label>
              <Select value={goalStoryId} onValueChange={setGoalStoryId}>
                <SelectTrigger>
                  <SelectValue placeholder={t("common.selectStory")} />
                </SelectTrigger>
                <SelectContent>
                  {stories.map((story) => (
                    <SelectItem key={story.id} value={story.id}>
                      {story.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t("workspaceExperience.goalType")}</Label>
              <Input value={goalType} onChange={(event) => setGoalType(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t("workspaceExperience.goalTarget")}</Label>
              <Input type="number" value={goalTarget} onChange={(event) => setGoalTarget(Number(event.target.value || 0))} />
            </div>
          </div>
          <Button onClick={createGoal}>{t("workspaceExperience.createGoal")}</Button>
          <div className="space-y-2">
            {goals.map((goal) => (
              <div key={goal.id} className="rounded border p-2 text-sm flex items-center justify-between">
                <div>
                  <div className="font-medium">{goal.goalType}</div>
                  <div className="text-muted-foreground">
                    {goal.currentValue}/{goal.targetValue}
                  </div>
                </div>
                <Button size="sm" variant="destructive" onClick={() => api.v2.workspace.deleteGoal(goal.id).then(loadData)}>
                  {t("common.delete")}
                </Button>
              </div>
            ))}
            {!goals.length && <p className="text-sm text-muted-foreground">{t("workspaceExperience.noGoals")}</p>}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default WorkspaceExperience;
