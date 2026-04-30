import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/mock-api";
import type { ChapterPlanning, Outline, ScenePlanning, Story, TwistOption } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { ChevronDown, FileText, Plus, Save, Sparkles } from "lucide-react";

interface OutlineWorkbenchProps {
  initialStoryId?: string;
}

type SelectedNode = { type: "chapter" | "scene"; id: string } | null;

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
  const [stories, setStories] = useState<Story[]>([]);
  const [selectedStoryId, setSelectedStoryId] = useState("");
  const [outlines, setOutlines] = useState<Outline[]>([]);
  const [selectedOutline, setSelectedOutline] = useState<Outline | null>(null);
  const [selectedNode, setSelectedNode] = useState<SelectedNode>(null);
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [chapterPlanning, setChapterPlanning] = useState<ChapterPlanning>(chapterDefaults());
  const [scenePlanning, setScenePlanning] = useState<ScenePlanning>(sceneDefaults());
  const [isSaving, setIsSaving] = useState(false);
  const { toast } = useToast();

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
        const description = error instanceof Error ? error.message : "无法加载故事";
        toast({ variant: "destructive", title: "加载故事失败", description });
      });
  }, [initialStoryId, toast]);

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
        const description = error instanceof Error ? error.message : "无法加载大纲";
        toast({ variant: "destructive", title: "加载大纲失败", description });
      });
  }, [selectedStoryId, toast]);

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
    try {
      const saved = await api.outlines.save(selectedOutline.id, nextOutline as Outline & { worldId?: string });
      setSelectedOutline(saved);
      setOutlines((prev) => prev.map((item) => (item.id === saved.id ? saved : item)));
      toast({ title: "结构规划已保存" });
    } catch (error: unknown) {
      const description = error instanceof Error ? error.message : "无法保存结构规划";
      toast({ variant: "destructive", title: "保存失败", description });
    } finally {
      setIsSaving(false);
    }
  };

  const handleCreateOutline = async () => {
    if (!selectedStoryId) return;
    try {
      const created = await api.outlines.create(selectedStoryId, { title: "剧情结构规划稿", planning });
      setOutlines((prev) => [created, ...prev]);
      setSelectedOutline(created);
      setSelectedNode(null);
      toast({ title: "已创建大纲" });
    } catch (error: unknown) {
      const description = error instanceof Error ? error.message : "无法创建大纲";
      toast({ variant: "destructive", title: "创建失败", description });
    }
  };

  const handleAddChapter = () => {
    if (!selectedOutline) return;
    const nextChapter = {
      id: ensureUuid(),
      title: "新章节",
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
              scenes: [...chapter.scenes, { id: ensureUuid(), title: "新场景", summary: "", content: "", planning: sceneDefaults(activeTwistId) }],
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
      const response = await api.ai.refine(
        summary,
        selectedNode?.type === "scene"
          ? "润色当前场景摘要，使其更适合服务伏笔、误导和揭示节奏。"
          : "润色当前章节摘要，使其更清晰地体现结构作用和信息释放。",
        models[0]?.id,
      );
      setSummary(response.result);
    } catch (error: unknown) {
      const description = error instanceof Error ? error.message : "AI 润色失败";
      toast({ variant: "destructive", title: "AI 失败", description });
    }
  };

  const overview = useMemo(() => {
    if (!planning) return null;
    return (
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>结构总览</CardTitle>
            <CardDescription>先确认这条大纲服务哪种主问题、隐藏真相和反转路径。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">核心承诺</div>
              <div className="mt-1 text-sm font-medium">{planning.corePromise || "待补充"}</div>
            </div>
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">主问题</div>
              <div className="mt-1 text-sm font-medium">{planning.centralQuestion || "待补充"}</div>
            </div>
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">隐藏真相</div>
              <div className="mt-1 text-sm font-medium">{planning.hiddenTruth || "待补充"}</div>
            </div>
            <div className="rounded-lg border p-3">
              <div className="text-xs text-muted-foreground">读者误判路径</div>
              <div className="mt-1 text-sm font-medium">{planning.readerMisdirect || "待补充"}</div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>双轨反转比较</CardTitle>
            <CardDescription>首版仍保留两个方案并行，不让 AI 单方面替你定稿。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 xl:grid-cols-2">
            {twistOptions.map((twist: TwistOption) => {
              const selected = twist.id === activeTwistId;
              return (
                <div key={twist.id} className={`rounded-xl border p-4 ${selected ? "border-primary bg-primary/5" : ""}`}>
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="font-medium">{twist.label}</div>
                      <div className="text-xs text-muted-foreground">{twist.track === "structure" ? "结构更强版" : "保留灵感版"}</div>
                    </div>
                    <Button size="sm" variant={selected ? "default" : "outline"} onClick={() => handleSelectTwist(twist.id)}>
                      {selected ? "当前采用" : "切换"}
                    </Button>
                  </div>
                  <div className="mt-3 space-y-2 text-sm">
                    <div>
                      <div className="text-xs text-muted-foreground">钩子</div>
                      <div>{twist.hook}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">隐藏真相</div>
                      <div>{twist.hiddenTruth}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">揭示时机</div>
                      <div>{twist.revealTiming}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">回收效果</div>
                      <div>{twist.payoff}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">暴露风险</div>
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
            <CardTitle>伏笔链</CardTitle>
            <CardDescription>主路径里先维护最小“埋点 {"->"} 掩护 {"->"} 回收”链条。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {foreshadowPlans.map((item) => (
              <div key={item.id} className="rounded-lg border p-3 text-sm">
                <div className="font-medium">{item.clue}</div>
                <div className="mt-2 grid gap-2 md:grid-cols-3">
                  <div>
                    <div className="text-xs text-muted-foreground">掩护</div>
                    <div>{item.disguise}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">回收</div>
                    <div>{item.payoff}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">揭示时机</div>
                    <div>{item.revealTiming || "待定"}</div>
                  </div>
                </div>
              </div>
            ))}
            {!foreshadowPlans.length ? <div className="text-sm text-muted-foreground">当前结构方案还没有显式伏笔链。</div> : null}
          </CardContent>
        </Card>
      </div>
    );
  }, [planning, twistOptions, activeTwistId, foreshadowPlans, handleSelectTwist]);

  const renderNodeEditor = () => {
    if (!selectedNode) {
      return overview || <div className="h-full flex items-center justify-center text-muted-foreground">请先选择一个大纲。</div>;
    }

    return (
      <div className="space-y-6 max-w-3xl">
        <div className="space-y-2">
          <Label>标题</Label>
          <Input value={title} onChange={(event) => setTitle(event.target.value)} />
        </div>

        <div className="space-y-2">
          <Label>梗概 / 摘要</Label>
          <Textarea className="min-h-[180px]" value={summary} onChange={(event) => setSummary(event.target.value)} />
        </div>

        {selectedNode.type === "chapter" ? (
          <Card>
            <CardHeader>
              <CardTitle>章节结构标签</CardTitle>
              <CardDescription>让每章都明确服务当前采用的反转路径。</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>采用哪条反转</Label>
                <Select value={chapterPlanning.selectedTwistId || activeTwistId} onValueChange={(value) => setChapterPlanning((current) => ({ ...current, selectedTwistId: value }))}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择反转方案" />
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
                <Label>张力变化</Label>
                <Input value={chapterPlanning.tensionShift || ""} onChange={(event) => setChapterPlanning((current) => ({ ...current, tensionShift: event.target.value }))} placeholder="例如：误导加深 / 真相逼近" />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>本章揭示焦点</Label>
                <Textarea value={chapterPlanning.revealFocus || ""} onChange={(event) => setChapterPlanning((current) => ({ ...current, revealFocus: event.target.value }))} placeholder="本章希望读者重新理解什么？" />
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>场景结构标签</CardTitle>
              <CardDescription>把场景的目标、冲突、信息释放和伏笔绑定起来。</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>场景目标</Label>
                <Input value={scenePlanning.goal || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, goal: event.target.value }))} placeholder="例如：让读者先接受错误答案" />
              </div>
              <div className="space-y-2">
                <Label>冲突</Label>
                <Input value={scenePlanning.conflict || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, conflict: event.target.value }))} placeholder="例如：角色理解与真实信息相互冲突" />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>本场景释放的信息</Label>
                <Textarea value={scenePlanning.infoRelease || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, infoRelease: event.target.value }))} placeholder="读者从这个场景里应该得到什么新信息？" />
              </div>
              <div className="space-y-2">
                <Label>关联伏笔</Label>
                <Select value={scenePlanning.foreshadowId || ""} onValueChange={(value) => setScenePlanning((current) => ({ ...current, foreshadowId: value }))}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择伏笔" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">无</SelectItem>
                    {foreshadowPlans.map((item) => (
                      <SelectItem key={item.id} value={item.id}>
                        {item.clue}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>服务哪条反转</Label>
                <Select value={scenePlanning.revealFor || activeTwistId} onValueChange={(value) => setScenePlanning((current) => ({ ...current, revealFor: value }))}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择反转方案" />
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
                <Label>梗融合方式</Label>
                <Textarea value={scenePlanning.memeUsage || ""} onChange={(event) => setScenePlanning((current) => ({ ...current, memeUsage: event.target.value }))} placeholder="如果要用梗，它应该如何自然出现而不出戏？" />
              </div>
            </CardContent>
          </Card>
        )}

        <div className="flex justify-end gap-2">
          {selectedNode.type === "chapter" ? (
            <Button variant="outline" onClick={handleAddScene}>
              <Plus className="mr-2 h-4 w-4" />
              添加场景
            </Button>
          ) : null}
          <Button variant="outline" onClick={handleAiRefine}>
            <Sparkles className="mr-2 h-4 w-4" />
            AI 润色
          </Button>
          <Button onClick={handleSave} disabled={isSaving}>
            <Save className="mr-2 h-4 w-4" />
            {isSaving ? "保存中..." : "保存修改"}
          </Button>
        </div>
      </div>
    );
  };

  return (
    <div className="flex h-[calc(100vh-200px)] gap-6">
      <div className="w-80 flex flex-col gap-4 border-r pr-4">
        <div className="space-y-2">
          <Label>当前故事</Label>
          <Select value={selectedStoryId} onValueChange={setSelectedStoryId}>
            <SelectTrigger>
              <SelectValue placeholder="选择故事" />
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

        <div className="rounded-lg border bg-muted/30 p-3">
          <div className="text-xs text-muted-foreground">当前采用方案</div>
          <div className="mt-1 font-medium">{activeTwist?.label || "尚未选择"}</div>
          <div className="mt-1 text-sm text-muted-foreground">{activeTwist?.payoff || "请先从构思入口应用一个结构方案。"}</div>
        </div>

        <div className="flex items-center justify-between mt-2">
          <span className="text-sm font-medium text-muted-foreground">大纲结构</span>
          <div className="flex gap-1">
            <Button size="sm" variant="ghost" className="h-6 px-2" onClick={handleCreateOutline}>
              <Plus className="mr-1 h-4 w-4" />
              新大纲
            </Button>
            <Button size="sm" variant="ghost" className="h-6 px-2" onClick={handleAddChapter} disabled={!selectedOutline}>
              <Plus className="mr-1 h-4 w-4" />
              章节
            </Button>
          </div>
        </div>

        <ScrollArea className="flex-1">
          {selectedOutline ? (
            <div className="space-y-1">
              <button
                type="button"
                className={`flex w-full items-center gap-2 rounded-md p-2 text-left hover:bg-accent ${selectedNode === null ? "bg-accent" : ""}`}
                onClick={() => setSelectedNode(null)}
              >
                <Sparkles className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium truncate">结构总览</span>
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
            <div className="text-sm text-muted-foreground text-center py-4">暂无大纲</div>
          )}
        </ScrollArea>
      </div>

      <div className="flex-1 min-w-0">
        {selectedOutline ? renderNodeEditor() : <div className="h-full flex items-center justify-center text-muted-foreground">请先创建或选择一个大纲。</div>}
      </div>
    </div>
  );
};

export default OutlineWorkbench;
