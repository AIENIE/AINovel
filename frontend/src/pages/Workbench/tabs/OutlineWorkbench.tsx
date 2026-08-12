import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { ChapterPlanning, Outline, ScenePlanning, Story, TwistOption, World } from "@/types";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { ChevronDown, FileText, Plus, Save, Sparkles, Trash2, X } from "lucide-react";
import { localizedErrorMessage } from "@/lib/error-messages";

interface OutlineWorkbenchProps {
  initialStoryId?: string;
}

type SelectedNode = { type: "chapter" | "scene"; id: string } | null;
const NO_FORESHADOW_VALUE = "__none__";

const ensureUuid = () => {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
    const rand = Math.floor(Math.random() * 16);
    const value = char === "x" ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
};

const chapterDefaults = (twistId = ""): ChapterPlanning => ({
  selectedTwistId: twistId,
  revealFocus: "",
  tensionShift: "",
});

const sceneDefaults = (twistId = ""): ScenePlanning => ({
  goal: "",
  conflict: "",
  infoRelease: "",
  foreshadowId: "",
  revealFor: twistId,
  memeUsage: "",
});

const OutlineWorkbench = ({ initialStoryId }: OutlineWorkbenchProps) => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [stories, setStories] = useState<Story[]>([]);
  const [selectedStoryId, setSelectedStoryId] = useState("");
  const [outlines, setOutlines] = useState<Outline[]>([]);
  const [worlds, setWorlds] = useState<World[]>([]);
  const [selectedOutline, setSelectedOutline] = useState<Outline | null>(null);
  const [selectedNode, setSelectedNode] = useState<SelectedNode>(null);
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [chapterPlanning, setChapterPlanning] = useState<ChapterPlanning>(chapterDefaults());
  const [scenePlanning, setScenePlanning] = useState<ScenePlanning>(sceneDefaults());
  const [isSaving, setIsSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState("");
  const [creatingOutline, setCreatingOutline] = useState(false);
  const [newOutlineName, setNewOutlineName] = useState(t("outline.newOutline"));

  useEffect(() => { api.worlds.list().then((items) => setWorlds(items.filter((world) => world.status === "active"))).catch(() => setWorlds([])); }, []);

  useEffect(() => {
    api.stories
      .list()
      .then((data) => {
        setStories(data);
        if (initialStoryId && data.some((story) => story.id === initialStoryId)) {
          setSelectedStoryId(initialStoryId);
        } else if (data.length > 0) {
          setSelectedStoryId(data[0].id);
        }
      })
      .catch((error: unknown) => {
        toast({ variant: "destructive", title: t("errors.loadStoriesFailed"), description: localizedErrorMessage(error, "errors.loadStoriesFailed") });
      });
  }, [initialStoryId, toast, t]);

  useEffect(() => {
    if (!selectedStoryId) return;
    api.outlines
      .listByStory(selectedStoryId)
      .then((data) => {
        setOutlines(data);
        setSelectedOutline(data[0] || null);
        setSelectedNode(null);
      })
      .catch((error: unknown) => {
        toast({ variant: "destructive", title: t("errors.loadOutlinesFailed"), description: localizedErrorMessage(error, "errors.loadOutlinesFailed") });
      });
  }, [selectedStoryId, toast, t]);

  const planning = selectedOutline?.planning;
  const twistOptions = useMemo(() => planning?.twistOptions || [], [planning]);
  const foreshadowPlans = useMemo(() => planning?.foreshadowPlans || [], [planning]);
  const activeTwistId = selectedOutline?.activeTwistId || planning?.selectedTwistId || twistOptions[0]?.id || "";
  const activeTwist = twistOptions.find((item) => item.id === activeTwistId) || twistOptions[0];

  useEffect(() => {
    if (!selectedOutline || !selectedNode) {
      setTitle("");
      setSummary("");
      setChapterPlanning(chapterDefaults(activeTwistId));
      setScenePlanning(sceneDefaults(activeTwistId));
      return;
    }
    if (selectedNode.type === "chapter") {
      const chapter = selectedOutline.chapters.find((item) => item.id === selectedNode.id);
      setTitle(chapter?.title || "");
      setSummary(chapter?.summary || "");
      setChapterPlanning({ ...chapterDefaults(activeTwistId), ...(chapter?.planning || {}) });
      setScenePlanning(sceneDefaults(activeTwistId));
      return;
    }
    for (const chapter of selectedOutline.chapters) {
      const scene = chapter.scenes.find((item) => item.id === selectedNode.id);
      if (scene) {
        setTitle(scene.title || "");
        setSummary(scene.summary || "");
        setScenePlanning({ ...sceneDefaults(activeTwistId), ...(scene.planning || {}) });
        setChapterPlanning(chapterDefaults(activeTwistId));
        return;
      }
    }
  }, [selectedNode, selectedOutline, activeTwistId]);

  const applyEdits = () => {
    if (!selectedOutline) return selectedOutline;
    const nextPlanning = planning ? { ...planning, selectedTwistId: activeTwistId } : planning;
    const chapters = selectedOutline.chapters.map((chapter) => {
      if (selectedNode?.type === "chapter" && chapter.id === selectedNode.id) {
        return {
          ...chapter,
          title,
          summary,
          planning: { ...chapterDefaults(activeTwistId), ...(chapter.planning || {}), ...chapterPlanning, selectedTwistId: activeTwistId },
        };
      }
      if (selectedNode?.type === "scene") {
        return {
          ...chapter,
          scenes: chapter.scenes.map((scene) =>
            scene.id === selectedNode.id
              ? {
                  ...scene,
                  title,
                  summary,
                  planning: { ...sceneDefaults(activeTwistId), ...(scene.planning || {}), ...scenePlanning, revealFor: activeTwistId },
                }
              : scene,
          ),
        };
      }
      return chapter;
    });
    return { ...selectedOutline, chapters, planning: nextPlanning, activeTwistId };
  };

  const handleSave = async () => {
    if (!selectedOutline) return;
    const nextOutline = applyEdits();
    if (!nextOutline) return;
    setIsSaving(true);
    setSaveStatus("");
    try {
      const saved = await api.outlines.save(selectedOutline.id, nextOutline as Outline & { worldId?: string });
      setSelectedOutline(saved);
      setOutlines((prev) => prev.map((item) => (item.id === saved.id ? saved : item)));
      setSaveStatus(t("outline.saved"));
      toast({ title: t("outline.structureSaved") });
    } catch (error: unknown) {
      setSaveStatus(t("errors.saveFailed"));
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
    } finally {
      setIsSaving(false);
    }
  };

  const handleCreateOutline = async () => {
    if (!selectedStoryId) return;
    try {
      const created = await api.outlines.create(selectedStoryId, { title: newOutlineName.trim() || t("outline.newOutline"), planning });
      setOutlines((prev) => [created, ...prev]);
      setSelectedOutline(created);
      setSelectedNode(null);
      setCreatingOutline(false);
      toast({ title: t("outline.created") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.createFailed"), description: localizedErrorMessage(error, "errors.createFailed") });
    }
  };

  const handleDeleteOutline = async () => {
    if (!selectedOutline || !confirm(t("outline.deleteConfirm", { title: selectedOutline.title }))) return;
    try {
      await api.outlines.delete(selectedOutline.id);
      const next = outlines.filter((outline) => outline.id !== selectedOutline.id);
      setOutlines(next); setSelectedOutline(next[0] || null); setSelectedNode(null);
      toast({ title: t("outline.deleted") });
    } catch (error: any) { toast({ variant: "destructive", title: t("errors.deleteFailed"), description: localizedErrorMessage(error, "errors.deleteFailed") }); }
  };

  const handleGenerateNextChapter = async () => {
    if (!selectedOutline) return;
    const chapterNumber = selectedOutline.chapters.length + 1;
    const worldName = worlds.find((world) => world.id === selectedOutline.worldId)?.name || t("outline.defaultWorld");
    if (!confirm(t("outline.generateChapterConfirm", { chapterNumber, worldName }))) return;
    try {
      await runTrackedAiOperation(api.outlines.startGenerateChapter(selectedOutline.id, { chapterNumber, sectionsPerChapter: 3, wordsPerSection: 2000, worldId: selectedOutline.worldId || null }));
      const refreshed = await api.outlines.get(selectedOutline.id);
      setOutlines((current) => current.map((outline) => outline.id === refreshed.id ? refreshed : outline));
      setSelectedOutline(refreshed);
      toast({ title: t("outline.chapterGenerated", { chapterNumber }) });
    } catch (error: any) { toast({ variant: "destructive", title: t("errors.generateChapterFailed"), description: localizedErrorMessage(error, "errors.generateChapterFailed") }); }
  };

  const handleAddChapter = () => {
    if (!selectedOutline) return;
    const nextChapter: Outline["chapters"][number] = {
      id: ensureUuid(),
      title: t("outline.newChapter"),
      summary: "",
      planning: chapterDefaults(activeTwistId),
      scenes: [],
    };
    setSelectedOutline({ ...selectedOutline, chapters: [...selectedOutline.chapters, nextChapter] });
    setSelectedNode({ type: "chapter", id: nextChapter.id });
  };

  const handleAddScene = () => {
    if (!selectedOutline || !selectedNode || selectedNode.type !== "chapter") return;
    setSelectedOutline({
      ...selectedOutline,
      chapters: selectedOutline.chapters.map((chapter) =>
        chapter.id === selectedNode.id
          ? {
              ...chapter,
              scenes: [...chapter.scenes, { id: ensureUuid(), title: t("outline.newScene"), summary: "", content: "", planning: sceneDefaults(activeTwistId) }],
            }
          : chapter,
      ),
    });
  };

  const handleSelectTwist = useCallback((twistId: string) => {
    if (!selectedOutline) return;
    setSelectedOutline({
      ...selectedOutline,
      activeTwistId: twistId,
      planning: selectedOutline.planning ? { ...selectedOutline.planning, selectedTwistId: twistId } : selectedOutline.planning,
    });
    setChapterPlanning((current) => ({ ...current, selectedTwistId: twistId }));
    setScenePlanning((current) => ({ ...current, revealFor: twistId }));
  }, [selectedOutline]);

  const handleAiRefine = async () => {
    if (!summary.trim()) return;
    try {
      const models = await api.ai.getModels();
      const modelId = models[0]?.id;
      if (!modelId) throw new Error(t("outline.noAiModel"));
      const response = await api.ai.refine(
        summary,
        selectedNode?.type === "scene"
          ? t("outline.refineScenePrompt")
          : t("outline.refineChapterPrompt"),
        modelId,
      );
      setSummary(response.result);
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("outline.aiRefineFailed"), description: localizedErrorMessage(error, "outline.aiRefineFailed") });
    }
  };

  const overview = useMemo(() => {
    if (!planning) return null;
    return (
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("outline.structureOverview")}</CardTitle>
            <CardDescription>{t("outline.structureOverviewDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">{t("outline.corePromise")}</div>
              <div className="mt-1 text-sm font-medium">{planning.corePromise || t("outline.todo")}</div>
            </div>
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">{t("outline.centralQuestion")}</div>
              <div className="mt-1 text-sm font-medium">{planning.centralQuestion || t("outline.todo")}</div>
            </div>
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">{t("outline.hiddenTruth")}</div>
              <div className="mt-1 text-sm font-medium">{planning.hiddenTruth || t("outline.todo")}</div>
            </div>
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">{t("outline.readerMisdirect")}</div>
              <div className="mt-1 text-sm font-medium">{planning.readerMisdirect || t("outline.todo")}</div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("outline.twistComparison")}</CardTitle>
            <CardDescription>{t("outline.twistComparisonDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 xl:grid-cols-2">
            {twistOptions.map((twist: TwistOption) => {
              const selected = twist.id === activeTwistId;
              return (
                <div key={twist.id} className={`rounded-xl border p-4 ${selected ? "border-primary bg-primary/5" : ""}`}>
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="font-medium">{twist.label}</div>
                      <div className="text-xs text-muted-foreground">{twist.track === "structure" ? t("outline.trackStructure") : t("outline.trackInspiration")}</div>
                    </div>
                    <Button size="sm" variant={selected ? "default" : "outline"} onClick={() => handleSelectTwist(twist.id)}>
                      {selected ? t("outline.currentlyAdopted") : t("outline.switch")}
                    </Button>
                  </div>
                  <div className="mt-3 space-y-2 text-sm">
                    <div>
                      <div className="text-xs text-muted-foreground">{t("outline.hook")}</div>
                      <div>{twist.hook}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">{t("outline.hiddenTruth")}</div>
                      <div>{twist.hiddenTruth}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">{t("outline.revealTiming")}</div>
                      <div>{twist.revealTiming}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">{t("outline.payoff")}</div>
                      <div>{twist.payoff}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">{t("outline.exposureRisk")}</div>
                      <div>{twist.risk}</div>
                    </div>
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("outline.foreshadowChain")}</CardTitle>
            <CardDescription>{t("outline.foreshadowChainDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {foreshadowPlans.map((item) => (
              <div key={item.id} className="rounded-lg border p-3 text-sm">
                <div className="font-medium">{item.clue}</div>
                <div className="mt-2 grid gap-2 md:grid-cols-3">
                  <div>
                    <div className="text-xs text-muted-foreground">{t("outline.disguise")}</div>
                    <div>{item.disguise}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">{t("outline.payoff")}</div>
                    <div>{item.payoff}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">{t("outline.revealTiming")}</div>
                    <div>{item.revealTiming || t("outline.tbd")}</div>
                  </div>
                </div>
              </div>
            ))}
            {!foreshadowPlans.length ? <div className="text-sm text-muted-foreground">{t("outline.noForeshadow")}</div> : null}
          </CardContent>
        </Card>
      </div>
    );
  }, [planning, twistOptions, activeTwistId, foreshadowPlans, handleSelectTwist, t]);

  const renderNodeEditor = () => {
    if (!selectedNode) {
      return overview || <div className="h-full flex items-center justify-center text-muted-foreground">{t("outline.selectOutlineFirst")}</div>;
    }

    return (
      <div className="space-y-6 max-w-3xl">
        <div className="space-y-2">
          <Label>{t("outline.title")}</Label>
          <Input value={title} onChange={(event) => setTitle(event.target.value)} />
        </div>

        <div className="space-y-2">
          <Label>{t("outline.summary")}</Label>
          <Textarea className="min-h-[180px]" value={summary} onChange={(event) => setSummary(event.target.value)} />
        </div>

        {selectedNode.type === "chapter" ? (
          <Card>
            <CardHeader>
              <CardTitle>{t("outline.chapterStructureLabel")}</CardTitle>
              <CardDescription>{t("outline.chapterStructureDesc")}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>{t("outline.whichTwist")}</Label>
                <Select value={chapterPlanning.selectedTwistId || activeTwistId} onValueChange={(value) => setChapterPlanning((current) => ({ ...current, selectedTwistId: value }))}>
                  <SelectTrigger>
                    <SelectValue placeholder={t("outline.selectTwist")} />
                  </SelectTrigger>
                  <SelectContent>
                    {twistOptions.map((twist) => (
                      <SelectItem key={twist.id} value={twist.id}>
                        {twist.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t("outline.tensionShift")}</Label>
                <Input value={chapterPlanning.tensionShift || ""} onChange={(event) => setChapterPlanning((current) => ({ ...current, tensionShift: event.target.value }))} placeholder={t("outline.tensionShiftPlaceholder")} />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>{t("outline.revealFocus")}</Label>
                <Textarea value={chapterPlanning.revealFocus || ""} onChange={(event) => setChapterPlanning((current) => ({ ...current, revealFocus: event.target.value }))} placeholder={t("outline.revealFocusPlaceholder")} />
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>{t("outline.sceneStructureLabel")}</CardTitle>
              <CardDescription>{t("outline.sceneStructureDesc")}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>{t("outline.sceneGoal")}</Label>
                <Input value={scenePlanning.goal || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, goal: event.target.value }))} placeholder={t("outline.sceneGoalPlaceholder")} />
              </div>
              <div className="space-y-2">
                <Label>{t("outline.sceneConflict")}</Label>
                <Input value={scenePlanning.conflict || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, conflict: event.target.value }))} placeholder={t("outline.sceneConflictPlaceholder")} />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>{t("outline.sceneInfoRelease")}</Label>
                <Textarea value={scenePlanning.infoRelease || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, infoRelease: event.target.value }))} placeholder={t("outline.sceneInfoReleasePlaceholder")} />
              </div>
              <div className="space-y-2">
                <Label>{t("outline.relatedForeshadow")}</Label>
                <Select
                  value={scenePlanning.foreshadowId || NO_FORESHADOW_VALUE}
                  onValueChange={(value) => setScenePlanning((current) => ({
                    ...current,
                    foreshadowId: value === NO_FORESHADOW_VALUE ? "" : value,
                  }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("outline.selectForeshadow")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NO_FORESHADOW_VALUE}>{t("outline.none")}</SelectItem>
                    {foreshadowPlans.map((item) => (
                      <SelectItem key={item.id} value={item.id}>
                        {item.clue}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t("outline.serveWhichTwist")}</Label>
                <Select value={scenePlanning.revealFor || activeTwistId} onValueChange={(value) => setScenePlanning((current) => ({ ...current, revealFor: value }))}>
                  <SelectTrigger>
                    <SelectValue placeholder={t("outline.selectTwist")} />
                  </SelectTrigger>
                  <SelectContent>
                    {twistOptions.map((twist) => (
                      <SelectItem key={twist.id} value={twist.id}>
                        {twist.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>{t("outline.memeUsage")}</Label>
                <Textarea value={scenePlanning.memeUsage || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, memeUsage: event.target.value }))} placeholder={t("outline.memeUsagePlaceholder")} />
              </div>
            </CardContent>
          </Card>
        )}

        <div className="flex justify-end gap-2">
          {selectedNode.type === "chapter" ? (
            <Button variant="outline" onClick={handleAddScene}>
              <Plus className="mr-2 h-4 w-4" />
              {t("outline.addScene")}
            </Button>
          ) : null}
          <Button variant="outline" onClick={handleAiRefine}>
            <Sparkles className="mr-2 h-4 w-4" />
            {t("outline.aiRefine")}
          </Button>
          <Button onClick={handleSave} disabled={isSaving}>
            <Save className="mr-2 h-4 w-4" />
            {isSaving ? t("common.saving") : t("outline.saveChanges")}
          </Button>
        </div>
      </div>
    );
  };

  return (
    <div className="flex min-w-0 flex-col gap-5 lg:h-[calc(100vh-200px)] lg:flex-row lg:gap-6">
      <div className="flex w-full min-w-0 flex-col gap-4 border-b pb-4 lg:w-80 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4">
        <div className="space-y-2">
          <Label>{t("common.currentStory")}</Label>
          <Select value={selectedStoryId} onValueChange={setSelectedStoryId}>
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
          <Label>{t("outline.currentOutline")}</Label>
          <div className="flex gap-1"><Select value={selectedOutline?.id || ""} onValueChange={(id) => { setSelectedOutline(outlines.find((outline) => outline.id === id) || null); setSelectedNode(null); }} disabled={!outlines.length}><SelectTrigger className="min-w-0 flex-1"><SelectValue placeholder={t("outline.noOutlineYet")} /></SelectTrigger><SelectContent>{outlines.map((outline) => <SelectItem key={outline.id} value={outline.id}>{outline.title}</SelectItem>)}</SelectContent></Select><Button size="icon" variant="outline" disabled={!selectedOutline} onClick={() => void handleDeleteOutline()}><Trash2 className="h-4 w-4" /></Button></div>
        </div>

        {selectedOutline && <div className="space-y-2"><Label>{t("outline.outlineWorld")}</Label><Select value={selectedOutline.worldId || "__default__"} onValueChange={(value) => setSelectedOutline({...selectedOutline, worldId: value === "__default__" ? undefined : value})}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="__default__">{t("outline.useDefaultWorld")}</SelectItem>{worlds.map((world) => <SelectItem key={world.id} value={world.id}>{world.name}</SelectItem>)}</SelectContent></Select></div>}

        <div className="rounded-lg border bg-muted/30 p-3">
          <div className="text-xs text-muted-foreground">{t("outline.currentPlan")}</div>
          <div className="mt-1 font-medium">{activeTwist?.label || t("outline.notSelected")}</div>
          <div className="mt-1 text-sm text-muted-foreground">{activeTwist?.payoff || t("outline.applyStructureHint")}</div>
        </div>

        <div className="flex items-center justify-between mt-2">
          <span className="text-sm font-medium text-muted-foreground">{t("outline.outlineStructure")}</span>
          <div className="flex gap-1">
            <Button size="sm" variant="ghost" className="h-6 px-2" onClick={() => setCreatingOutline(true)} disabled={!selectedStoryId}>
              <Plus className="mr-1 h-4 w-4" />
              {t("outline.newOutline")}
            </Button>
            <Button size="sm" variant="ghost" className="h-6 px-2" onClick={handleAddChapter} disabled={!selectedOutline}>
              <Plus className="mr-1 h-4 w-4" />
              {t("outline.chapter")}
            </Button>
          </div>
        </div>
        {creatingOutline ? <div className="flex gap-1"><Input value={newOutlineName} onChange={(e) => setNewOutlineName(e.target.value)} placeholder={t("outline.outlineName")} /><Button size="sm" onClick={() => void handleCreateOutline()} disabled={!newOutlineName.trim()}>{t("common.create")}</Button><Button size="icon" variant="ghost" onClick={() => setCreatingOutline(false)}><X className="h-4 w-4" /></Button></div> : null}
        <div className="flex items-center gap-2">
          <Button size="sm" onClick={() => void handleSave()} disabled={!selectedOutline || isSaving}><Save className="mr-1 h-4 w-4" />{isSaving ? t("common.saving") : t("outline.saveOutline")}</Button>
          {saveStatus ? <span role="status" aria-live="polite" className="text-xs text-muted-foreground">{saveStatus}</span> : null}
        </div>
        <Button size="sm" variant="outline" onClick={() => void handleGenerateNextChapter()} disabled={!selectedOutline}><Sparkles className="mr-1 h-4 w-4" />{t("outline.generateNextChapter")}</Button>

        <ScrollArea className="flex-1">
          {selectedOutline ? (
            <div className="space-y-1">
              <button
                type="button"
                className={`flex w-full items-center gap-2 rounded-md p-2 text-left hover:bg-accent ${selectedNode === null ? "bg-accent" : ""}`}
                onClick={() => setSelectedNode(null)}
              >
                <Sparkles className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium truncate">{t("outline.structureOverview")}</span>
              </button>
              {selectedOutline.chapters.map((chapter) => (
                <div key={chapter.id} className="space-y-1">
                  <div
                    className={`flex items-center gap-1 p-2 rounded-md cursor-pointer hover:bg-accent ${selectedNode?.id === chapter.id ? "bg-accent" : ""}`}
                    onClick={() => setSelectedNode({ type: "chapter", id: chapter.id })}
                  >
                    <ChevronDown className="h-4 w-4 text-muted-foreground" />
                    <span className="text-sm font-medium truncate">{chapter.title}</span>
                  </div>
                  <div className="pl-6 space-y-1">
                    {chapter.scenes.map((scene) => (
                      <div
                        key={scene.id}
                        className={`flex items-center gap-2 p-2 rounded-md cursor-pointer hover:bg-accent text-sm ${selectedNode?.id === scene.id ? "bg-accent text-accent-foreground" : "text-muted-foreground"}`}
                        onClick={() => setSelectedNode({ type: "scene", id: scene.id })}
                      >
                        <FileText className="h-3 w-3" />
                        <span className="truncate">{scene.title}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-muted-foreground text-center py-4">{t("outline.noOutlines")}</div>
          )}
        </ScrollArea>
      </div>

      <div className="flex-1 min-w-0">
        {selectedOutline ? renderNodeEditor() : <div className="h-full flex items-center justify-center text-muted-foreground">{t("outline.createOrSelectOutline")}</div>}
      </div>
    </div>
  );
};

export default OutlineWorkbench;
