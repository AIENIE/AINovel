import type { NetworkObject } from "@/lib/api-client";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Edit, Palette, Plus, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api-client";
import type { Story, World } from "@/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage } from "@/lib/error-messages";

type CharacterDraft = { id?: string; name: string; synopsis: string; role: string; archetype: string };
const emptyCharacter = (): CharacterDraft => ({ name: "", synopsis: "", role: "", archetype: "" });

interface StoryManagerProps {
  initialStoryId?: string;
  onStoryChange?: (storyId: string) => void;
}

const StoryManager = ({ initialStoryId, onStoryChange }: StoryManagerProps) => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [stories, setStories] = useState<Story[]>([]);
  const [worlds, setWorlds] = useState<World[]>([]);
  const [selectedStory, setSelectedStory] = useState<Story | null>(null);
  const [characters, setCharacters] = useState<NetworkObject[]>([]);
  const [storyDialogOpen, setStoryDialogOpen] = useState(false);
  const [characterDialogOpen, setCharacterDialogOpen] = useState(false);
  const [storyDraft, setStoryDraft] = useState<Partial<Story>>({});
  const [characterDraft, setCharacterDraft] = useState<CharacterDraft>(emptyCharacter());
  const [isSaving, setIsSaving] = useState(false);

  const loadCharacters = async (storyId: string) => {
    try { setCharacters(await api.stories.listCharacters(storyId)); }
    catch { setCharacters([]); }
  };

  useEffect(() => {
    Promise.all([api.stories.list(), api.worlds.list()]).then(([storyList, worldList]) => {
      setStories(storyList);
      setWorlds(worldList.filter((world) => world.status === "active"));
      const initial = storyList.find((story) => story.id === initialStoryId) || storyList[0] || null;
      setSelectedStory(initial);
    }).catch((error: Error) => toast({ variant: "destructive", title: t("errors.loadFailed"), description: localizedErrorMessage(error, "errors.loadFailed") }));
  }, [initialStoryId, toast, t]);

  useEffect(() => {
    if (!selectedStory) return;
    onStoryChange?.(selectedStory.id);
    void loadCharacters(selectedStory.id);
  }, [selectedStory, onStoryChange]);

  const selectedWorld = useMemo(() => worlds.find((world) => world.id === selectedStory?.worldId), [selectedStory, worlds]);

  const openStoryEditor = () => {
    if (!selectedStory) return;
    setStoryDraft(selectedStory);
    setStoryDialogOpen(true);
  };

  const saveStory = async () => {
    if (!selectedStory || !storyDraft.title?.trim()) return;
    setIsSaving(true);
    try {
      const saved = await api.stories.update(selectedStory.id, {
        title: storyDraft.title.trim(), synopsis: storyDraft.synopsis || "", genre: storyDraft.genre || "",
        tone: storyDraft.tone || "", status: storyDraft.status || "draft", worldId: storyDraft.worldId || "",
      });
      setStories((current) => current.map((story) => story.id === saved.id ? saved : story));
      setSelectedStory(saved);
      setStoryDialogOpen(false);
      toast({ title: t("storyManager.storySaved") });
    } catch (error: unknown) { toast({ variant: "destructive", title: t("errors.saveFailed"), description: localizedErrorMessage(error, "errors.saveFailed") }); }
    finally { setIsSaving(false); }
  };

  const openCharacterEditor = (character?: NetworkObject) => {
    setCharacterDraft(character ? {
      id: character.id, name: character.name || "", synopsis: character.synopsis || character.summary || "",
      role: character.role || "", archetype: character.archetype || "",
    } : emptyCharacter());
    setCharacterDialogOpen(true);
  };

  const saveCharacter = async () => {
    if (!selectedStory || !characterDraft.name.trim()) return;
    setIsSaving(true);
    try {
      const payload = { name: characterDraft.name.trim(), synopsis: characterDraft.synopsis.trim(), role: characterDraft.role.trim(), archetype: characterDraft.archetype.trim() };
      if (characterDraft.id) await api.stories.updateCharacter(characterDraft.id, payload);
      else await api.stories.addCharacter(selectedStory.id, payload);
      await loadCharacters(selectedStory.id);
      setCharacterDialogOpen(false);
      toast({ title: characterDraft.id ? t("storyManager.characterUpdated") : t("storyManager.characterAdded") });
    } catch (error: unknown) { toast({ variant: "destructive", title: t("storyManager.characterSaveFailed"), description: localizedErrorMessage(error, "storyManager.characterSaveFailed") }); }
    finally { setIsSaving(false); }
  };

  const deleteCharacter = async (character: NetworkObject) => {
    if (!selectedStory || !confirm(t("storyManager.deleteCharacterConfirm", { name: character.name }))) return;
    try { await api.stories.deleteCharacter(character.id); await loadCharacters(selectedStory.id); toast({ title: t("storyManager.characterDeleted") }); }
    catch (error: unknown) { toast({ variant: "destructive", title: t("errors.deleteFailed"), description: localizedErrorMessage(error, "errors.deleteFailed") }); }
  };

  const deleteStory = async () => {
    if (!selectedStory || !confirm(t("storyManager.deleteStoryConfirm", { title: selectedStory.title }))) return;
    try {
      await api.stories.delete(selectedStory.id);
      const next = stories.filter((story) => story.id !== selectedStory.id);
      const nextStory = next[0] || null;
      setStories(next); setSelectedStory(nextStory); onStoryChange?.(nextStory?.id || ""); toast({ title: t("storyManager.storyDeleted") });
    } catch (error: unknown) { toast({ variant: "destructive", title: t("errors.deleteFailed"), description: localizedErrorMessage(error, "errors.deleteFailed") }); }
  };

  return (
    <div className="grid h-full min-h-[560px] gap-5 lg:grid-cols-[260px_minmax(0,1fr)]">
      <aside className="min-w-0 border-r pr-0 lg:pr-5">
        <div className="mb-3 flex items-center justify-between"><h3 className="font-medium">{t("storyManager.storyProjects")}</h3><Badge variant="secondary">{stories.length}</Badge></div>
        <ScrollArea className="h-[480px]">
          <div className="space-y-1 pr-3">
            {stories.map((story) => <button key={story.id} onClick={() => setSelectedStory(story)} className={`w-full rounded-md px-3 py-2 text-left text-sm ${selectedStory?.id === story.id ? "bg-primary/10 text-primary" : "hover:bg-muted"}`}><span className="block truncate font-medium">{story.title}</span><span className="block truncate text-xs text-muted-foreground">{story.genre || t("storyManager.genreUnset")}</span></button>)}
            {!stories.length && <p className="py-10 text-center text-sm text-muted-foreground">{t("storyManager.noStories")}</p>}
          </div>
        </ScrollArea>
      </aside>

      {selectedStory ? <section className="min-w-0 space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div><div className="flex items-center gap-2"><h2 className="text-2xl font-semibold">{selectedStory.title}</h2><Badge variant="outline">{selectedStory.status}</Badge></div><p className="mt-2 max-w-3xl text-sm text-muted-foreground">{selectedStory.synopsis || t("storyManager.noSynopsis")}</p></div>
          <div className="flex gap-2"><Button variant="outline" onClick={openStoryEditor}><Edit className="mr-2 h-4 w-4" />{t("storyManager.editSettings")}</Button><Button variant="destructive" size="icon" onClick={() => void deleteStory()}><Trash2 className="h-4 w-4" /></Button></div>
        </div>
        <div className="grid gap-4 sm:grid-cols-3"><div><p className="text-xs text-muted-foreground">{t("storyManager.genre")}</p><p className="mt-1 text-sm font-medium">{selectedStory.genre || t("storyManager.unset")}</p></div><div><p className="text-xs text-muted-foreground">{t("storyManager.tone")}</p><p className="mt-1 text-sm font-medium">{selectedStory.tone || t("storyManager.unset")}</p></div><div><p className="text-xs text-muted-foreground">{t("storyManager.relatedWorld")}</p><p className="mt-1 text-sm font-medium">{selectedWorld?.name || t("storyManager.notLinked")}</p></div></div>
        <Card><CardHeader className="flex-row items-center justify-between"><div><CardTitle className="text-lg">{t("storyManager.characterCards")}</CardTitle><CardDescription>{t("storyManager.characterCardsDesc")}</CardDescription></div><Button size="sm" onClick={() => openCharacterEditor()}><Plus className="mr-2 h-4 w-4" />{t("storyManager.addCharacter")}</Button></CardHeader><CardContent className="divide-y">
          {characters.map((character) => <div key={character.id} className="flex items-start justify-between gap-3 py-4 first:pt-0"><div className="min-w-0"><div className="font-medium">{character.name}</div><div className="mt-1 text-xs text-muted-foreground">{[character.role, character.archetype].filter(Boolean).join(" · ") || t("storyManager.roleUnset")}</div><p className="mt-2 text-sm text-muted-foreground">{character.synopsis || character.summary || t("storyManager.noSummary")}</p></div><div className="flex shrink-0 gap-1"><Button size="icon" variant="ghost" onClick={() => openCharacterEditor(character)}><Edit className="h-4 w-4" /></Button><Button size="icon" variant="ghost" onClick={() => void deleteCharacter(character)}><Trash2 className="h-4 w-4" /></Button></div></div>)}
          {!characters.length && <p className="py-8 text-center text-sm text-muted-foreground">{t("storyManager.noCharacters")}</p>}
        </CardContent></Card>
        <Button asChild variant="outline"><Link to={`/settings?tab=style&storyId=${selectedStory.id}`}><Palette className="mr-2 h-4 w-4" />{t("storyManager.manageStyleVoice")}</Link></Button>
      </section> : <div className="flex items-center justify-center rounded-lg border border-dashed text-muted-foreground">{t("storyManager.selectStory")}</div>}

      <Dialog open={storyDialogOpen} onOpenChange={setStoryDialogOpen}><DialogContent><DialogHeader><DialogTitle>{t("storyManager.editStoryTitle")}</DialogTitle><DialogDescription>{t("storyManager.editStoryDesc")}</DialogDescription></DialogHeader><div className="grid gap-4 py-2 sm:grid-cols-2"><div className="space-y-2 sm:col-span-2"><Label>{t("storyManager.title")}</Label><Input value={storyDraft.title || ""} onChange={(e) => setStoryDraft({...storyDraft,title:e.target.value})} /></div><div className="space-y-2"><Label>{t("storyManager.genre")}</Label><Input value={storyDraft.genre || ""} onChange={(e) => setStoryDraft({...storyDraft,genre:e.target.value})} /></div><div className="space-y-2"><Label>{t("storyManager.tone")}</Label><Input value={storyDraft.tone || ""} onChange={(e) => setStoryDraft({...storyDraft,tone:e.target.value})} /></div><div className="space-y-2"><Label>{t("storyManager.status")}</Label><Select value={storyDraft.status || "draft"} onValueChange={(value: string) => setStoryDraft({...storyDraft,status:value as Story["status"]})}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="draft">{t("storyManager.statusDraft")}</SelectItem><SelectItem value="published">{t("storyManager.statusPublished")}</SelectItem><SelectItem value="archived">{t("storyManager.statusArchived")}</SelectItem></SelectContent></Select></div><div className="space-y-2"><Label>{t("storyManager.world")}</Label><Select value={storyDraft.worldId || "__none__"} onValueChange={(value) => setStoryDraft({...storyDraft,worldId:value === "__none__" ? "" : value})}><SelectTrigger><SelectValue placeholder={t("storyManager.notLinked")} /></SelectTrigger><SelectContent><SelectItem value="__none__">{t("storyManager.noWorld")}</SelectItem>{worlds.map((world) => <SelectItem key={world.id} value={world.id}>{world.name}</SelectItem>)}</SelectContent></Select></div><div className="space-y-2 sm:col-span-2"><Label>{t("storyManager.synopsis")}</Label><Textarea className="min-h-32" value={storyDraft.synopsis || ""} onChange={(e) => setStoryDraft({...storyDraft,synopsis:e.target.value})} /></div></div><DialogFooter><Button variant="outline" onClick={() => setStoryDialogOpen(false)}>{t("common.cancel")}</Button><Button disabled={isSaving || !storyDraft.title?.trim()} onClick={() => void saveStory()}>{isSaving ? t("common.saving") : t("common.save")}</Button></DialogFooter></DialogContent></Dialog>

      <Dialog open={characterDialogOpen} onOpenChange={setCharacterDialogOpen}><DialogContent><DialogHeader><DialogTitle>{characterDraft.id ? t("storyManager.editCharacter") : t("storyManager.addCharacter")}</DialogTitle><DialogDescription>{t("storyManager.editCharacterDesc")}</DialogDescription></DialogHeader><div className="grid gap-4 py-2 sm:grid-cols-2"><div className="space-y-2 sm:col-span-2"><Label>{t("storyManager.characterName")}</Label><Input value={characterDraft.name} onChange={(e) => setCharacterDraft({...characterDraft,name:e.target.value})} /></div><div className="space-y-2"><Label>{t("storyManager.characterRole")}</Label><Input value={characterDraft.role} onChange={(e) => setCharacterDraft({...characterDraft,role:e.target.value})} /></div><div className="space-y-2"><Label>{t("storyManager.characterArchetype")}</Label><Input value={characterDraft.archetype} onChange={(e) => setCharacterDraft({...characterDraft,archetype:e.target.value})} /></div><div className="space-y-2 sm:col-span-2"><Label>{t("storyManager.synopsis")}</Label><Textarea className="min-h-28" value={characterDraft.synopsis} onChange={(e) => setCharacterDraft({...characterDraft,synopsis:e.target.value})} /></div></div><DialogFooter><Button variant="outline" onClick={() => setCharacterDialogOpen(false)}>{t("common.cancel")}</Button><Button disabled={isSaving || !characterDraft.name.trim()} onClick={() => void saveCharacter()}>{isSaving ? t("common.saving") : t("storyManager.saveCharacter")}</Button></DialogFooter></DialogContent></Dialog>
    </div>
  );
};

export default StoryManager;
