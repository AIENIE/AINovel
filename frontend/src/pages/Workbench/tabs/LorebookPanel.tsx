import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { Story } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Progress } from "@/components/ui/progress";
import { localizedErrorMessage } from "@/lib/error-messages";

interface LorebookPanelProps {
  initialStoryId?: string;
}

type LorebookEntry = {
  id?: string;
  displayName: string;
  entryKey: string;
  category: string;
  content: string;
  keywords: string[];
  priority: number;
  tokenBudget: number;
  insertionPosition: string;
  enabled: boolean;
};

const newEntry = (): LorebookEntry => ({
  displayName: "",
  entryKey: "",
  category: "custom",
  content: "",
  keywords: [],
  priority: 0,
  tokenBudget: 500,
  insertionPosition: "before_scene",
  enabled: true,
});

const LorebookPanel = ({ initialStoryId }: LorebookPanelProps) => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState(initialStoryId || "");
  const [entries, setEntries] = useState<any[]>([]);
  const [selectedId, setSelectedId] = useState<string>("");
  const [editor, setEditor] = useState<LorebookEntry>(newEntry());
  const [keywordInput, setKeywordInput] = useState("");
  const [filter, setFilter] = useState("all");
  const [search, setSearch] = useState("");
  const [importPayload, setImportPayload] = useState("[]");
  const [extractions, setExtractions] = useState<any[]>([]);
  const [preview, setPreview] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.stories
      .list()
      .then((list) => {
        setStories(list);
        if (!storyId && list.length > 0) {
          setStoryId(initialStoryId && list.some((story) => story.id === initialStoryId) ? initialStoryId : list[0].id);
        }
      })
      .catch((error: any) => toast({ variant: "destructive", title: t("errors.loadStoriesFailed"), description: localizedErrorMessage(error, "errors.loadStoriesFailed") }));
  }, []);

  const loadLorebook = async () => {
    if (!storyId) return;
    const [entryList, extractionList] = await Promise.all([api.v2.context.listLorebook(storyId), api.v2.context.listExtractions(storyId)]);
    setEntries(entryList);
    setExtractions(extractionList);
    if (entryList.length > 0) {
      const first = entryList[0];
      setSelectedId(first.id);
      setEditor({
        id: first.id,
        displayName: first.displayName || "",
        entryKey: first.entryKey || "",
        category: first.category || "custom",
        content: first.content || "",
        keywords: Array.isArray(first.keywords) ? first.keywords : [],
        priority: Number(first.priority ?? 0),
        tokenBudget: Number(first.tokenBudget ?? 500),
        insertionPosition: first.insertionPosition || "before_scene",
        enabled: Boolean(first.enabled ?? true),
      });
    } else {
      setSelectedId("");
      setEditor(newEntry());
    }
  };

  const loadPreview = async () => {
    if (!storyId) return;
    const data = await api.v2.context.previewContext(storyId, { tokenBudget: 1200 });
    setPreview(data);
  };

  useEffect(() => {
    if (!storyId) return;
    setLoading(true);
    Promise.all([loadLorebook(), loadPreview()])
      .catch((error: any) => toast({ variant: "destructive", title: t("lorebook.loadFailed"), description: localizedErrorMessage(error, "lorebook.loadFailed") }))
      .finally(() => setLoading(false));
  }, [storyId]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return entries.filter((entry) => {
      const categoryMatch = filter === "all" || entry.category === filter;
      const textMatch =
        !term ||
        String(entry.displayName || "").toLowerCase().includes(term) ||
        String(entry.content || "").toLowerCase().includes(term) ||
        (Array.isArray(entry.keywords) && entry.keywords.some((keyword: string) => keyword.toLowerCase().includes(term)));
      return categoryMatch && textMatch;
    });
  }, [entries, filter, search]);

  const pickEntry = (entry: any) => {
    setSelectedId(entry.id);
    setEditor({
      id: entry.id,
      displayName: entry.displayName || "",
      entryKey: entry.entryKey || "",
      category: entry.category || "custom",
      content: entry.content || "",
      keywords: Array.isArray(entry.keywords) ? entry.keywords : [],
      priority: Number(entry.priority ?? 0),
      tokenBudget: Number(entry.tokenBudget ?? 500),
      insertionPosition: entry.insertionPosition || "before_scene",
      enabled: Boolean(entry.enabled ?? true),
    });
  };

  const saveEntry = async () => {
    if (!storyId) return;
    if (!editor.displayName.trim()) {
      toast({ variant: "destructive", title: t("lorebook.nameRequired") });
      return;
    }
    setLoading(true);
    try {
      const payload = {
        ...editor,
        entryKey: editor.entryKey.trim() || undefined,
      };
      if (editor.id) {
        await api.v2.context.updateLorebook(storyId, editor.id, payload);
      } else {
        await api.v2.context.createLorebook(storyId, payload);
      }
      await loadLorebook();
      toast({ title: t("lorebook.saved") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
    } finally {
      setLoading(false);
    }
  };

  const removeEntry = async () => {
    if (!storyId || !editor.id) return;
    setLoading(true);
    try {
      await api.v2.context.deleteLorebook(storyId, editor.id);
      await loadLorebook();
      toast({ title: t("lorebook.deleted") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("errors.deleteFailed"), description: localizedErrorMessage(error, "errors.deleteFailed") });
    } finally {
      setLoading(false);
    }
  };

  const importEntries = async () => {
    if (!storyId) return;
    setLoading(true);
    try {
      const parsed = JSON.parse(importPayload);
      if (!Array.isArray(parsed)) {
        throw new Error(t("lorebook.jsonArrayRequired"));
      }
      await api.v2.context.importLorebook(storyId, parsed);
      await loadLorebook();
      toast({ title: t("lorebook.imported") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("lorebook.importFailed"), description: localizedErrorMessage(error, "lorebook.importFailed") });
    } finally {
      setLoading(false);
    }
  };

  const reviewExtraction = async (id: string, action: "approved" | "rejected") => {
    if (!storyId) return;
    try {
      await api.v2.context.reviewExtraction(storyId, id, action);
      await loadLorebook();
      toast({ title: action === "approved" ? t("lorebook.approved") : t("lorebook.rejected") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("errors.operationFailed"), description: localizedErrorMessage(error, "errors.operationFailed") });
    }
  };

  const reviewExtractionBatch = async (action: "approved" | "rejected", mode: "all" | "high" | "low") => {
    if (!storyId) return;
    const targets = extractions.filter((item) => {
      if (mode === "all") return true;
      const confidence = Number(item.confidence ?? 0);
      if (mode === "high") return confidence >= 0.8;
      return confidence <= 0.5;
    });
    if (!targets.length) {
      toast({ title: t("lorebook.noMatchingExtractions") });
      return;
    }
    setLoading(true);
    try {
      await Promise.all(targets.map((item) => api.v2.context.reviewExtraction(storyId, String(item.id), action)));
      await loadLorebook();
      toast({ title: action === "approved" ? t("lorebook.batchApproved") : t("lorebook.batchRejected"), description: t("lorebook.batchProcessed", { count: targets.length }) });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("lorebook.batchReviewFailed"), description: localizedErrorMessage(error, "lorebook.batchReviewFailed") });
    } finally {
      setLoading(false);
    }
  };

  const syncGraph = async () => {
    if (!storyId) return;
    try {
      await api.v2.context.syncGraph(storyId);
      toast({ title: t("lorebook.graphSynced") });
    } catch (error: any) {
      toast({ variant: "destructive", title: t("lorebook.syncFailed"), description: localizedErrorMessage(error, "lorebook.syncFailed") });
    }
  };

  const used = Number(preview?.tokenUsed ?? 0);
  const budget = Number(preview?.tokenBudget ?? 1);
  const usagePercent = Math.min(100, Math.round((used / Math.max(budget, 1)) * 100));
  const systemPromptEntries = Array.isArray(preview?.systemPromptEntries) ? preview.systemPromptEntries : [];
  const beforeSceneEntries = Array.isArray(preview?.beforeSceneEntries) ? preview.beforeSceneEntries : [];
  const afterSceneEntries = Array.isArray(preview?.afterSceneEntries) ? preview.afterSceneEntries : [];
  const graphRelations = Array.isArray(preview?.graphRelations) ? preview.graphRelations : [];
  const activeCharacters = Array.isArray(preview?.activeCharacters) ? preview.activeCharacters : [];

  return (
    <div className="h-full flex flex-col gap-4">
      <Card>
        <CardContent className="pt-6 flex flex-wrap items-end gap-3">
          <div className="w-[280px]">
            <Label>{t("common.story")}</Label>
            <Select value={storyId} onValueChange={setStoryId}>
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
          <Button variant="secondary" onClick={loadLorebook} disabled={!storyId || loading}>
            {t("common.refresh")}
          </Button>
          <Button variant="outline" onClick={syncGraph} disabled={!storyId || loading}>
            {t("lorebook.syncGraph")}
          </Button>
          <Button variant="outline" onClick={loadPreview} disabled={!storyId || loading}>
            {t("lorebook.refreshContextPreview")}
          </Button>
        </CardContent>
      </Card>

      <div className="grid flex-1 gap-4 lg:grid-cols-[360px_1fr] min-h-0">
        <Card className="min-h-0 flex flex-col">
          <CardHeader>
            <CardTitle>{t("lorebook.entries")}</CardTitle>
            <CardDescription>{t("lorebook.entriesDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 flex-1 min-h-0 flex flex-col">
            <div className="grid grid-cols-[1fr_130px] gap-2">
              <Input placeholder={t("lorebook.searchPlaceholder")} value={search} onChange={(event) => setSearch(event.target.value)} />
              <Select value={filter} onValueChange={setFilter}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t("lorebook.categoryAll")}</SelectItem>
                  <SelectItem value="character">{t("lorebook.categoryCharacter")}</SelectItem>
                  <SelectItem value="location">{t("lorebook.categoryLocation")}</SelectItem>
                  <SelectItem value="event">{t("lorebook.categoryEvent")}</SelectItem>
                  <SelectItem value="item">{t("lorebook.categoryItem")}</SelectItem>
                  <SelectItem value="concept">{t("lorebook.categoryConcept")}</SelectItem>
                  <SelectItem value="custom">{t("lorebook.categoryCustom")}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button
              variant="outline"
              onClick={() => {
                setSelectedId("");
                setEditor(newEntry());
              }}
            >
              {t("lorebook.newEntry")}
            </Button>
            <ScrollArea className="flex-1 border rounded-md">
              <div className="p-2 space-y-2">
                {filtered.map((entry) => (
                  <button
                    key={entry.id}
                    onClick={() => pickEntry(entry)}
                    className={`w-full rounded-md border px-3 py-2 text-left transition ${
                      selectedId === entry.id ? "border-primary bg-primary/10" : "hover:bg-muted"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium">{entry.displayName}</span>
                      <Badge variant="secondary">{entry.category}</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{entry.content}</p>
                  </button>
                ))}
                {!filtered.length && <p className="text-sm text-muted-foreground p-2">{t("lorebook.noEntries")}</p>}
              </div>
            </ScrollArea>
            <Separator />
            <div className="space-y-2">
              <Label>{t("lorebook.batchImportJson")}</Label>
              <Textarea
                className="min-h-[120px] font-mono text-xs"
                value={importPayload}
                onChange={(event) => setImportPayload(event.target.value)}
                placeholder='[{"displayName":"主角","content":"..."}]'
              />
              <Button variant="secondary" onClick={importEntries} disabled={!storyId || loading}>
                {t("lorebook.importEntries")}
              </Button>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-4 min-h-0 flex flex-col">
          <Card>
            <CardHeader>
              <CardTitle>{t("lorebook.editor")}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 md:grid-cols-2">
              <div className="space-y-2">
                <Label>{t("lorebook.entryName")}</Label>
                <Input value={editor.displayName} onChange={(event) => setEditor((prev) => ({ ...prev, displayName: event.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>entryKey</Label>
                <Input value={editor.entryKey} onChange={(event) => setEditor((prev) => ({ ...prev, entryKey: event.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t("lorebook.category")}</Label>
                <Select value={editor.category} onValueChange={(value) => setEditor((prev) => ({ ...prev, category: value }))}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="character">{t("lorebook.categoryCharacter")}</SelectItem>
                    <SelectItem value="location">{t("lorebook.categoryLocation")}</SelectItem>
                    <SelectItem value="event">{t("lorebook.categoryEvent")}</SelectItem>
                    <SelectItem value="item">{t("lorebook.categoryItem")}</SelectItem>
                    <SelectItem value="concept">{t("lorebook.categoryConcept")}</SelectItem>
                    <SelectItem value="custom">{t("lorebook.categoryCustom")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t("lorebook.insertionPosition")}</Label>
                <Select
                  value={editor.insertionPosition}
                  onValueChange={(value) => setEditor((prev) => ({ ...prev, insertionPosition: value }))}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="before_scene">{t("lorebook.beforeScene")}</SelectItem>
                    <SelectItem value="after_scene">{t("lorebook.afterScene")}</SelectItem>
                    <SelectItem value="system_prompt">System Prompt</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t("lorebook.priority")}</Label>
                <Input
                  type="number"
                  min={0}
                  max={100}
                  value={editor.priority}
                  onChange={(event) => setEditor((prev) => ({ ...prev, priority: Number(event.target.value || 0) }))}
                />
              </div>
              <div className="space-y-2">
                <Label>{t("lorebook.tokenBudget")}</Label>
                <Input
                  type="number"
                  min={1}
                  value={editor.tokenBudget}
                  onChange={(event) => setEditor((prev) => ({ ...prev, tokenBudget: Number(event.target.value || 500) }))}
                />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>{t("lorebook.content")}</Label>
                <Textarea
                  className="min-h-[150px]"
                  value={editor.content}
                  onChange={(event) => setEditor((prev) => ({ ...prev, content: event.target.value }))}
                />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>{t("lorebook.keywords")}</Label>
                <div className="flex gap-2">
                  <Input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} placeholder={t("lorebook.keywordPlaceholder")} />
                  <Button
                    variant="outline"
                    onClick={() => {
                      const value = keywordInput.trim();
                      if (!value) return;
                      if (!editor.keywords.includes(value)) {
                        setEditor((prev) => ({ ...prev, keywords: [...prev.keywords, value] }));
                      }
                      setKeywordInput("");
                    }}
                  >
                    {t("common.add")}
                  </Button>
                </div>
                <div className="flex flex-wrap gap-2">
                  {editor.keywords.map((keyword) => (
                    <Badge
                      key={keyword}
                      className="cursor-pointer"
                      variant="outline"
                      onClick={() => setEditor((prev) => ({ ...prev, keywords: prev.keywords.filter((item) => item !== keyword) }))}
                    >
                      {keyword} ×
                    </Badge>
                  ))}
                </div>
              </div>
              <div className="flex items-center gap-2 md:col-span-2">
                <Switch checked={editor.enabled} onCheckedChange={(checked) => setEditor((prev) => ({ ...prev, enabled: checked }))} />
                <Label>{t("lorebook.enableEntry")}</Label>
              </div>
              <div className="md:col-span-2 flex gap-2">
                <Button onClick={saveEntry} disabled={!storyId || loading}>
                  {t("lorebook.saveEntry")}
                </Button>
                <Button variant="destructive" onClick={removeEntry} disabled={!editor.id || loading}>
                  {t("lorebook.deleteEntry")}
                </Button>
              </div>
            </CardContent>
          </Card>

          <Tabs defaultValue="preview" className="flex-1 min-h-0 flex flex-col">
            <TabsList className="w-[360px]">
              <TabsTrigger value="preview">{t("lorebook.contextPreview")}</TabsTrigger>
              <TabsTrigger value="extractions">{t("lorebook.entityReview")}</TabsTrigger>
            </TabsList>
            <TabsContent value="preview" className="flex-1 min-h-0 m-0 mt-3">
              <Card className="h-full">
                <CardHeader>
                  <CardTitle>Context Preview</CardTitle>
                  <CardDescription>
                    {t("lorebook.tokenUsage", { used, budget })}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  <Progress value={usagePercent} />
                  <div className="grid md:grid-cols-2 gap-3 text-xs">
                    <div className="rounded border p-2">
                      <div className="text-muted-foreground">{t("lorebook.chapter")}</div>
                      <div className="font-medium">{t("lorebook.chapterNumber", { count: preview?.chapterIndex || 1 })}</div>
                    </div>
                    <div className="rounded border p-2">
                      <div className="text-muted-foreground">{t("lorebook.scene")}</div>
                      <div className="font-medium">Sc. {preview?.sceneIndex || 1}</div>
                    </div>
                  </div>
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.systemPromptEntries")}</div>
                    {systemPromptEntries.map((entry: any) => (
                      <div key={entry.id} className="rounded-md border p-2">
                        <div className="flex items-center justify-between">
                          <span className="font-medium">{entry.displayName}</span>
                          <Badge variant="secondary">{entry.tokenBudget} tokens</Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{entry.content}</p>
                      </div>
                    ))}
                    {!systemPromptEntries.length && <p className="text-sm text-muted-foreground">{t("lorebook.noSystemPromptEntries")}</p>}
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.beforeSceneEntries")}</div>
                    {beforeSceneEntries.map((entry: any) => (
                      <div key={entry.id} className="rounded-md border p-2">
                        <div className="flex items-center justify-between">
                          <span className="font-medium">{entry.displayName}</span>
                          <Badge variant="secondary">{entry.tokenBudget} tokens</Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{entry.content}</p>
                      </div>
                    ))}
                    {!beforeSceneEntries.length && <p className="text-sm text-muted-foreground">{t("lorebook.noBeforeSceneEntries")}</p>}
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.afterSceneEntries")}</div>
                    {afterSceneEntries.map((entry: any) => (
                      <div key={entry.id} className="rounded-md border p-2">
                        <div className="flex items-center justify-between">
                          <span className="font-medium">{entry.displayName}</span>
                          <Badge variant="secondary">{entry.tokenBudget} tokens</Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{entry.content}</p>
                      </div>
                    ))}
                    {!afterSceneEntries.length && <p className="text-sm text-muted-foreground">{t("lorebook.noAfterSceneEntries")}</p>}
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.graphRelations")}</div>
                    {graphRelations.map((relation: string, index: number) => (
                      <div key={`${relation}-${index}`} className="rounded-md border p-2 text-xs">
                        {relation}
                      </div>
                    ))}
                    {!graphRelations.length && <p className="text-sm text-muted-foreground">{t("lorebook.noGraphRelations")}</p>}
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.recentSummary")}</div>
                    <div className="rounded-md border p-2 text-xs text-muted-foreground">
                      {preview?.recentSummary || t("lorebook.noRecentSummary")}
                    </div>
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.activeCharacters")}</div>
                    <div className="flex flex-wrap gap-2">
                      {activeCharacters.map((character: string) => (
                        <Badge key={character} variant="outline">
                          {character}
                        </Badge>
                      ))}
                    </div>
                    {!activeCharacters.length && <p className="text-sm text-muted-foreground">{t("lorebook.noActiveCharacters")}</p>}
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="font-medium text-sm">{t("lorebook.pendingExtractions")}</div>
                    {(preview?.pendingExtractions || []).map((item: any) => (
                      <div key={item.id} className="rounded-md border p-2">
                        <div className="flex items-center justify-between">
                          <span>{item.entityName}</span>
                          <Badge variant="outline">{item.entityType}</Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{item.sourceText}</p>
                      </div>
                    ))}
                    {!preview?.pendingExtractions?.length && <p className="text-sm text-muted-foreground">{t("lorebook.noPendingExtractions")}</p>}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
            <TabsContent value="extractions" className="flex-1 min-h-0 m-0 mt-3">
              <Card className="h-full">
                <CardHeader>
                  <CardTitle>{t("lorebook.entityExtractionReview")}</CardTitle>
                  <CardDescription>{t("lorebook.entityExtractionReviewDesc")}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="outline" onClick={() => void reviewExtractionBatch("approved", "high")} disabled={loading}>
                      {t("lorebook.approveHighConfidence")}
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => void reviewExtractionBatch("rejected", "low")} disabled={loading}>
                      {t("lorebook.rejectLowConfidence")}
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => void reviewExtractionBatch("approved", "all")} disabled={loading}>
                      {t("lorebook.approveAll")}
                    </Button>
                  </div>
                  <ScrollArea className="h-[260px] rounded-md border">
                    <div className="p-3 space-y-2">
                      {extractions.map((item) => (
                        <div key={item.id} className="border rounded-md p-2">
                          <div className="flex items-center justify-between">
                            <div>
                              <span className="font-medium">{item.entityName}</span>
                              <Badge variant="outline" className="ml-2">
                                {item.entityType}
                              </Badge>
                            </div>
                            <Badge>{Math.round(Number(item.confidence ?? 0) * 100)}%</Badge>
                          </div>
                          <p className="text-xs text-muted-foreground mt-1">{item.sourceText}</p>
                          <pre className="mt-1 rounded bg-muted p-2 text-[11px] overflow-auto">{JSON.stringify(item.attributes || {}, null, 2)}</pre>
                          <div className="flex gap-2 mt-2">
                            <Button size="sm" onClick={() => reviewExtraction(item.id, "approved")} disabled={item.reviewAction === "approved"}>
                              {t("lorebook.approve")}
                            </Button>
                            <Button
                              size="sm"
                              variant="secondary"
                              onClick={() => reviewExtraction(item.id, "rejected")}
                              disabled={item.reviewAction === "rejected"}
                            >
                              {t("lorebook.reject")}
                            </Button>
                          </div>
                        </div>
                      ))}
                      {!extractions.length && <p className="text-sm text-muted-foreground">{t("lorebook.noPendingExtractions")}</p>}
                    </div>
                  </ScrollArea>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
  );
};

export default LorebookPanel;
