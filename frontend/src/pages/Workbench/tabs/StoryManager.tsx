import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Edit, Trash2, BookOpen, Plus } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { Link } from "react-router-dom";
import { Palette } from "lucide-react";

interface StoryManagerProps {
  initialStoryId?: string;
}

const StoryManager = ({ initialStoryId }: StoryManagerProps) => {
  const [stories, setStories] = useState<Story[]>([]);
  const [selectedStory, setSelectedStory] = useState<Story | null>(null);
  const [characters, setCharacters] = useState<any[]>([]);
  const [newCharacterName, setNewCharacterName] = useState("");
  const [newCharacterSynopsis, setNewCharacterSynopsis] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    api.stories.list().then((list) => {
      setStories(list);
      if (initialStoryId) {
        const found = list.find((s) => s.id === initialStoryId);
        if (found) setSelectedStory(found);
      }
    });
  }, []);

  useEffect(() => {
    if (!selectedStory) return;
    api.stories.listCharacters(selectedStory.id).then(setCharacters).catch(() => setCharacters([]));
  }, [selectedStory]);

  const handleAddCharacter = async () => {
    if (!selectedStory || !newCharacterName.trim()) return;
    setIsSaving(true);
    try {
      await api.stories.addCharacter(selectedStory.id, { name: newCharacterName.trim(), synopsis: newCharacterSynopsis.trim() });
      setNewCharacterName("");
      setNewCharacterSynopsis("");
      const list = await api.stories.listCharacters(selectedStory.id);
      setCharacters(list);
      toast({ title: "已添加角色" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "添加失败", description: e.message });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteStory = async () => {
    if (!selectedStory) return;
    if (!confirm("确认删除该故事？此操作不可恢复。")) return;
    try {
      await api.stories.delete(selectedStory.id);
      toast({ title: "已删除" });
      const list = await api.stories.list();
      setStories(list);
      setSelectedStory(null);
    } catch (e: any) {
      toast({ variant: "destructive", title: "删除失败", description: e.message });
    }
  };

  return (
    <div className="flex h-[calc(100vh-200px)] gap-6">
      {/* Story List */}
      <div className="w-1/3 flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-lg">我的故事</h3>
          <Link to="/novels/create">
            <Button size="sm" variant="outline">
              <Plus className="mr-2 h-4 w-4" /> 新建
            </Button>
          </Link>
        </div>
        <ScrollArea className="flex-1 pr-4">
          <div className="space-y-3">
            {stories.map((story) => (
              <Card 
                key={story.id} 
                className={`cursor-pointer transition-all hover:border-primary/50 ${selectedStory?.id === story.id ? 'border-primary bg-primary/5' : ''}`}
                onClick={() => setSelectedStory(story)}
              >
                <CardHeader className="p-4 pb-2">
                  <div className="flex justify-between items-start">
                    <CardTitle className="text-base">{story.title}</CardTitle>
                    <Badge variant={story.status === 'published' ? 'default' : 'secondary'}>
                      {story.status}
                    </Badge>
                  </div>
                  <CardDescription className="line-clamp-2 text-xs mt-1">
                    {story.synopsis}
                  </CardDescription>
                </CardHeader>
                <CardContent className="p-4 pt-2 flex gap-2 text-xs text-muted-foreground">
                  <span>{story.genre}</span>
                  <span>•</span>
                  <span>{story.tone}</span>
                </CardContent>
              </Card>
            ))}
          </div>
        </ScrollArea>
      </div>

      {/* Story Detail */}
      <div className="flex-1">
        {selectedStory ? (
          <Card className="h-full flex flex-col">
            <CardHeader>
              <div className="flex justify-between items-start">
                <div>
                  <CardTitle className="text-2xl">{selectedStory.title}</CardTitle>
                  <CardDescription className="mt-2 text-base">
                    {selectedStory.synopsis}
                  </CardDescription>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="icon"><Edit className="h-4 w-4" /></Button>
                  <Button variant="destructive" size="icon" onClick={handleDeleteStory}><Trash2 className="h-4 w-4" /></Button>
                </div>
              </div>
                <div className="flex gap-2 mt-4">
                  <Badge variant="outline">{selectedStory.genre}</Badge>
                  <Badge variant="outline">{selectedStory.tone}</Badge>
                  <Badge variant="outline">更新于 {new Date(selectedStory.updatedAt).toLocaleDateString()}</Badge>
                  <Link to={`/settings?tab=style&storyId=${selectedStory.id}`}>
                    <Button size="sm" variant="outline" className="h-6 px-2 text-xs">
                      <Palette className="h-3 w-3 mr-1" />
                      风格画像
                    </Button>
                  </Link>
                </div>
              </CardHeader>
            <CardContent className="flex-1 overflow-y-auto">
              <div className="space-y-6">
                <div>
                  <h4 className="font-semibold mb-3 flex items-center gap-2">
                    <BookOpen className="h-4 w-4" /> 角色列表
                  </h4>
                  <div className="grid grid-cols-2 gap-4">
                    {characters.map((c) => (
                      <div key={c.id} className="border rounded p-3 bg-muted/30">
                        <div className="font-medium">{c.name}</div>
                        <div className="text-sm text-muted-foreground line-clamp-2">{c.synopsis || "暂无简介"}</div>
                      </div>
                    ))}
                    <div className="border rounded p-3 bg-muted/30 border-dashed space-y-2">
                      <Input value={newCharacterName} onChange={(e) => setNewCharacterName(e.target.value)} placeholder="角色名" />
                      <Textarea value={newCharacterSynopsis} onChange={(e) => setNewCharacterSynopsis(e.target.value)} placeholder="一句话设定" className="min-h-[72px]" />
                      <Button size="sm" onClick={handleAddCharacter} disabled={isSaving || !newCharacterName.trim()}>
                        {isSaving ? "保存中..." : "添加角色"}
                      </Button>
                    </div>
                  </div>
                </div>
                
                <div>
                  <h4 className="font-semibold mb-3">世界观设定</h4>
                  <p className="text-sm text-muted-foreground">暂未关联世界观</p>
                </div>
              </div>
            </CardContent>
          </Card>
        ) : (
          <div className="h-full flex items-center justify-center text-muted-foreground border-2 border-dashed rounded-lg">
            请选择一个故事查看详情
          </div>
        )}
      </div>
    </div>
  );
};

export default StoryManager;
