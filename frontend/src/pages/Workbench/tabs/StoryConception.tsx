import type { NetworkObject } from "@/lib/api-client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api, normalizeConceptionResult } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import type { Outline, PlotPlanning, ScenePlanning } from "@/types";
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
import { localizedErrorMessage } from "@/lib/error-messages";

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

const CACHE_PREFIX = "ainovel.plot-planner.conception";

const ensureUuid = () => crypto.randomUUID();

const toSceneType = (value?: string): ScenePlanning["sceneType"] => (
  value === "action"
  || value === "dialogue"
  || value === "introspection"
  || value === "description"
  || value === "flashback"
    ? value
    : undefined
);

const readCache = (storyId?: string) => {
  if (!storyId) return null;
  const raw = window.sessionStorage.getItem(`${CACHE_PREFIX}:${storyId}`);
  return raw ? normalizeConceptionResult(JSON.parse(raw)) as ConceptionResult : null;
};

const writeCache = (storyId: string, result: ConceptionResult) => {
  window.sessionStorage.setItem(`${CACHE_PREFIX}:${storyId}`, JSON.stringify(result));
};

const createOutlineFromPlanning = (storyId: string, planning: PlotPlanning, selectedTwistId: string, outlineSeed?: ConceptionResult["outlineSeed"]): Outline => {
  if (!outlineSeed?.title || !outlineSeed.chapters?.length) throw new Error("AI_INITIALIZATION_INVALID_RESULT");
  const chapters = outlineSeed.chapters.map(chapter => {
    if (!chapter.title || !chapter.summary || !chapter.scenes?.length) throw new Error("AI_INITIALIZATION_INVALID_RESULT");
    return {
      id: chapter.id || ensureUuid(), title: chapter.title, summary: chapter.summary,
      planning: { ...chapter.planning, selectedTwistId },
      scenes: chapter.scenes.map(scene => {
        if (!scene.title || !scene.summary) throw new Error("AI_INITIALIZATION_INVALID_RESULT");
        return {
          id: scene.id || ensureUuid(), title: scene.title, summary: scene.summary, content: "",
          planning: { ...scene.planning, sceneType: toSceneType(scene.planning?.sceneType) },
        };
      }),
    };
  });
  return { id: ensureUuid(), storyId, title: outlineSeed.title, chapters,
    updatedAt: new Date().toISOString(), planning, activeTwistId: selectedTwistId };
};

const StoryConception = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { t } = useTranslation();
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
  const [cacheReadError, setCacheReadError] = useState<string | null>(null);
  const [selectedTwistId, setSelectedTwistId] = useState("");

  const storyIdFromQuery = params.get("id") || "";

  const restoreCache = useCallback((storyId: string) => {
    setCacheReadError(null);
    try {
      const cached = readCache(storyId);
      if (cached?.plotPlanning) {
        setResult(cached);
        setSelectedTwistId(cached.plotPlanning.selectedTwistId || cached.plotPlanning.twistOptions[0]?.id || "");
      }
    } catch (error) {
      setCacheReadError(localizedErrorMessage(error, "errors.loadFailed"));
    }
  }, []);
  useEffect(() => { restoreCache(storyIdFromQuery); }, [storyIdFromQuery, restoreCache]);

  const planning = result?.plotPlanning;
  const selectedTwist = useMemo(
    () => planning?.twistOptions.find((item) => item.id === selectedTwistId) || planning?.twistOptions[0],
    [planning, selectedTwistId],
  );

  const handleGenerate = async () => {
    if (!idea.trim()) {
      toast({ variant: "destructive", title: t("storyConception.ideaRequired") });
      return;
    }
    setIsGenerating(true);
    try {
      const title = idea.length > 16 ? `${idea.slice(0, 16)}...` : idea;
      const operation = await runTrackedAiOperation(api.stories.startConception({
        title,
        synopsis: idea,
        genre: genre || t("storyConception.uncategorized"),
        tone: tone || t("storyConception.defaultTone"),
        plotPlanningHints: {
          corePromise: promiseHint.trim() || undefined,
          hiddenTruth: secretHint.trim() || undefined,
          memeReference: memeHint.trim() || undefined,
        },
      }));
      const response = normalizeConceptionResult(operation.resultJson ? JSON.parse(operation.resultJson) : null);
      const nextPlanning = response.plotPlanning;
      if (!nextPlanning || !response.storyCard?.id || !Array.isArray(response.characterCards) || !response.outlineSeed?.chapters?.length)
        throw new Error("AI_INITIALIZATION_INVALID_RESULT");
      const nextResult: ConceptionResult = {
        storyCard: response?.storyCard,
        characterCards: response.characterCards,
        plotPlanning: nextPlanning,
        outlineSeed: response?.outlineSeed,
      };
      setResult(nextResult);
      setSelectedTwistId(nextPlanning.selectedTwistId || nextPlanning.twistOptions[0]?.id || "");
      if (response?.storyCard?.id) {
        try { writeCache(response.storyCard.id, nextResult); }
        catch (error) { toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") }); }
      }
      toast({ title: t("storyConception.generated"), description: t("storyConception.generatedDesc") });
    } catch (error) {
      toast({ variant: "destructive", title: t("errors.generateFailed"), description: localizedErrorMessage(error, "errors.generateFailed") });
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
    const syncedEntries = new Map<string, { id?: string } & Record<string, NetworkObject>>();

    for (const seed of lorebookSeeds) {
      const entryKey = String((seed as Record<string, NetworkObject>).entryKey || "").trim();
      if (!entryKey) continue;
      const payload = {
        entryKey,
        displayName: String((seed as Record<string, NetworkObject>).displayName || entryKey),
        category: String((seed as Record<string, NetworkObject>).category || "concept"),
        content: String((seed as Record<string, NetworkObject>).content || ""),
        keywords: Array.isArray((seed as Record<string, NetworkObject>).keywords) ? (seed as Record<string, NetworkObject>).keywords : [],
        insertionPosition: String((seed as Record<string, NetworkObject>).insertionPosition || "system_prompt"),
        tokenBudget: Number((seed as Record<string, NetworkObject>).tokenBudget || 160),
        priority: Number((seed as Record<string, NetworkObject>).priority || 50),
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
      ((currentGraph.edges as Array<Record<string, NetworkObject>>) || [])
        .filter((edge) => managedRelations.has(String(edge.relationType || edge.type || "")))
        .map((edge) => api.v2.context.deleteRelationship(storyId, String(edge.id))),
    );

    for (const relation of graphSeeds) {
      const source = syncedEntries.get(String((relation as Record<string, NetworkObject>).sourceKey || ""));
      const target = syncedEntries.get(String((relation as Record<string, NetworkObject>).targetKey || ""));
      if (!source?.id || !target?.id) continue;
      await api.v2.context.createRelationship(storyId, {
        source: String(source.id),
        target: String(target.id),
        relationType: String((relation as Record<string, NetworkObject>).relationType || "related_to"),
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
      toast({ title: t("storyConception.applied"), description: t("storyConception.appliedDesc") });
      navigate(`/workbench?storyId=${result.storyCard.id}&tab=outline`);
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("storyConception.applyFailed"), description: localizedErrorMessage(error, "storyConception.applyFailed") });
    } finally {
      setIsApplying(false);
    }
  };

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,420px)_minmax(0,1fr)] h-full">
      {cacheReadError && <div role="alert" className="text-sm text-destructive">{cacheReadError}<Button variant="link" onClick={() => restoreCache(storyIdFromQuery)}>{t("common.retry")}</Button></div>}
      <Card className="h-fit">
        <CardHeader>
          <CardTitle>{t("storyConception.entryTitle")}</CardTitle>
          <CardDescription>{t("storyConception.entryDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>{t("storyConception.ideaLabel")}</Label>
            <Textarea
              placeholder={t("storyConception.ideaPlaceholder")}
              className="min-h-[120px]"
              value={idea}
              onChange={(event) => setIdea(event.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>{t("storyConception.genreLabel")}</Label>
              <Select value={genre} onValueChange={setGenre}>
                <SelectTrigger>
                  <SelectValue placeholder={t("storyConception.selectGenre")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="scifi">{t("storyConception.genreSciFi")}</SelectItem>
                  <SelectItem value="fantasy">{t("storyConception.genreFantasy")}</SelectItem>
                  <SelectItem value="mystery">{t("storyConception.genreMystery")}</SelectItem>
                  <SelectItem value="romance">{t("storyConception.genreRomance")}</SelectItem>
                  <SelectItem value="wuxia">{t("storyConception.genreWuxia")}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t("storyConception.toneLabel")}</Label>
              <Select value={tone} onValueChange={setTone}>
                <SelectTrigger>
                  <SelectValue placeholder={t("storyConception.selectTone")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="dark">{t("storyConception.toneDark")}</SelectItem>
                  <SelectItem value="humorous">{t("storyConception.toneHumorous")}</SelectItem>
                  <SelectItem value="epic">{t("storyConception.toneEpic")}</SelectItem>
                  <SelectItem value="warm">{t("storyConception.toneWarm")}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="rounded-lg border bg-muted/30 p-3">
            <button type="button" className="flex w-full items-center justify-between text-left text-sm font-medium" onClick={() => setShowGuide((value) => !value)}>
              <span>{t("storyConception.expandGuide")}</span>
              <ArrowRight className={`h-4 w-4 transition-transform ${showGuide ? "rotate-90" : ""}`} />
            </button>
            {showGuide ? (
              <div className="mt-3 space-y-3">
                <div className="space-y-2">
                  <Label>{t("storyConception.promiseLabel")}</Label>
                  <Input value={promiseHint} onChange={(event) => setPromiseHint(event.target.value)} placeholder={t("storyConception.promisePlaceholder")} />
                </div>
                <div className="space-y-2">
                  <Label>{t("storyConception.secretLabel")}</Label>
                  <Input value={secretHint} onChange={(event) => setSecretHint(event.target.value)} placeholder={t("storyConception.secretPlaceholder")} />
                </div>
                <div className="space-y-2">
                  <Label>{t("storyConception.memeLabel")}</Label>
                  <Input value={memeHint} onChange={(event) => setMemeHint(event.target.value)} placeholder={t("storyConception.memePlaceholder")} />
                </div>
                <p className="text-xs text-muted-foreground">{t("storyConception.guideNote")}</p>
              </div>
            ) : null}
          </div>
        </CardContent>
        <CardFooter className="gap-2">
          <Button className="flex-1" onClick={handleGenerate} disabled={isGenerating}>
            {isGenerating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
            {t("storyConception.generateSkeleton")}
          </Button>
          <Button variant="outline" onClick={() => setResult(null)} disabled={!result}>
            {t("storyConception.clearResult")}
          </Button>
        </CardFooter>
      </Card>

      <div className="space-y-6 min-h-0">
        {!result || !planning ? (
          <div className="border-2 border-dashed rounded-lg p-8 flex flex-col items-center justify-center text-muted-foreground h-[520px] bg-muted/20">
            <Wand2 className="h-12 w-12 mb-4 opacity-20" />
            <p>{t("storyConception.emptyHint")}</p>
            <p className="text-sm opacity-70">{t("storyConception.emptyHint2")}</p>
          </div>
        ) : (
          <>
            <Card>
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      <BookOpen className="h-5 w-5" />
                      {result.storyCard?.title || t("storyConception.untitledStory")}
                    </CardTitle>
                    <CardDescription>{result.storyCard?.synopsis || idea}</CardDescription>
                  </div>
                  <div className="flex gap-2">
                    <Badge variant="secondary">{result.storyCard?.genre || genre || t("storyConception.uncategorized")}</Badge>
                    <Badge variant="outline">{result.storyCard?.tone || tone || t("storyConception.defaultTone")}</Badge>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-lg border p-3">
                  <div className="text-xs text-muted-foreground">{t("storyConception.corePromise")}</div>
                  <div className="mt-1 text-sm font-medium">{planning.corePromise || t("outline.todo")}</div>
                </div>
                <div className="rounded-lg border p-3">
                  <div className="text-xs text-muted-foreground">{t("storyConception.centralQuestion")}</div>
                  <div className="mt-1 text-sm font-medium">{planning.centralQuestion || t("outline.todo")}</div>
                </div>
                <div className="rounded-lg border p-3">
                  <div className="text-xs text-muted-foreground">{t("storyConception.hiddenTruth")}</div>
                  <div className="mt-1 text-sm font-medium">{planning.hiddenTruth || t("outline.todo")}</div>
                </div>
                <div className="rounded-lg border p-3">
                  <div className="text-xs text-muted-foreground">{t("storyConception.readerMisdirect")}</div>
                  <div className="mt-1 text-sm font-medium">{planning.readerMisdirect || t("outline.todo")}</div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{t("storyConception.structureSkeleton")}</CardTitle>
                <CardDescription>{t("storyConception.structureSkeletonDesc")}</CardDescription>
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
                <CardTitle>{t("storyConception.dualTwist")}</CardTitle>
                <CardDescription>{t("storyConception.dualTwistDesc")}</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-4 xl:grid-cols-2">
                {planning.twistOptions.map((twist) => {
                  const selected = twist.id === (selectedTwistId || selectedTwist?.id);
                  return (
                    <div key={twist.id} className={`rounded-xl border p-4 ${selected ? "border-primary bg-primary/5" : ""}`}>
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <div className="font-medium">{twist.label}</div>
                          <div className="text-xs text-muted-foreground">{twist.track === "structure" ? t("outline.trackStructure") : t("outline.trackInspiration")}</div>
                        </div>
                        <Button size="sm" variant={selected ? "default" : "outline"} onClick={() => setSelectedTwistId(twist.id)}>
                          {selected ? t("storyConception.currentPlan") : t("storyConception.adoptPlan")}
                        </Button>
                      </div>
                      <Separator className="my-3" />
                      <div className="space-y-3 text-sm">
                        <div>
                          <div className="text-xs text-muted-foreground">{t("outline.hook")}</div>
                          <div>{twist.hook}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">{t("outline.hiddenTruth")}</div>
                          <div>{twist.hiddenTruth}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">{t("storyConception.setupPoints")}</div>
                          <ul className="mt-1 space-y-1 list-disc pl-5">
                            {twist.setup.map((item, index) => <li key={`${twist.id}-setup-${index}`}>{item}</li>)}
                          </ul>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">{t("storyConception.misdirection")}</div>
                          <ul className="mt-1 space-y-1 list-disc pl-5">
                            {twist.misdirection.map((item, index) => <li key={`${twist.id}-mis-${index}`}>{item}</li>)}
                          </ul>
                        </div>
                        <div className="grid gap-3 md:grid-cols-2">
                          <div>
                            <div className="text-xs text-muted-foreground">{t("outline.revealTiming")}</div>
                            <div>{twist.revealTiming}</div>
                          </div>
                          <div>
                            <div className="text-xs text-muted-foreground">{t("storyConception.earlyRevealRisk")}</div>
                            <div>{twist.risk}</div>
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">{t("outline.payoff")}</div>
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
                  <CardTitle>{t("outline.foreshadowChain")}</CardTitle>
                  <CardDescription>{t("storyConception.foreshadowChainDesc")}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {planning.foreshadowPlans.map((item, index) => (
                    <div key={item.id} className="rounded-lg border p-3">
                      <div className="flex items-center justify-between gap-3">
                        <div className="font-medium">{t("storyConception.foreshadowItem", { count: index + 1 })}</div>
                        {item.revealTiming ? <Badge variant="outline">{item.revealTiming}</Badge> : null}
                      </div>
                      <div className="mt-2 grid gap-3 md:grid-cols-3 text-sm">
                        <div>
                          <div className="text-xs text-muted-foreground">{t("outline.seed")}</div>
                          <div>{item.clue}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">{t("outline.disguise")}</div>
                          <div>{item.disguise}</div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">{t("outline.payoff")}</div>
                          <div>{item.payoff}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>{t("storyConception.memeStrategy")}</CardTitle>
                  <CardDescription>{t("storyConception.memeStrategyDesc")}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div>
                    <div className="text-xs text-muted-foreground">{t("storyConception.memeReference")}</div>
                    <div>{planning.memeStrategy?.reference || t("storyConception.notSpecified")}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">{t("storyConception.memePurpose")}</div>
                    <div>{planning.memeStrategy?.purpose || t("storyConception.memePurposeDefault")}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">{t("storyConception.memeNatural")}</div>
                    <div>{planning.memeStrategy?.usage || t("storyConception.memeNaturalDefault")}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">{t("storyConception.memeCaution")}</div>
                    <div>{planning.memeStrategy?.caution || t("storyConception.memeCautionDefault")}</div>
                  </div>
                  {planning.warnings?.length ? (
                    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-900">
                      <div className="mb-1 flex items-center gap-2 text-xs font-medium">
                        <AlertCircle className="h-4 w-4" />
                        {t("storyConception.riskBoundary")}
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
                {t("storyConception.applyToOutline")}
              </Button>
              {result.storyCard?.id ? (
                <Button variant="outline" onClick={() => navigate(`/workbench?storyId=${result.storyCard?.id}&tab=outline`)}>
                  {t("storyConception.viewOutlineWorkbench")}
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
