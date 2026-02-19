
import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/mock-api";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useToast } from "@/components/ui/use-toast";
import { Slider } from "@/components/ui/slider";
import { PolarAngleAxis, PolarGrid, Radar, RadarChart, ResponsiveContainer } from "recharts";

const STYLE_DIMENSIONS = [
  { key: "formality", label: "正式程度", minHint: "口语", maxHint: "书面" },
  { key: "sentence_length", label: "句长偏好", minHint: "短句", maxHint: "长句" },
  { key: "vocabulary_richness", label: "词汇丰富度", minHint: "朴素", maxHint: "文学" },
  { key: "pacing", label: "叙事节奏", minHint: "舒缓", maxHint: "紧凑" },
  { key: "descriptiveness", label: "描写密度", minHint: "简洁", maxHint: "浓墨" },
  { key: "dialogue_ratio", label: "对话占比", minHint: "叙述", maxHint: "对话" },
  { key: "emotional_intensity", label: "情感强度", minHint: "克制", maxHint: "浓烈" },
  { key: "rhetoric_frequency", label: "修辞频率", minHint: "直白", maxHint: "修辞" },
] as const;

const SCENE_MODES = ["action", "dialogue", "introspection", "description", "flashback"];
const VOCABULARY_LEVELS = ["colloquial", "neutral", "literary", "formal"];
const DEFAULT_EMOTIONS = ["冷静", "愤怒", "悲伤", "讽刺", "坚定"];

const buildDefaultDimensions = (score = 6) =>
  STYLE_DIMENSIONS.reduce(
    (acc, item) => ({
      ...acc,
      [item.key]: score,
    }),
    {} as Record<string, number>,
  );

type DialogueSample = { context: string; line: string };

interface StyleProfilesProps {
  initialStoryId?: string;
}

const normalizeDimensionScore = (value: any, fallback = 6) => {
  const num = Number(value);
  if (!Number.isFinite(num)) return fallback;
  if (num <= 10 && num >= 1) return Math.round(num);
  if (num >= 0 && num <= 100) return Math.max(1, Math.min(10, Math.round(num / 10)));
  return fallback;
};

const toDialogueSamples = (raw: any): DialogueSample[] => {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => {
      if (typeof item === "string") return { context: "常规", line: item };
      if (item && typeof item === "object") {
        return { context: String(item.context || item.emotion || "常规"), line: String(item.line || item.content || "") };
      }
      return null;
    })
    .filter((item): item is DialogueSample => !!item && !!item.line.trim());
};

const mapAnalysisDimensions = (analysisResult: any) => {
  const raw = analysisResult?.result || analysisResult || {};
  const mapped = buildDefaultDimensions(6);
  const keyMap: Record<string, string> = {
    formality: "formality",
    sentence_length: "sentence_length",
    vocabulary_richness: "vocabulary_richness",
    pacing: "pacing",
    descriptiveness: "descriptiveness",
    dialogue_ratio: "dialogue_ratio",
    emotional_intensity: "emotional_intensity",
    rhetoric_frequency: "rhetoric_frequency",
    rhythm: "pacing",
    imagery: "descriptiveness",
    dialogueDensity: "dialogue_ratio",
    tension: "emotional_intensity",
    emotion: "emotional_intensity",
    complexity: "vocabulary_richness",
    descriptionDensity: "descriptiveness",
  };
  Object.entries(raw).forEach(([key, value]) => {
    const target = keyMap[key];
    if (target) mapped[target] = normalizeDimensionScore(value, mapped[target]);
  });
  return mapped;
};

const emptyVoiceForm = {
  characterCardId: "",
  speechPattern: "",
  vocabularyLevel: "colloquial",
  catchphrases: [],
  emotionalRange: [],
  dialect: "",
  sampleDialogues: [] as DialogueSample[],
};

const emptyProfileForm = {
  name: "叙事基调画像",
  profileType: "narrative",
  sampleText: "",
  dimensions: buildDefaultDimensions(6),
  sceneOverrides: SCENE_MODES.map((mode) => ({ sceneType: mode, dimensions: buildDefaultDimensions(6) })),
  dimensionNotes: {},
};

const StyleProfiles = ({ initialStoryId }: StyleProfilesProps) => {
  const { toast } = useToast();
  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState(initialStoryId || "");
  const [characters, setCharacters] = useState<any[]>([]);
  const [profiles, setProfiles] = useState<any[]>([]);
  const [voices, setVoices] = useState<any[]>([]);
  const [selectedProfileId, setSelectedProfileId] = useState("");
  const [selectedVoiceId, setSelectedVoiceId] = useState("");
  const [compareProfileA, setCompareProfileA] = useState("");
  const [compareProfileB, setCompareProfileB] = useState("");
  const [analysisText, setAnalysisText] = useState("夜风掠过城墙，远处钟声在雾中回荡，巡夜人攥紧披风，沉默地走向灯影尽头。");
  const [analysisResult, setAnalysisResult] = useState<any>(null);
  const [profileForm, setProfileForm] = useState<any>(emptyProfileForm);
  const [voiceForm, setVoiceForm] = useState<any>(emptyVoiceForm);
  const [catchphraseInput, setCatchphraseInput] = useState("");
  const [emotionInput, setEmotionInput] = useState("");
  const [sampleContextInput, setSampleContextInput] = useState("日常");
  const [sampleLineInput, setSampleLineInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [voicePreview, setVoicePreview] = useState("");

  useEffect(() => {
    api.stories
      .list()
      .then((list) => {
        setStories(list);
        if (!storyId && list.length > 0) {
          setStoryId(initialStoryId && list.some((story) => story.id === initialStoryId) ? initialStoryId : list[0].id);
        }
      })
      .catch((error: any) => toast({ variant: "destructive", title: "加载故事失败", description: error.message }));
  }, []);

  const loadData = async () => {
    if (!storyId) return;
    const [profileList, voiceList, characterList] = await Promise.all([
      api.v2.style.listProfiles(storyId),
      api.v2.style.listVoices(storyId),
      api.stories.listCharacters(storyId),
    ]);
    setProfiles(profileList);
    setVoices(voiceList);
    setCharacters(characterList);
  };

  useEffect(() => {
    setSelectedProfileId("");
    setSelectedVoiceId("");
    setCompareProfileA("");
    setCompareProfileB("");
    if (!storyId) return;
    setLoading(true);
    loadData()
      .catch((error: any) => toast({ variant: "destructive", title: "加载风格数据失败", description: error.message }))
      .finally(() => setLoading(false));
  }, [storyId]);

  useEffect(() => {
    if (!profiles.length) return;
    if (!compareProfileA) setCompareProfileA(String(profiles[0].id));
    if (!compareProfileB) setCompareProfileB(String(profiles[Math.min(1, profiles.length - 1)]?.id || profiles[0].id));
  }, [profiles, compareProfileA, compareProfileB]);

  useEffect(() => {
    if (!selectedProfileId) return;
    const profile = profiles.find((item) => String(item.id) === String(selectedProfileId));
    if (!profile) return;
    const baseDimensions = buildDefaultDimensions(6);
    setProfileForm({
      id: profile.id,
      name: profile.name || "",
      profileType: profile.profileType || "narrative",
      sampleText: profile.sampleText || "",
      dimensions: {
        ...baseDimensions,
        ...Object.fromEntries(STYLE_DIMENSIONS.map((item) => [item.key, normalizeDimensionScore(profile?.dimensions?.[item.key], baseDimensions[item.key])])),
      },
      sceneOverrides:
        profile.sceneOverrides && profile.sceneOverrides.length
          ? profile.sceneOverrides.map((override: any) => ({
              sceneType: override.sceneType || "action",
              dimensions: {
                ...baseDimensions,
                ...Object.fromEntries(STYLE_DIMENSIONS.map((item) => [item.key, normalizeDimensionScore(override?.dimensions?.[item.key], baseDimensions[item.key])])),
              },
            }))
          : SCENE_MODES.map((mode) => ({ sceneType: mode, dimensions: baseDimensions })),
      dimensionNotes: profile.dimensionNotes || {},
    });
  }, [selectedProfileId, profiles]);

  useEffect(() => {
    if (!selectedVoiceId) return;
    const voice = voices.find((item) => String(item.id) === String(selectedVoiceId));
    if (!voice) return;
    setVoicePreview("");
    setVoiceForm({
      id: voice.id,
      characterCardId: String(voice.characterCardId || ""),
      speechPattern: voice.speechPattern || "",
      vocabularyLevel: voice.vocabularyLevel || "colloquial",
      catchphrases: Array.isArray(voice.catchphrases) ? voice.catchphrases : [],
      emotionalRange: Array.isArray(voice.emotionalRange) ? voice.emotionalRange : [],
      dialect: voice.dialect || "",
      sampleDialogues: toDialogueSamples(voice.sampleDialogues),
    });
  }, [selectedVoiceId, voices]);

  const activeProfile = useMemo(() => profiles.find((profile) => profile.isActive), [profiles]);
  const profileA = profiles.find((item) => String(item.id) === String(compareProfileA));
  const profileB = profiles.find((item) => String(item.id) === String(compareProfileB));

  const compareData = useMemo(
    () => STYLE_DIMENSIONS.map((item) => ({ metric: item.label, A: normalizeDimensionScore(profileA?.dimensions?.[item.key], 6), B: normalizeDimensionScore(profileB?.dimensions?.[item.key], 6) })),
    [profileA, profileB],
  );

  const saveProfile = async () => {
    if (!storyId) return;
    if (!profileForm.name?.trim()) {
      toast({ variant: "destructive", title: "请填写画像名称" });
      return;
    }
    try {
      if (profileForm.id) await api.v2.style.updateProfile(storyId, profileForm.id, profileForm);
      else await api.v2.style.createProfile(storyId, profileForm);
      await loadData();
      toast({ title: "风格画像已保存" });
      setVoicePreview("");
    } catch (error: any) {
      toast({ variant: "destructive", title: "保存失败", description: error.message });
    }
  };

  const removeProfile = async () => {
    if (!storyId || !profileForm.id) return;
    try {
      await api.v2.style.deleteProfile(storyId, profileForm.id);
      await loadData();
      setSelectedProfileId("");
      setProfileForm(emptyProfileForm);
      toast({ title: "画像已删除" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "删除失败", description: error.message });
    }
  };

  const activateProfile = async (profileId: string) => {
    if (!storyId) return;
    try {
      await api.v2.style.activateProfile(storyId, profileId);
      await loadData();
      toast({ title: "已激活风格画像" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "激活失败", description: error.message });
    }
  };

  const runAnalysis = async () => {
    try {
      const result = await api.v2.style.analyze({ sourceType: "uploaded_text", sourceReference: "settings-style", sampleText: analysisText });
      setAnalysisResult(result);
      toast({ title: "风格分析完成" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "分析失败", description: error.message });
    }
  };

  const createFromAnalysis = async () => {
    if (!storyId || !analysisResult) return;
    try {
      const analysisPayload = analysisResult.result || {};
      await api.v2.style.createProfile(storyId, {
        name: String(analysisPayload.suggestedProfileName || `分析画像-${new Date().toLocaleTimeString()}`),
        profileType: "narrative",
        dimensions: mapAnalysisDimensions(analysisResult),
        aiAnalysis: analysisPayload,
        sampleText: analysisText,
      });
      await loadData();
      toast({ title: "已根据分析创建画像" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "创建失败", description: error.message });
    }
  };

  const saveVoice = async () => {
    if (!storyId) return;
    if (!voiceForm.characterCardId) {
      toast({ variant: "destructive", title: "请选择角色" });
      return;
    }
    try {
      const payload = { ...voiceForm, sampleDialogues: (voiceForm.sampleDialogues || []).filter((item: DialogueSample) => item.line?.trim()) };
      const savedVoice = voiceForm.id
        ? await api.v2.style.updateVoice(storyId, voiceForm.id, payload)
        : await api.v2.style.createVoice(storyId, payload);
      await loadData();
      const nextVoiceId = String(savedVoice?.id || voiceForm.id || "");
      if (nextVoiceId) setSelectedVoiceId(nextVoiceId);
      toast({ title: "角色声音已保存" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "保存失败", description: error.message });
    }
  };

  const removeVoice = async () => {
    if (!storyId || !voiceForm.id) return;
    try {
      await api.v2.style.deleteVoice(storyId, voiceForm.id);
      await loadData();
      setSelectedVoiceId("");
      setVoiceForm(emptyVoiceForm);
      setVoicePreview("");
      toast({ title: "角色声音已删除" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "删除失败", description: error.message });
    }
  };

  const generateVoice = async () => {
    if (!storyId || !voiceForm.id) return;
    const selectedCharacter = characters.find((character) => String(character.id) === String(voiceForm.characterCardId));
    try {
      await api.v2.style.generateVoice(storyId, voiceForm.id, { characterName: selectedCharacter?.name || "角色" });
      await loadData();
      toast({ title: "AI 已生成声音画像" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "生成失败", description: error.message });
    }
  };

  const previewVoice = async () => {
    if (!voiceForm.characterCardId) {
      toast({ variant: "destructive", title: "请先选择角色" });
      return;
    }
    const selectedCharacter = characters.find((character) => String(character.id) === String(voiceForm.characterCardId));
    const characterName = selectedCharacter?.name || "角色";
    const catchphrase = (voiceForm.catchphrases || [])[0] || "让我想想";
    const mood = (voiceForm.emotionalRange || [])[0] || "平静";
    const sample = (voiceForm.sampleDialogues || [])[0]?.line || "先看线索，再下结论。";
    const dialect = voiceForm.dialect ? `（${voiceForm.dialect}口音）` : "";
    const pattern = voiceForm.speechPattern?.trim() || "说话干脆，先抛结论再补细节";
    setVoicePreview(
      `${dialect}${characterName}压低声音说：“${catchphrase}，${sample}”\n` +
      `他在${mood}情绪下保持${voiceForm.vocabularyLevel || "colloquial"}词汇风格，整体说话模式为：${pattern}。`,
    );
    toast({ title: "声音预览已生成" });
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="pt-6 flex flex-wrap items-end gap-3">
          <div className="w-[300px]">
            <Label>故事</Label>
            <Select value={storyId} onValueChange={setStoryId}>
              <SelectTrigger>
                <SelectValue placeholder="选择故事" />
              </SelectTrigger>
              <SelectContent>
                {stories.map((story) => (
                  <SelectItem key={story.id} value={story.id}>{story.title}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button variant="secondary" onClick={() => void loadData()} disabled={!storyId || loading}>刷新</Button>
          {activeProfile ? <Badge>当前激活：{activeProfile.name}</Badge> : <Badge variant="outline">暂无激活画像</Badge>}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>风格画像管理</CardTitle>
            <CardDescription>覆盖 AC-1/2/3/4/5：CRUD、维度控制、分析转画像。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-[1fr_auto] gap-2">
              <Select value={selectedProfileId} onValueChange={setSelectedProfileId}>
                <SelectTrigger><SelectValue placeholder="选择画像进行编辑" /></SelectTrigger>
                <SelectContent>{profiles.map((profile) => <SelectItem key={profile.id} value={String(profile.id)}>{profile.name}</SelectItem>)}</SelectContent>
              </Select>
              <Button variant="outline" onClick={() => { setSelectedProfileId(""); setProfileForm(emptyProfileForm); }}>新建</Button>
            </div>
            <div className="grid md:grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>画像名称</Label>
                <Input value={profileForm.name || ""} onChange={(event) => setProfileForm((prev: any) => ({ ...prev, name: event.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>类型</Label>
                <Select value={profileForm.profileType || "narrative"} onValueChange={(value) => setProfileForm((prev: any) => ({ ...prev, profileType: value }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="narrative">叙事</SelectItem>
                    <SelectItem value="dialogue">对话</SelectItem>
                    <SelectItem value="description">描写</SelectItem>
                    <SelectItem value="global">全局</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid md:grid-cols-2 gap-2">
              {STYLE_DIMENSIONS.map((item) => (
                <div key={item.key} className="space-y-1 rounded border p-2">
                  <div className="flex items-center justify-between">
                    <Label>{item.label}</Label>
                    <Badge variant="outline">{Number(profileForm.dimensions?.[item.key] ?? 6)}</Badge>
                  </div>
                  <div className="text-[11px] text-muted-foreground flex justify-between"><span>{item.minHint}</span><span>{item.maxHint}</span></div>
                  <Slider min={1} max={10} step={1} value={[Number(profileForm.dimensions?.[item.key] ?? 6)]} onValueChange={(value) => setProfileForm((prev: any) => ({ ...prev, dimensions: { ...(prev.dimensions || {}), [item.key]: Number(value[0] ?? 6) } }))} />
                  <Input placeholder="该维度说明（可选）" value={String(profileForm.dimensionNotes?.[item.key] || "")} onChange={(event) => setProfileForm((prev: any) => ({ ...prev, dimensionNotes: { ...(prev.dimensionNotes || {}), [item.key]: event.target.value } }))} />
                </div>
              ))}
            </div>
            <Separator />
            <div className="space-y-2">
              <Label>场景模式覆盖</Label>
              <div className="space-y-2">
                {(profileForm.sceneOverrides || []).map((override: any, index: number) => (
                  <div key={`${override.sceneType}-${index}`} className="rounded border p-2 space-y-2">
                    <div className="font-medium text-sm">{override.sceneType}</div>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                      {STYLE_DIMENSIONS.map((dim) => (
                        <div key={`${override.sceneType}-${dim.key}`} className="space-y-1">
                          <Label className="text-xs">{dim.label}</Label>
                          <Slider min={1} max={10} step={1} value={[Number(override.dimensions?.[dim.key] ?? 6)]} onValueChange={(value) => setProfileForm((prev: any) => { const nextOverrides = [...(prev.sceneOverrides || [])]; const target = { ...nextOverrides[index] }; target.dimensions = { ...(target.dimensions || {}), [dim.key]: Number(value[0] ?? 6) }; nextOverrides[index] = target; return { ...prev, sceneOverrides: nextOverrides }; })} />
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <Label>参考样文</Label>
              <Textarea className="min-h-[120px]" value={profileForm.sampleText || ""} onChange={(event) => setProfileForm((prev: any) => ({ ...prev, sampleText: event.target.value }))} />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button onClick={saveProfile} disabled={!storyId}>保存画像</Button>
              <Button variant="outline" onClick={() => selectedProfileId && activateProfile(selectedProfileId)} disabled={!selectedProfileId}>激活画像</Button>
              <Button variant="destructive" onClick={removeProfile} disabled={!profileForm.id}>删除画像</Button>
            </div>
            <Separator />
            <div className="space-y-2">
              <Label>风格分析文本</Label>
              <Textarea className="min-h-[120px]" value={analysisText} onChange={(event) => setAnalysisText(event.target.value)} />
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => void runAnalysis()}>分析</Button>
                <Button variant="outline" onClick={() => void createFromAnalysis()} disabled={!analysisResult}>分析结果转画像</Button>
              </div>
              {analysisResult && <div className="rounded-md border p-2 text-xs grid grid-cols-2 gap-1">{STYLE_DIMENSIONS.map((item) => <div key={item.key} className="flex items-center justify-between rounded bg-muted px-2 py-1"><span>{item.label}</span><span>{mapAnalysisDimensions(analysisResult)[item.key]}</span></div>)}</div>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>角色声音</CardTitle>
            <CardDescription>覆盖 AC-6/7/8：声音 CRUD + AI 生成 + 预览配置。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-[1fr_auto] gap-2">
              <Select value={selectedVoiceId} onValueChange={setSelectedVoiceId}>
                <SelectTrigger><SelectValue placeholder="选择声音配置" /></SelectTrigger>
                <SelectContent>{voices.map((voice) => <SelectItem key={voice.id} value={String(voice.id)}>{String(voice.characterCardId || voice.id).slice(0, 8)}</SelectItem>)}</SelectContent>
              </Select>
              <Button variant="outline" onClick={() => { setSelectedVoiceId(""); setVoiceForm(emptyVoiceForm); setVoicePreview(""); }}>新建</Button>
            </div>
            <div className="space-y-2">
              <Label>角色</Label>
              <Select value={voiceForm.characterCardId || ""} onValueChange={(value) => setVoiceForm((prev: any) => ({ ...prev, characterCardId: value }))}>
                <SelectTrigger><SelectValue placeholder="选择角色卡" /></SelectTrigger>
                <SelectContent>{characters.map((character) => <SelectItem key={character.id} value={String(character.id)}>{character.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="grid md:grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>词汇等级</Label>
                <Select value={voiceForm.vocabularyLevel || "colloquial"} onValueChange={(value) => setVoiceForm((prev: any) => ({ ...prev, vocabularyLevel: value }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>{VOCABULARY_LEVELS.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div className="space-y-2"><Label>方言/口音</Label><Input value={voiceForm.dialect || ""} onChange={(event) => setVoiceForm((prev: any) => ({ ...prev, dialect: event.target.value }))} /></div>
            </div>
            <div className="space-y-2"><Label>说话模式</Label><Textarea value={voiceForm.speechPattern || ""} onChange={(event) => setVoiceForm((prev: any) => ({ ...prev, speechPattern: event.target.value }))} className="min-h-[100px]" /></div>
            <div className="space-y-2">
              <Label>口头禅</Label>
              <div className="flex gap-2"><Input value={catchphraseInput} onChange={(event) => setCatchphraseInput(event.target.value)} placeholder="输入口头禅" /><Button variant="outline" onClick={() => { const value = catchphraseInput.trim(); if (!value) return; setVoiceForm((prev: any) => ({ ...prev, catchphrases: [...(prev.catchphrases || []), value] })); setCatchphraseInput(""); }}>添加</Button></div>
              <div className="flex flex-wrap gap-2">{(voiceForm.catchphrases || []).map((item: string) => <Badge key={item} variant="outline" className="cursor-pointer" onClick={() => setVoiceForm((prev: any) => ({ ...prev, catchphrases: (prev.catchphrases || []).filter((value: string) => value !== item) }))}>{item} ×</Badge>)}</div>
            </div>
            <div className="space-y-2">
              <Label>情绪表达范围</Label>
              <div className="flex gap-2"><Select value={emotionInput} onValueChange={setEmotionInput}><SelectTrigger><SelectValue placeholder="选择情绪" /></SelectTrigger><SelectContent>{DEFAULT_EMOTIONS.map((emotion) => <SelectItem key={emotion} value={emotion}>{emotion}</SelectItem>)}</SelectContent></Select><Button variant="outline" onClick={() => { const value = emotionInput.trim(); if (!value || (voiceForm.emotionalRange || []).includes(value)) return; setVoiceForm((prev: any) => ({ ...prev, emotionalRange: [...(prev.emotionalRange || []), value] })); setEmotionInput(""); }}>添加</Button></div>
              <div className="flex flex-wrap gap-2">{(voiceForm.emotionalRange || []).map((item: string) => <Badge key={item} variant="secondary" className="cursor-pointer" onClick={() => setVoiceForm((prev: any) => ({ ...prev, emotionalRange: (prev.emotionalRange || []).filter((value: string) => value !== item) }))}>{item} ×</Badge>)}</div>
            </div>
            <div className="space-y-2">
              <Label>示例对白</Label>
              <div className="grid grid-cols-3 gap-2"><Input value={sampleContextInput} onChange={(event) => setSampleContextInput(event.target.value)} placeholder="场景/情绪" /><Input className="col-span-2" value={sampleLineInput} onChange={(event) => setSampleLineInput(event.target.value)} placeholder="输入示例对白" /></div>
              <Button variant="outline" onClick={() => { const line = sampleLineInput.trim(); if (!line) return; const context = sampleContextInput.trim() || "常规"; setVoiceForm((prev: any) => ({ ...prev, sampleDialogues: [...(prev.sampleDialogues || []), { context, line }] })); setSampleLineInput(""); }}>添加示例</Button>
              <ScrollArea className="h-24 rounded-md border p-2"><div className="space-y-1 text-sm">{(voiceForm.sampleDialogues || []).map((item: DialogueSample, index: number) => <div key={`${item.context}-${item.line}-${index}`} className="rounded border p-1"><div className="text-xs text-muted-foreground">[{item.context}]</div><div>{item.line}</div></div>)}</div></ScrollArea>
            </div>
            <div className="flex flex-wrap gap-2"><Button onClick={() => void saveVoice()} disabled={!storyId}>保存声音</Button><Button variant="secondary" onClick={() => void generateVoice()} disabled={!voiceForm.id}>AI 生成</Button><Button variant="outline" onClick={() => void previewVoice()}>预览</Button><Button variant="destructive" onClick={() => void removeVoice()} disabled={!voiceForm.id}>删除声音</Button></div>
            {voicePreview && <div className="rounded border bg-muted/40 p-2 text-sm"><div className="font-medium mb-1">声音预览</div><p className="whitespace-pre-wrap text-muted-foreground">{voicePreview}</p></div>}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>风格画像对比</CardTitle><CardDescription>通过雷达图快速比较两个画像在 8 个维度上的差异。</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-2"><Label>画像 A</Label><Select value={compareProfileA} onValueChange={setCompareProfileA}><SelectTrigger><SelectValue placeholder="选择画像 A" /></SelectTrigger><SelectContent>{profiles.map((profile) => <SelectItem key={`A-${profile.id}`} value={String(profile.id)}>{profile.name}</SelectItem>)}</SelectContent></Select></div>
            <div className="space-y-2"><Label>画像 B</Label><Select value={compareProfileB} onValueChange={setCompareProfileB}><SelectTrigger><SelectValue placeholder="选择画像 B" /></SelectTrigger><SelectContent>{profiles.map((profile) => <SelectItem key={`B-${profile.id}`} value={String(profile.id)}>{profile.name}</SelectItem>)}</SelectContent></Select></div>
          </div>
          <div className="h-[280px] rounded border p-2">
            {profileA && profileB ? (
              <ResponsiveContainer width="100%" height="100%"><RadarChart data={compareData}><PolarGrid /><PolarAngleAxis dataKey="metric" tick={{ fontSize: 12 }} /><Radar name="画像A" dataKey="A" stroke="#2563eb" fill="#60a5fa" fillOpacity={0.3} /><Radar name="画像B" dataKey="B" stroke="#16a34a" fill="#4ade80" fillOpacity={0.3} /></RadarChart></ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-sm text-muted-foreground">请选择两个画像进行对比</div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default StyleProfiles;
