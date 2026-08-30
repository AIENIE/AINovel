
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, type NetworkObject } from "@/lib/api-client";
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
import { localizedErrorMessage } from "@/lib/error-messages";

const STYLE_DIMENSIONS = [
  { key: "formality", labelKey: "styleDims.formality", minHintKey: "styleDims.formalityMin", maxHintKey: "styleDims.formalityMax" },
  { key: "sentence_length", labelKey: "styleDims.sentenceLength", minHintKey: "styleDims.sentenceLengthMin", maxHintKey: "styleDims.sentenceLengthMax" },
  { key: "vocabulary_richness", labelKey: "styleDims.vocabulary", minHintKey: "styleDims.vocabularyMin", maxHintKey: "styleDims.vocabularyMax" },
  { key: "pacing", labelKey: "styleDims.pacing", minHintKey: "styleDims.pacingMin", maxHintKey: "styleDims.pacingMax" },
  { key: "descriptiveness", labelKey: "styleDims.descriptiveness", minHintKey: "styleDims.descriptivenessMin", maxHintKey: "styleDims.descriptivenessMax" },
  { key: "dialogue_ratio", labelKey: "styleDims.dialogueRatio", minHintKey: "styleDims.dialogueRatioMin", maxHintKey: "styleDims.dialogueRatioMax" },
  { key: "emotional_intensity", labelKey: "styleDims.emotion", minHintKey: "styleDims.emotionMin", maxHintKey: "styleDims.emotionMax" },
  { key: "rhetoric_frequency", labelKey: "styleDims.rhetoric", minHintKey: "styleDims.rhetoricMin", maxHintKey: "styleDims.rhetoricMax" },
] as const;

const SCENE_MODES = ["action", "dialogue", "introspection", "description", "flashback"];
const VOCABULARY_LEVELS = ["colloquial", "neutral", "literary", "formal"];
const DEFAULT_EMOTIONS = [
  { value: "冷静", labelKey: "styleDims.emotionCalm" },
  { value: "愤怒", labelKey: "styleDims.emotionAngry" },
  { value: "悲伤", labelKey: "styleDims.emotionSad" },
  { value: "讽刺", labelKey: "styleDims.emotionSarcastic" },
  { value: "坚定", labelKey: "styleDims.emotionDetermined" },
] as const;

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

const normalizeDimensionScore = (value: unknown, fallback = 6) => {
  const num = Number(value);
  if (!Number.isFinite(num)) return fallback;
  if (num <= 10 && num >= 1) return Math.round(num);
  if (num >= 0 && num <= 100) return Math.max(1, Math.min(10, Math.round(num / 10)));
  return fallback;
};

const toDialogueSamples = (raw: NetworkObject): DialogueSample[] => {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => {
      if (typeof item === "string") return { context: "", line: item };
      if (item && typeof item === "object") {
        return { context: String(item.context || item.emotion || ""), line: String(item.line || item.content || "") };
      }
      return null;
    })
    .filter((item): item is DialogueSample => !!item && !!item.line.trim());
};

const mapAnalysisDimensions = (analysisResult: NetworkObject) => {
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
  catchphrases: [] as string[],
  emotionalRange: [] as string[],
  dialect: "",
  sampleDialogues: [] as DialogueSample[],
};

const emptyProfileForm = {
  name: "",
  profileType: "narrative",
  sampleText: "",
  dimensions: buildDefaultDimensions(6),
  sceneOverrides: SCENE_MODES.map((mode) => ({ sceneType: mode, dimensions: buildDefaultDimensions(6) })),
  dimensionNotes: {},
};

const StyleProfiles = ({ initialStoryId }: StyleProfilesProps) => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState(initialStoryId || "");
  const [characters, setCharacters] = useState<NetworkObject[]>([]);
  const [profiles, setProfiles] = useState<NetworkObject[]>([]);
  const [voices, setVoices] = useState<NetworkObject[]>([]);
  const [selectedProfileId, setSelectedProfileId] = useState("");
  const [selectedVoiceId, setSelectedVoiceId] = useState("");
  const [compareProfileA, setCompareProfileA] = useState("");
  const [compareProfileB, setCompareProfileB] = useState("");
  const [analysisText, setAnalysisText] = useState("");
  const [analysisResult, setAnalysisResult] = useState<NetworkObject | null>(null);
  const [profileForm, setProfileForm] = useState<NetworkObject>(emptyProfileForm);
  const [voiceForm, setVoiceForm] = useState<NetworkObject>(emptyVoiceForm);
  const [catchphraseInput, setCatchphraseInput] = useState("");
  const [emotionInput, setEmotionInput] = useState("");
  const [sampleContextInput, setSampleContextInput] = useState("");
  const [sampleLineInput, setSampleLineInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [voicePreview, setVoicePreview] = useState("");

  useEffect(() => {
    api.stories
      .list()
      .then((list) => {
        setStories(list);
        setStoryId((current) => current || (initialStoryId && list.some((story) => story.id === initialStoryId) ? initialStoryId : list[0]?.id || ""));
      })
      .catch((error: NetworkObject) => toast({ variant: "destructive", title: t("errors.loadStoriesFailed"), description: localizedErrorMessage(error, "errors.loadStoriesFailed") }));
  }, [initialStoryId, t, toast]);

  const loadData = useCallback(async () => {
    if (!storyId) return;
    const [profileList, voiceList, characterList] = await Promise.all([
      api.v2.style.listProfiles(storyId),
      api.v2.style.listVoices(storyId),
      api.stories.listCharacters(storyId),
    ]);
    setProfiles(profileList);
    setVoices(voiceList);
    setCharacters(characterList);
  }, [storyId]);

  useEffect(() => {
    setSelectedProfileId("");
    setSelectedVoiceId("");
    setCompareProfileA("");
    setCompareProfileB("");
    if (!storyId) return;
    setLoading(true);
    loadData()
      .catch((error: NetworkObject) => toast({ variant: "destructive", title: t("errors.loadStyleDataFailed"), description: localizedErrorMessage(error, "errors.loadStyleDataFailed") }))
      .finally(() => setLoading(false));
  }, [loadData, storyId, t, toast]);

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
          ? profile.sceneOverrides.map((override: NetworkObject) => ({
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
    () => STYLE_DIMENSIONS.map((item) => ({ metric: t(item.labelKey), A: normalizeDimensionScore(profileA?.dimensions?.[item.key], 6), B: normalizeDimensionScore(profileB?.dimensions?.[item.key], 6) })),
    [profileA, profileB, t],
  );

  const saveProfile = async () => {
    if (!storyId) return;
    if (!profileForm.name?.trim()) {
      toast({ variant: "destructive", title: t("style.profileNameRequired") });
      return;
    }
    try {
      if (profileForm.id) await api.v2.style.updateProfile(storyId, profileForm.id, profileForm);
      else await api.v2.style.createProfile(storyId, profileForm);
      await loadData();
      toast({ title: t("style.profileSaved") });
      setVoicePreview("");
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
    }
  };

  const removeProfile = async () => {
    if (!storyId || !profileForm.id) return;
    try {
      await api.v2.style.deleteProfile(storyId, profileForm.id);
      await loadData();
      setSelectedProfileId("");
      setProfileForm(emptyProfileForm);
      toast({ title: t("style.profileDeleted") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.deleteFailed"), description: localizedErrorMessage(error, "errors.deleteFailed") });
    }
  };

  const activateProfile = async (profileId: string) => {
    if (!storyId) return;
    try {
      await api.v2.style.activateProfile(storyId, profileId);
      await loadData();
      toast({ title: t("style.profileActivated") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.activateFailed"), description: localizedErrorMessage(error, "errors.activateFailed") });
    }
  };

  const runAnalysis = async () => {
    try {
      const result = await api.v2.style.analyze({ sourceType: "uploaded_text", sourceReference: "settings-style", sampleText: analysisText });
      setAnalysisResult(result);
      toast({ title: t("style.analysisDone") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.analysisFailed"), description: localizedErrorMessage(error, "errors.analysisFailed") });
    }
  };

  const createFromAnalysis = async () => {
    if (!storyId || !analysisResult) return;
    try {
      const analysisPayload = analysisResult.result || {};
      await api.v2.style.createProfile(storyId, {
        name: String(analysisPayload.suggestedProfileName || `${t("style.analysisProfile")}-${new Date().toLocaleTimeString()}`),
        profileType: "narrative",
        dimensions: mapAnalysisDimensions(analysisResult),
        aiAnalysis: analysisPayload,
        sampleText: analysisText,
      });
      await loadData();
      toast({ title: t("style.profileCreatedFromAnalysis") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.createFailed"), description: localizedErrorMessage(error, "errors.createFailed") });
    }
  };

  const saveVoice = async () => {
    if (!storyId) return;
    if (!voiceForm.characterCardId) {
      toast({ variant: "destructive", title: t("style.selectCharacterFirst") });
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
      toast({ title: t("style.voiceSaved") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") });
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
      toast({ title: t("style.voiceDeleted") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.deleteFailed"), description: localizedErrorMessage(error, "errors.deleteFailed") });
    }
  };

  const generateVoice = async () => {
    if (!storyId || !voiceForm.id) return;
    const selectedCharacter = characters.find((character) => String(character.id) === String(voiceForm.characterCardId));
    try {
      await api.v2.style.generateVoice(storyId, voiceForm.id, { characterName: selectedCharacter?.name || t("style.characterFallback") });
      await loadData();
      toast({ title: t("style.voiceGenerated") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.generateFailed"), description: localizedErrorMessage(error, "errors.generateFailed") });
    }
  };

  const previewVoice = async () => {
    if (!voiceForm.characterCardId) {
      toast({ variant: "destructive", title: t("style.selectCharacterFirst") });
      return;
    }
    const selectedCharacter = characters.find((character) => String(character.id) === String(voiceForm.characterCardId));
    const characterName = selectedCharacter?.name || t("style.characterFallback");
    const catchphrase = (voiceForm.catchphrases || [])[0] || t("style.catchphraseFallback");
    const mood = (voiceForm.emotionalRange || [])[0] || t("style.moodFallback");
    const sample = (voiceForm.sampleDialogues || [])[0]?.line || t("style.sampleLineFallback");
    const dialect = voiceForm.dialect ? t("style.dialectSuffix", { dialect: voiceForm.dialect }) : "";
    const pattern = voiceForm.speechPattern?.trim() || t("style.speechPatternFallback");
    setVoicePreview(
      t("style.voicePreviewTemplate", { dialect, characterName, catchphrase, sample, mood, level: voiceForm.vocabularyLevel || "colloquial", pattern }),
    );
    toast({ title: t("style.previewGenerated") });
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="pt-6 flex flex-wrap items-end gap-3">
          <div className="w-[300px]">
            <Label>{t("style.story")}</Label>
            <Select value={storyId} onValueChange={setStoryId}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.selectStory")} />
              </SelectTrigger>
              <SelectContent>
                {stories.map((story) => (
                  <SelectItem key={story.id} value={story.id}>{story.title}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button variant="secondary" onClick={() => void loadData()} disabled={!storyId || loading}>{t("common.refresh")}</Button>
          {activeProfile ? <Badge>{t("style.currentlyActive", { name: activeProfile.name })}</Badge> : <Badge variant="outline">{t("style.noActiveProfile")}</Badge>}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("style.manageTitle")}</CardTitle>
            <CardDescription>{t("style.manageDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-[1fr_auto] gap-2">
              <Select value={selectedProfileId} onValueChange={setSelectedProfileId}>
                <SelectTrigger><SelectValue placeholder={t("style.selectProfileToEdit")} /></SelectTrigger>
                <SelectContent>{profiles.map((profile) => <SelectItem key={profile.id} value={String(profile.id)}>{profile.name}</SelectItem>)}</SelectContent>
              </Select>
              <Button variant="outline" onClick={() => { setSelectedProfileId(""); setProfileForm(emptyProfileForm); }}>{t("common.create")}</Button>
            </div>
            <div className="grid md:grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>{t("style.profileName")}</Label>
                <Input value={profileForm.name || ""} onChange={(event) => setProfileForm((prev: NetworkObject) => ({ ...prev, name: event.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t("style.type")}</Label>
                <Select value={profileForm.profileType || "narrative"} onValueChange={(value) => setProfileForm((prev: NetworkObject) => ({ ...prev, profileType: value }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="narrative">{t("style.typeNarrative")}</SelectItem>
                    <SelectItem value="dialogue">{t("style.typeDialogue")}</SelectItem>
                    <SelectItem value="description">{t("style.typeDescription")}</SelectItem>
                    <SelectItem value="global">{t("style.typeGlobal")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid md:grid-cols-2 gap-2">
              {STYLE_DIMENSIONS.map((item) => (
                <div key={item.key} className="space-y-1 rounded border p-2">
                  <div className="flex items-center justify-between">
                    <Label>{t(item.labelKey)}</Label>
                    <Badge variant="outline">{Number(profileForm.dimensions?.[item.key] ?? 6)}</Badge>
                  </div>
                  <div className="text-[11px] text-muted-foreground flex justify-between"><span>{t(item.minHintKey)}</span><span>{t(item.maxHintKey)}</span></div>
                  <Slider min={1} max={10} step={1} value={[Number(profileForm.dimensions?.[item.key] ?? 6)]} onValueChange={(value) => setProfileForm((prev: NetworkObject) => ({ ...prev, dimensions: { ...(prev.dimensions || {}), [item.key]: Number(value[0] ?? 6) } }))} />
                  <Input placeholder={t("style.dimensionNotePlaceholder")} value={String(profileForm.dimensionNotes?.[item.key] || "")} onChange={(event) => setProfileForm((prev: NetworkObject) => ({ ...prev, dimensionNotes: { ...(prev.dimensionNotes || {}), [item.key]: event.target.value } }))} />
                </div>
              ))}
            </div>
            <Separator />
            <div className="space-y-2">
              <Label>{t("style.sceneOverrides")}</Label>
              <div className="space-y-2">
                {(profileForm.sceneOverrides || []).map((override: NetworkObject, index: number) => (
                  <div key={`${override.sceneType}-${index}`} className="rounded border p-2 space-y-2">
                    <div className="font-medium text-sm">{override.sceneType}</div>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                      {STYLE_DIMENSIONS.map((dim) => (
                        <div key={`${override.sceneType}-${dim.key}`} className="space-y-1">
                          <Label className="text-xs">{t(dim.labelKey)}</Label>
                          <Slider min={1} max={10} step={1} value={[Number(override.dimensions?.[dim.key] ?? 6)]} onValueChange={(value) => setProfileForm((prev: NetworkObject) => { const nextOverrides = [...(prev.sceneOverrides || [])]; const target = { ...nextOverrides[index] }; target.dimensions = { ...(target.dimensions || {}), [dim.key]: Number(value[0] ?? 6) }; nextOverrides[index] = target; return { ...prev, sceneOverrides: nextOverrides }; })} />
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t("style.sampleText")}</Label>
              <Textarea className="min-h-[120px]" value={profileForm.sampleText || ""} onChange={(event) => setProfileForm((prev: NetworkObject) => ({ ...prev, sampleText: event.target.value }))} />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button onClick={saveProfile} disabled={!storyId}>{t("style.saveProfile")}</Button>
              <Button variant="outline" onClick={() => selectedProfileId && activateProfile(selectedProfileId)} disabled={!selectedProfileId}>{t("style.activateProfile")}</Button>
              <Button variant="destructive" onClick={removeProfile} disabled={!profileForm.id}>{t("style.deleteProfile")}</Button>
            </div>
            <Separator />
            <div className="space-y-2">
              <Label>{t("style.analysisText")}</Label>
              <Textarea className="min-h-[120px]" value={analysisText} onChange={(event) => setAnalysisText(event.target.value)} />
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => void runAnalysis()}>{t("style.analyze")}</Button>
                <Button variant="outline" onClick={() => void createFromAnalysis()} disabled={!analysisResult}>{t("style.analysisToProfile")}</Button>
              </div>
              {analysisResult && <div className="rounded-md border p-2 text-xs grid grid-cols-2 gap-1">{STYLE_DIMENSIONS.map((item) => <div key={item.key} className="flex items-center justify-between rounded bg-muted px-2 py-1"><span>{t(item.labelKey)}</span><span>{mapAnalysisDimensions(analysisResult)[item.key]}</span></div>)}</div>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("style.voiceTitle")}</CardTitle>
            <CardDescription>{t("style.voiceDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-[1fr_auto] gap-2">
              <Select value={selectedVoiceId} onValueChange={setSelectedVoiceId}>
                <SelectTrigger><SelectValue placeholder={t("style.selectVoiceConfig")} /></SelectTrigger>
                <SelectContent>{voices.map((voice) => <SelectItem key={voice.id} value={String(voice.id)}>{String(voice.characterCardId || voice.id).slice(0, 8)}</SelectItem>)}</SelectContent>
              </Select>
              <Button variant="outline" onClick={() => { setSelectedVoiceId(""); setVoiceForm(emptyVoiceForm); setVoicePreview(""); }}>{t("common.create")}</Button>
            </div>
            <div className="space-y-2">
              <Label>{t("style.character")}</Label>
              <Select value={voiceForm.characterCardId || ""} onValueChange={(value) => setVoiceForm((prev: NetworkObject) => ({ ...prev, characterCardId: value }))}>
                <SelectTrigger><SelectValue placeholder={t("style.selectCharacterCard")} /></SelectTrigger>
                <SelectContent>{characters.map((character) => <SelectItem key={character.id} value={String(character.id)}>{character.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="grid md:grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>{t("style.vocabularyLevel")}</Label>
                <Select value={voiceForm.vocabularyLevel || "colloquial"} onValueChange={(value) => setVoiceForm((prev: NetworkObject) => ({ ...prev, vocabularyLevel: value }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>{VOCABULARY_LEVELS.map((item) => <SelectItem key={item} value={item}>{t(`style.vocab.${item}`)}</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div className="space-y-2"><Label>{t("style.dialect")}</Label><Input value={voiceForm.dialect || ""} onChange={(event) => setVoiceForm((prev: NetworkObject) => ({ ...prev, dialect: event.target.value }))} /></div>
            </div>
            <div className="space-y-2"><Label>{t("style.speechPattern")}</Label><Textarea value={voiceForm.speechPattern || ""} onChange={(event) => setVoiceForm((prev: NetworkObject) => ({ ...prev, speechPattern: event.target.value }))} className="min-h-[100px]" /></div>
            <div className="space-y-2">
              <Label>{t("style.catchphrases")}</Label>
              <div className="flex gap-2"><Input value={catchphraseInput} onChange={(event) => setCatchphraseInput(event.target.value)} placeholder={t("style.catchphrasePlaceholder")} /><Button variant="outline" onClick={() => { const value = catchphraseInput.trim(); if (!value) return; setVoiceForm((prev: NetworkObject) => ({ ...prev, catchphrases: [...(prev.catchphrases || []), value] })); setCatchphraseInput(""); }}>{t("common.add")}</Button></div>
              <div className="flex flex-wrap gap-2">{(voiceForm.catchphrases || []).map((item: string) => <Badge key={item} variant="outline" className="cursor-pointer" onClick={() => setVoiceForm((prev: NetworkObject) => ({ ...prev, catchphrases: (prev.catchphrases || []).filter((value: string) => value !== item) }))}>{item} ×</Badge>)}</div>
            </div>
            <div className="space-y-2">
              <Label>{t("style.emotionalRange")}</Label>
              <div className="flex gap-2"><Select value={emotionInput} onValueChange={setEmotionInput}><SelectTrigger><SelectValue placeholder={t("style.selectEmotion")} /></SelectTrigger><SelectContent>{DEFAULT_EMOTIONS.map((emotion) => <SelectItem key={emotion.value} value={emotion.value}>{t(emotion.labelKey)}</SelectItem>)}</SelectContent></Select><Button variant="outline" onClick={() => { const value = emotionInput.trim(); if (!value || (voiceForm.emotionalRange || []).includes(value)) return; setVoiceForm((prev: NetworkObject) => ({ ...prev, emotionalRange: [...(prev.emotionalRange || []), value] })); setEmotionInput(""); }}>{t("common.add")}</Button></div>
              <div className="flex flex-wrap gap-2">{(voiceForm.emotionalRange || []).map((item: string) => <Badge key={item} variant="secondary" className="cursor-pointer" onClick={() => setVoiceForm((prev: NetworkObject) => ({ ...prev, emotionalRange: (prev.emotionalRange || []).filter((value: string) => value !== item) }))}>{item} ×</Badge>)}</div>
            </div>
            <div className="space-y-2">
              <Label>{t("style.sampleDialogues")}</Label>
              <div className="grid grid-cols-3 gap-2"><Input value={sampleContextInput} onChange={(event) => setSampleContextInput(event.target.value)} placeholder={t("style.sampleContextPlaceholder")} /><Input className="col-span-2" value={sampleLineInput} onChange={(event) => setSampleLineInput(event.target.value)} placeholder={t("style.sampleLinePlaceholder")} /></div>
              <Button variant="outline" onClick={() => { const line = sampleLineInput.trim(); if (!line) return; const context = sampleContextInput.trim(); setVoiceForm((prev: NetworkObject) => ({ ...prev, sampleDialogues: [...(prev.sampleDialogues || []), { context, line }] })); setSampleLineInput(""); }}>{t("style.addSample")}</Button>
              <ScrollArea className="h-24 rounded-md border p-2"><div className="space-y-1 text-sm">{(voiceForm.sampleDialogues || []).map((item: DialogueSample, index: number) => <div key={`${item.context}-${item.line}-${index}`} className="rounded border p-1"><div className="text-xs text-muted-foreground">{item.context ? `[${item.context}]` : ""}</div><div>{item.line}</div></div>)}</div></ScrollArea>
            </div>
            <div className="flex flex-wrap gap-2"><Button onClick={() => void saveVoice()} disabled={!storyId}>{t("style.saveVoice")}</Button><Button variant="secondary" onClick={() => void generateVoice()} disabled={!voiceForm.id}>{t("style.aiGenerate")}</Button><Button variant="outline" onClick={() => void previewVoice()}>{t("common.preview")}</Button><Button variant="destructive" onClick={() => void removeVoice()} disabled={!voiceForm.id}>{t("style.deleteVoice")}</Button></div>
            {voicePreview && <div className="rounded border bg-muted/40 p-2 text-sm"><div className="font-medium mb-1">{t("style.voicePreview")}</div><p className="whitespace-pre-wrap text-muted-foreground">{voicePreview}</p></div>}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>{t("style.compareTitle")}</CardTitle><CardDescription>{t("style.compareDesc")}</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-2"><Label>{t("style.profileA")}</Label><Select value={compareProfileA} onValueChange={setCompareProfileA}><SelectTrigger><SelectValue placeholder={t("style.selectProfileA")} /></SelectTrigger><SelectContent>{profiles.map((profile) => <SelectItem key={`A-${profile.id}`} value={String(profile.id)}>{profile.name}</SelectItem>)}</SelectContent></Select></div>
            <div className="space-y-2"><Label>{t("style.profileB")}</Label><Select value={compareProfileB} onValueChange={setCompareProfileB}><SelectTrigger><SelectValue placeholder={t("style.selectProfileB")} /></SelectTrigger><SelectContent>{profiles.map((profile) => <SelectItem key={`B-${profile.id}`} value={String(profile.id)}>{profile.name}</SelectItem>)}</SelectContent></Select></div>
          </div>
          <div className="h-[280px] rounded border p-2">
            {profileA && profileB ? (
              <ResponsiveContainer width="100%" height="100%"><RadarChart data={compareData}><PolarGrid /><PolarAngleAxis dataKey="metric" tick={{ fontSize: 12 }} /><Radar name={t("style.profileA")} dataKey="A" stroke="#2563eb" fill="#60a5fa" fillOpacity={0.3} /><Radar name={t("style.profileB")} dataKey="B" stroke="#16a34a" fill="#4ade80" fillOpacity={0.3} /></RadarChart></ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-sm text-muted-foreground">{t("style.selectTwoProfiles")}</div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default StyleProfiles;
