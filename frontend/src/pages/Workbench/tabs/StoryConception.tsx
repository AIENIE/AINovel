import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "@/lib/mock-api";
import type { ForeshadowPlan, Outline, PlotBeat, PlotPlanning, TwistOption } from "@/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { AlertCircle, ArrowRight, BookOpen, Loader2, Network, Sparkles, Wand2 } from "lucide-react";

type ConceptionResult = {
  storyCard?: { id: string; title: string; synopsis: string; genre: string; tone: string };
  characterCards?: Array<{ id?: string; name: string; synopsis?: string; details?: string }>;
  plotPlanning?: PlotPlanning;
  outlineSeed?: {
    title?: string;
    chapters?: Array<{
      id?: string;
      title: string;
      summary: string;
      scenes?: Array<{
        id?: string;
        title: string;
        summary: string;
        planning?: Record<string, string>;
      }>;
      planning?: Record<string, string>;
    }>;
  };
};

type OutlineSeedChapter = NonNullable<ConceptionResult["outlineSeed"]>["chapters"][number];
type OutlineSeedScene = NonNullable<OutlineSeedChapter["scenes"]>[number];

const CACHE_PREFIX = "ainovel.plot-planner.conception";

const ensureUuid = () => {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
    const rand = Math.floor(Math.random() * 16);
    const value = char === "x" ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
};

const readCache = (storyId?: string) => {
  if (!storyId) return null;
  try {
    const raw = window.sessionStorage.getItem(`${CACHE_PREFIX}:${storyId}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ConceptionResult & { generated?: { plotPlanning?: PlotPlanning; outlineSuggestion?: ConceptionResult["outlineSeed"] } };
    if (!parsed.plotPlanning && parsed.generated?.plotPlanning) {
      parsed.plotPlanning = parsed.generated.plotPlanning;
    }
    if (!parsed.outlineSeed && parsed.generated?.outlineSuggestion) {
      parsed.outlineSeed = parsed.generated.outlineSuggestion;
    }
    return parsed;
  } catch {
    return null;
  }
};

const writeCache = (storyId: string, result: ConceptionResult) => {
  try {
    window.sessionStorage.setItem(`${CACHE_PREFIX}:${storyId}`, JSON.stringify(result));
  } catch {
    // Ignore session storage failures.
  }
};

const createFallbackPlanning = (
  title: string,
  idea: string,
  genre: string,
  tone: string,
  promiseHint: string,
  secretHint: string,
  memeHint: string,
): PlotPlanning => {
  const promise = promiseHint.trim() || `读者会持续追着《${title || "这个故事"}》的核心秘密往下读。`;
  const hiddenTruth = secretHint.trim() || `真相并不在表面的冲突里，而是与“${idea.slice(0, 18) || "主角命运"}”相关。`;
  const beats: PlotBeat[] = [
    { id: ensureUuid(), label: "钩子", summary: "用异常事件或错误认知开篇，让读者先相信一个表层解释。" },
    { id: ensureUuid(), label: "误导", summary: "通过角色选择和环境证据强化错误判断，让真相暂时潜伏。" },
    { id: ensureUuid(), label: "揭示", summary: "在代价最高的时刻揭开真相，让前文伏笔完成回收。" },
  ];
  const foreshadowPlans: ForeshadowPlan[] = [
    {
      id: ensureUuid(),
      clue: "第一章留下一处看似无关的异常细节",
      disguise: "将异常包装成背景噪音或角色口误",
      payoff: "在揭示时证明这其实是最早的真相提示",
      revealTiming: "中后段",
    },
    {
      id: ensureUuid(),
      clue: "让配角说出半真半假的解释",
      disguise: "把它做成情绪化发言，而不是事实陈述",
      payoff: "让读者回头意识到解释里藏着反转答案",
      revealTiming: "高潮前",
    },
  ];
  const twistOptions: TwistOption[] = [
    {
      id: ensureUuid(),
      label: "保留灵感版",
      track: "instinct",
      hook: `沿着“${idea || "核心创意"}”直推，让反转保持最原始的惊奇感。`,
      hiddenTruth,
      setup: ["用主角的直观判断推进事件", "早期伏笔只轻触，不做过度解释"],
      misdirection: ["让读者把异常归因于表层敌人", "用类型预期掩护真正真相"],
      revealTiming: "第三幕前半",
      payoff: "反转更猛，但容错更低，需要后续大纲配合回收。",
      risk: "如果中段铺垫不足，读者会觉得突然。",
    },
    {
      id: ensureUuid(),
      label: "结构更强版",
      track: "structure",
      hook: `先明确“${promise}”，让每次推进都服务最终揭示。`,
      hiddenTruth,
      setup: ["前 30% 明示错误目标", "中段重复同一线索但换解释视角", "在高潮前把关键线索倒转意义"],
      misdirection: ["给每条线索一个合理但错误的解释", "让配角承担部分误导功能"],
      revealTiming: "高潮节点",
      payoff: "读者会觉得反转早有铺垫，回看时更成立。",
      risk: "结构感更强，但如果角色动机不足，会显得用力过猛。",
    },
  ];

  return {
    corePromise: promise,
    centralQuestion: `谁在误导读者理解“${idea.slice(0, 24) || "这场事件"}”？`,
    hiddenTruth,
    readerMisdirect: "让读者长期相信一个表层答案，再用更深一层因果推翻它。",
    stakes: `一旦真相暴露，主角将失去当前最依赖的判断方式。${tone ? `整体气质保持 ${tone}。` : ""}`,
    beats,
    twistOptions,
    foreshadowPlans,
    memeStrategy: memeHint.trim()
      ? {
          reference: memeHint.trim(),
          purpose: "用来增加角色辨识度或反差感，而不是抢戏。",
          usage: "只在关键场景做一次高识别度点缀，避免反复刷存在。",
          caution: "如果它削弱沉浸感，就退回到更保守的表达。",
        }
      : {
          reference: `${genre || "当前题材"}梗`,
          purpose: "给角色或世界观增加熟悉感。",
          usage: "让梗先服务角色视角，再服务笑点。",
          caution: "一旦读者会跳戏，就不要为了玩梗牺牲结构。",
        },
    confidence: 0.48,
    warnings: ["当前结果使用了轻量 fallback 规划，建议保存后继续在大纲阶段细化。"],
  };
};

const createOutlineFromPlanning = (storyId: string, planning: PlotPlanning, selectedTwistId: string, outlineSeed?: ConceptionResult["outlineSeed"]): Outline => {
  const selectedTwist = planning.twistOptions.find((item) => item.id === selectedTwistId) || planning.twistOptions[0];
  const chapters = (outlineSeed?.chapters?.length ? outlineSeed.chapters : planning.beats).map((item: PlotBeat | OutlineSeedChapter, index) => {
    const beat = planning.beats[index];
    const fallbackScenePlanning = planning.foreshadowPlans[index]
      ? {
          foreshadowHint: planning.foreshadowPlans[index].clue,
          misdirectionAction: planning.foreshadowPlans[index].disguise,
          revealTrigger: planning.foreshadowPlans[index].revealTiming || "",
          payoffPlan: planning.foreshadowPlans[index].payoff,
          memeUsage: index === 0 ? planning.memeStrategy?.usage || "" : "",
        }
      : {
          foreshadowHint: "",
          misdirectionAction: beat?.summary || "",
          revealTrigger: selectedTwist?.revealTiming || "",
          payoffPlan: selectedTwist?.payoff || "",
          memeUsage: "",
        };
    const scenes = item.scenes?.length
      ? item.scenes.map((scene: OutlineSeedScene, sceneIndex: number) => ({
          id: scene.id || ensureUuid(),
          title: scene.title || `场景 ${sceneIndex + 1}`,
          summary: scene.summary || beat?.summary || item.summary || "",
          content: "",
          planning: {
            foreshadowHint: scene.planning?.foreshadowHint || (sceneIndex === 0 ? fallbackScenePlanning.foreshadowHint : ""),
            misdirectionAction: scene.planning?.misdirectionAction || fallbackScenePlanning.misdirectionAction,
            revealTrigger: scene.planning?.revealTrigger || fallbackScenePlanning.revealTrigger,
            payoffPlan: scene.planning?.payoffPlan || fallbackScenePlanning.payoffPlan,
            memeUsage: scene.planning?.memeUsage || (sceneIndex === 0 ? fallbackScenePlanning.memeUsage : ""),
          },
        }))
      : [
          {
            id: ensureUuid(),
            title: beat ? `${beat.label}场景` : `第 ${index + 1} 场`,
            summary: item.summary || beat?.summary || "",
            content: "",
            planning: fallbackScenePlanning,
          },
        ];
    return {
      id: item.id || ensureUuid(),
      title: item.title || beat?.label || `第 ${index + 1} 章`,
      summary: item.summary || beat?.summary || "",
      scenes,
      planning: {
        purpose: index === 0 ? "建立错误认知" : index === planning.beats.length - 1 ? "翻转并回收伏笔" : "加深误导并抬升代价",
        informationRelease: item.summary || beat?.summary || "",
        twistRole: index === planning.beats.length - 1 ? "reveal" : "setup",
        selectedTwistId,
      },
    };
  });

  return {
    id: ensureUuid(),
    storyId,
    title: outlineSeed?.title || "剧情结构规划稿",
    chapters,
    updatedAt: new Date().toISOString(),
    planning,
    activeTwistId: selectedTwistId,
  };
};

const StoryConception = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [idea, setIdea] = useState("");
  const [genre, setGenre] = useState("");
  const [tone, setTone] = useState("");
  const [promiseHint, setPromiseHint] = useState("");
  const [secretHint, setSecretHint] = useState("");
  const [memeHint, setMemeHint] = useState("");
  const [showGuide, setShowGuide] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isApplying, setIsApplying] = useState(false);
  const [result, setResult] = useState<ConceptionResult | null>(null);
  const [selectedTwistId, setSelectedTwistId] = useState("");

  const storyIdFromQuery = params.get("id") || "";

  useEffect(() => {
    const cached = readCache(storyIdFromQuery);
    if (cached?.plotPlanning) {
      setResult(cached);
      setSelectedTwistId(cached.plotPlanning.selectedTwistId || cached.plotPlanning.twistOptions[0]?.id || "");
    }
  }, [storyIdFromQuery]);

  const planning = result?.plotPlanning;
  const selectedTwist = useMemo(
    () => planning?.twistOptions.find((item) => item.id === selectedTwistId) || planning?.twistOptions[0],
    [planning, selectedTwistId],
  );

  const handleGenerate = async () => {
    if (!idea.trim()) {
      toast({ variant: "destructive", title: "请输入核心创意" });
      return;
    }
    setIsGenerating(true);
    try {
      const title = idea.length > 16 ? `${idea.slice(0, 16)}...` : idea;
      const response = await api.stories.conception({
        title,
        synopsis: idea,
        genre: genre || "未分类",
        tone: tone || "默认",
        plotPlanningHints: {
          corePromise: promiseHint.trim() || undefined,
          hiddenTruth: secretHint.trim() || undefined,
          memeReference: memeHint.trim() || undefined,
        },
      });
      const nextPlanning = response?.plotPlanning || createFallbackPlanning(title, idea, genre, tone, promiseHint, secretHint, memeHint);
      const nextResult: ConceptionResult = {
        storyCard: response?.storyCard,
        characterCards: response?.characterCards || response?.generated?.characters || [],
        plotPlanning: nextPlanning,
        outlineSeed: response?.outlineSeed,
      };
      setResult(nextResult);
      setSelectedTwistId(nextPlanning.selectedTwistId || nextPlanning.twistOptions[0]?.id || "");
      if (response?.storyCard?.id) {
        writeCache(response.storyCard.id, nextResult);
      }
      toast({ title: "剧情策划已生成", description: "你现在可以比较双轨反转方案，并把结构落地到大纲。" });
    } catch {
      toast({ variant: "destructive", title: "生成失败" });
    } finally {
      setIsGenerating(false);
    }
  };

  const syncPlanningContext = async (storyId: string, nextPlanning: PlotPlanning) => {
    const lorebookSeeds = Array.isArray(nextPlanning.lorebookSeeds) ? nextPlanning.lorebookSeeds : [];
    const graphSeeds = Array.isArray(nextPlanning.graphSeeds) ? nextPlanning.graphSeeds : [];
    if (!lorebookSeeds.length) return;

    const existingEntries = await api.v2.context.listLorebook(storyId);
    const existingByKey = new Map(existingEntries.map((entry) => [String(entry.entryKey || ""), entry]));
    const syncedEntries = new Map<string, { id?: string } & Record<string, unknown>>();

    for (const seed of lorebookSeeds) {
      const entryKey = String((seed as Record<string, unknown>).entryKey || "").trim();
      if (!entryKey) continue;
      const payload = {
        entryKey,
        displayName: String((seed as Record<string, unknown>).displayName || entryKey),
        category: String((seed as Record<string, unknown>).category || "concept"),
        content: String((seed as Record<string, unknown>).content || ""),
        keywords: Array.isArray((seed as Record<string, unknown>).keywords) ? (seed as Record<string, unknown>).keywords : [],
        insertionPosition: String((seed as Record<string, unknown>).insertionPosition || "system_prompt"),
        tokenBudget: Number((seed as Record<string, unknown>).tokenBudget || 160),
        priority: Number((seed as Record<string, unknown>).priority || 50),
        enabled: true,
      };
      const existing = existingByKey.get(entryKey);
      const saved = existing
        ? await api.v2.context.updateLorebook(storyId, String(existing.id), payload)
        : await api.v2.context.createLorebook(storyId, payload);
      syncedEntries.set(entryKey, saved);
    }

    const currentGraph = await api.v2.context.getGraph(storyId);
    const managedRelations = new Set(["foreshadows", "echoes_meme", "misleads", "reveals", "pays_off"]);
    await Promise.all(
      ((currentGraph.edges as Array<Record<string, unknown>>) || [])
        .filter((edge) => managedRelations.has(String(edge.relationType || edge.type || "")))
        .map((edge) => api.v2.context.deleteRelationship(storyId, String(edge.id))),
    );

    for (const relation of graphSeeds) {
      const source = syncedEntries.get(String((relation as Record<string, unknown>).sourceKey || ""));
      const target = syncedEntries.get(String((relation as Record<string, unknown>).targetKey || ""));
      if (!source?.id || !target?.id) continue;
      await api.v2.context.createRelationship(storyId, {
        source: String(source.id),
        target: String(target.id),
        relationType: String((relation as Record<string, unknown>).relationType || "related_to"),
      });
    }
  };

  const handleApplyToOutline = async () => {
    if (!result?.storyCard?.id || !planning) return;
    setIsApplying(true);
    try {
      const draft = createOutlineFromPlanning(result.storyCard.id, planning, selectedTwistId || planning.twistOptions[0]?.id || "", result.outlineSeed);
      const created = await api.outlines.create(result.storyCard.id, { title: draft.title, planning: draft.planning });
      await api.outlines.save(created.id, { ...draft, id: created.id });
      await syncPlanningContext(result.storyCard.id, planning);
      toast({ title: "结构方案已落地到大纲", description: "已同步最小 Lorebook / Graph 节点。" });
      navigate(`/workbench?id=${result.storyCard.id}&tab=outline`);
    } catch (error: unknown) {
      const description = error instanceof Error ? error.message : "无法把剧情规划落地到大纲";
      toast({ variant: "destructive", title: "应用失败", description });
    } finally {
      setIsApplying(false);
    }
  };

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,420px)_minmax(0,1fr)] h-full">
      <Card className="h-fit">
        <CardHeader>
          <CardTitle>剧情策划入口</CardTitle>
          <CardDescription>先从一句话创意出发，再逐步展开秘密、反转和伏笔的结构骨架。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>核心创意 / 一句话梗概</Label>
            <Textarea
              placeholder="例如：一个在末日废土上送快递的机器人，意外发现了一个冷冻的人类婴儿..."
              className="min-h-[120px]"
              value={idea}
              onChange={(event) => setIdea(event.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>类型流派</Label>
              <Select value={genre} onValueChange={setGenre}>
                <SelectTrigger>
                  <SelectValue placeholder="选择类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="scifi">科幻</SelectItem>
                  <SelectItem value="fantasy">奇幻</SelectItem>
                  <SelectItem value="mystery">悬疑</SelectItem>
                  <SelectItem value="romance">言情</SelectItem>
                  <SelectItem value="wuxia">仙侠</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>基调风格</Label>
              <Select value={tone} onValueChange={setTone}>
                <SelectTrigger>
                  <SelectValue placeholder="选择基调" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="dark">暗黑/压抑</SelectItem>
                  <SelectItem value="humorous">幽默/轻松</SelectItem>
                  <SelectItem value="epic">史诗/宏大</SelectItem>
                  <SelectItem value="warm">治愈/温馨</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="rounded-lg border bg-muted/30 p-3">
            <button type="button" className="flex w-full items-center justify-between text-left text-sm font-medium" onClick={() => setShowGuide((value) => !value)}>
              <span>展开结构引导</span>
              <ArrowRight className={`h-4 w-4 transition-transform ${showGuide ? "rotate-90" : ""}`} />
            </button>
            {showGuide ? (
              <div className="mt-3 space-y-3">
                <div className="space-y-2">
                  <Label>核心承诺（可选）</Label>
                  <Input value={promiseHint} onChange={(event) => setPromiseHint(event.target.value)} placeholder="例如：让读者一直想知道主角究竟在隐瞒什么" />
                </div>
                <div className="space-y-2">
                  <Label>隐藏真相（可选）</Label>
                  <Input value={secretHint} onChange={(event) => setSecretHint(event.target.value)} placeholder="例如：真正的幕后操控者其实是主角自己" />
                </div>
                <div className="space-y-2">
                  <Label>想用的梗 / 圈层气质（可选）</Label>
                  <Input value={memeHint} onChange={(event) => setMemeHint(event.target.value)} placeholder="例如：赛博社畜梗 / 仙侠论坛黑话" />
                </div>
                <p className="text-xs text-muted-foreground">这些字段首版只用于辅助结构建议，不会把入口变成强制重表单。</p>
              </div>
            ) : null}
          </div>
        </CardContent>
        <CardFooter className="gap-2">
          <Button className="flex-1" onClick={handleGenerate} disabled={isGenerating}>
            {isGenerating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
            生成结构骨架
          </Button>
          <Button variant="outline" onClick={() => setResult(null)} disabled={!result}>
            清空结果
          </Button>
        </CardFooter>
      </Card>

      <div className="space-y-6 min-h-0">
        {!result || !planning ? (
          <div className="border-2 border-dashed rounded-lg p-8 flex flex-col items-center justify-center text-muted-foreground h-[520px] bg-muted/20">
            <Wand2 className="h-12 w-12 mb-4 opacity-20" />
            <p>生成后会在这里展示结构骨架、双轨反转和伏笔链。</p>
            <p className="text-sm opacity-70">首版先帮助你做出更强的大纲，而不是直接把正文写满。</p>
          </div>
        ) : (
          <>
            <Card>
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      <BookOpen className="h-5 w-5" />
                      {result.storyCard?.title || "未命名故事"}
                    </CardTitle>
                    <CardDescription>{result.storyCard?.synopsis || idea}</CardDescription>
                  </div>
                  <div className="flex gap-2">
                    <Badge variant="secondary">{result.storyCard?.genre || genre || "未分类"}</Badge>
                    <Badge variant="outline">{result.storyCard?.tone || tone || "默认"}</Badge>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
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
                <CardTitle>结构骨架</CardTitle>
                <CardDescription>先把剧情的推进骨架拉直，再决定具体章节怎么落地。</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 md:grid-cols-3">
                {planning.beats.map((beat) => (
                  <div key={beat.id} className="rounded-lg border p-3">
                    <div className="text-xs text-muted-foreground">{beat.label}</div>
                    <div className="mt-2 text-sm">{beat.summary}</div>
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>双轨反转方案</CardTitle>
                <CardDescription>AI 不替你拍板，而是把“保留灵感版”和“结构更强版”放到同一张桌子上。</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-4 xl:grid-cols-2">
                {planning.twistOptions.map((twist) => {
                  const selected = twist.id === (selectedTwistId || selectedTwist?.id);
                  return (
                    <div key={twist.id} className={`rounded-xl border p-4 ${selected ? "border-primary bg-primary/5" : ""}`}>
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <div className="font-medium">{twist.label}</div>
                          <div className="text-xs text-muted-foreground">{twist.track === "structure" ? "结构更强版" : "保留灵感版"}</div>
                        </div>
                        <Button size="sm" variant={selected ? "default" : "outline"} onClick={() => setSelectedTwistId(twist.id)}>
                          {selected ? "当前方案" : "采用此方案"}
                        </Button>
                      </div>
                      <Separator className="my-3" />
                      <div className="space-y-3 text-sm">
                        <div>
                          <div className="text-xs text-muted-foreground">钩子</div>
                          <div>{twist.hook}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">隐藏真相</div>
                          <div>{twist.hiddenTruth}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">铺垫点</div>
                          <ul className="mt-1 space-y-1 list-disc pl-5">
                            {twist.setup.map((item, index) => <li key={`${twist.id}-setup-${index}`}>{item}</li>)}
                          </ul>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">误导动作</div>
                          <ul className="mt-1 space-y-1 list-disc pl-5">
                            {twist.misdirection.map((item, index) => <li key={`${twist.id}-mis-${index}`}>{item}</li>)}
                          </ul>
                        </div>
                        <div className="grid gap-3 md:grid-cols-2">
                          <div>
                            <div className="text-xs text-muted-foreground">揭示时机</div>
                            <div>{twist.revealTiming}</div>
                          </div>
                          <div>
                            <div className="text-xs text-muted-foreground">提前暴露风险</div>
                            <div>{twist.risk}</div>
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">回收效果</div>
                          <div>{twist.payoff}</div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </CardContent>
            </Card>

            <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
              <Card>
                <CardHeader>
                  <CardTitle>伏笔链</CardTitle>
                  <CardDescription>首版先记录“埋点 {"->"} 掩护 {"->"} 回收”，后续再扩成完整链路板。</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {planning.foreshadowPlans.map((item, index) => (
                    <div key={item.id} className="rounded-lg border p-3">
                      <div className="flex items-center justify-between gap-3">
                        <div className="font-medium">伏笔 {index + 1}</div>
                        {item.revealTiming ? <Badge variant="outline">{item.revealTiming}</Badge> : null}
                      </div>
                      <div className="mt-2 grid gap-3 md:grid-cols-3 text-sm">
                        <div>
                          <div className="text-xs text-muted-foreground">埋点</div>
                          <div>{item.clue}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">掩护</div>
                          <div>{item.disguise}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">回收</div>
                          <div>{item.payoff}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>梗融合策略</CardTitle>
                  <CardDescription>不是单纯好玩，而是确保它不把故事带出戏。</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div>
                    <div className="text-xs text-muted-foreground">引用对象</div>
                    <div>{planning.memeStrategy?.reference || "暂未指定"}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">使用目的</div>
                    <div>{planning.memeStrategy?.purpose || "让角色或世界更有辨识度"}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">自然融合版</div>
                    <div>{planning.memeStrategy?.usage || "把梗放进角色视角，而不是直接拿来当段子。"}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">保守提醒</div>
                    <div>{planning.memeStrategy?.caution || "如果它破坏沉浸感，就直接回退到保守版。"}</div>
                  </div>
                  {planning.warnings?.length ? (
                    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-900">
                      <div className="mb-1 flex items-center gap-2 text-xs font-medium">
                        <AlertCircle className="h-4 w-4" />
                        风险边界
                      </div>
                      <ul className="list-disc pl-5 space-y-1">
                        {planning.warnings.map((item, index) => <li key={`warning-${index}`}>{item}</li>)}
                      </ul>
                    </div>
                  ) : null}
                </CardContent>
              </Card>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button onClick={handleApplyToOutline} disabled={isApplying || !planning}>
                {isApplying ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Network className="mr-2 h-4 w-4" />}
                应用当前方案到大纲
              </Button>
              {result.storyCard?.id ? (
                <Button variant="outline" onClick={() => navigate(`/workbench?id=${result.storyCard?.id}&tab=outline`)}>
                  查看大纲工作台
                </Button>
              ) : null}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default StoryConception;
