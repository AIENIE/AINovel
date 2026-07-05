import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Plus, MoreHorizontal, BookOpen, Clock, Trash2 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api } from "@/lib/api-client";
import { Story } from "@/types";
import { showError, showSuccess } from "@/utils/toast";

type StoryCardStats = Record<string, { wordCount: number; progress: number }>;

function estimateTextLength(html: string) {
  return (html || "").replace(/<[^>]*>/g, "").trim().length;
}

const NovelManager = () => {
  const navigate = useNavigate();
  const [stories, setStories] = useState<Story[]>([]);
  const [stats, setStats] = useState<StoryCardStats>({});
  const [loading, setLoading] = useState(true);

  const refresh = async () => {
    setLoading(true);
    try {
      const list = await api.stories.list();
      setStories(list);
      setLoading(false);

      // Background stats: wordCount & progress (best-effort)
      (async () => {
        const next: StoryCardStats = {};
        await Promise.all(
          list.map(async (story) => {
            try {
              const outlines = await api.outlines.listByStory(story.id);
              const outline = outlines[0];
              if (!outline) {
                next[story.id] = { wordCount: 0, progress: 0 };
                return;
              }
              const manuscripts = await api.manuscripts.listByOutline(outline.id);
              const manuscript = manuscripts[0];
              const totalScenes = (outline.chapters || []).reduce((acc, c) => acc + (c.scenes || []).length, 0);
              const filledScenes = manuscript ? Object.values(manuscript.sections || {}).filter((v) => estimateTextLength(v) > 0).length : 0;
              const progress = totalScenes > 0 ? Math.min(100, Math.round((filledScenes / totalScenes) * 100)) : 0;
              const wordCount = manuscript ? Object.values(manuscript.sections || {}).reduce((acc, v) => acc + estimateTextLength(v), 0) : 0;
              next[story.id] = { wordCount, progress };
            } catch {
              next[story.id] = { wordCount: 0, progress: 0 };
            }
          })
        );
        setStats(next);
      })();
    } catch (e: any) {
      setLoading(false);
      showError(e?.message || "加载失败");
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const handleDelete = async (storyId: string) => {
    if (!confirm("确认删除该故事？此操作不可恢复。")) return;
    try {
      await api.stories.delete(storyId);
      showSuccess("已删除");
      refresh();
    } catch (e: any) {
      showError(e?.message || "删除失败");
    }
  };

  const displayStories = useMemo(() => stories, [stories]);

  return (
    <div className="min-h-screen bg-muted/20">
      <header className="h-16 border-b bg-background px-6 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-4">
          <Link to="/dashboard">
            <Button variant="ghost" size="icon">
              <ArrowLeft className="h-5 w-5" />
            </Button>
          </Link>
          <h1 className="text-xl font-bold">我的小说</h1>
        </div>
        <Link to="/novels/create">
          <Button>
            <Plus className="mr-2 h-4 w-4" /> 新建小说
          </Button>
        </Link>
      </header>

      <main className="p-6 max-w-7xl mx-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Link to="/novels/create" className="block h-full">
            <div className="border-2 border-dashed border-muted-foreground/20 rounded-xl hover:border-primary/50 hover:bg-primary/5 cursor-pointer flex flex-col items-center justify-center min-h-[280px] h-full transition-all duration-300 group bg-background/50">
              <div className="h-14 w-14 rounded-full bg-muted flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <Plus className="h-6 w-6 text-muted-foreground group-hover:text-primary" />
              </div>
              <span className="font-medium text-muted-foreground group-hover:text-primary">创建新故事</span>
            </div>
          </Link>

          {loading && (
            <div className="col-span-full text-sm text-muted-foreground">加载中...</div>
          )}

          {!loading && displayStories.length === 0 && (
            <div className="col-span-full text-sm text-muted-foreground">暂无作品，先创建一个吧。</div>
          )}

          {displayStories.map((novel) => {
            const s = stats[novel.id];
            const progress = s?.progress ?? 0;
            const wordCount = s?.wordCount ?? 0;
            return (
              <div
                key={novel.id}
                className="bg-background border rounded-xl overflow-hidden flex flex-col min-h-[280px] group cursor-pointer hover:shadow-lg hover:border-primary/50 transition-all duration-300"
                onClick={() => navigate(`/workbench?id=${novel.id}`)}
              >
                <div className="h-32 bg-zinc-800 w-full relative overflow-hidden">
                  <div className="absolute inset-0 bg-gradient-to-br from-purple-900/50 to-blue-900/50" />
                  <div className="absolute top-3 right-3" onClick={(e) => e.stopPropagation()}>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8 text-white hover:bg-white/20">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => navigate(`/workbench?id=${novel.id}`)}>进入工作台</DropdownMenuItem>
                        <DropdownMenuItem className="text-destructive" onClick={() => handleDelete(novel.id)}>
                          <Trash2 className="mr-2 h-4 w-4" /> 删除
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                  <div className="absolute bottom-3 left-3 text-white text-xs font-medium bg-black/30 px-2 py-1 rounded backdrop-blur-sm">
                    {novel.genre || "未分类"}
                  </div>
                </div>

                <div className="p-5 flex-1 flex flex-col">
                  <div className="mb-4">
                    <h3 className="font-bold text-lg mb-1 line-clamp-1 group-hover:text-primary transition-colors">{novel.title}</h3>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground mb-3">
                      <div className="flex items-center gap-1">
                        <Clock className="h-3 w-3" /> {new Date(novel.updatedAt).toLocaleString()}
                      </div>
                      <div className="flex items-center gap-1">
                        <BookOpen className="h-3 w-3" /> {wordCount.toLocaleString()} 字
                      </div>
                    </div>
                    <p className="text-sm text-muted-foreground line-clamp-2">{novel.synopsis || "暂无简介"}</p>
                  </div>

                  <div className="mt-auto">
                    <div className="flex justify-between text-xs mb-1.5">
                      <span className="text-muted-foreground">完稿进度</span>
                      <span className="font-mono font-medium">{progress}%</span>
                    </div>
                    <div className="w-full bg-secondary h-1.5 rounded-full overflow-hidden">
                      <div className="bg-primary h-full rounded-full" style={{ width: `${progress}%` }} />
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </main>
    </div>
  );
};

export default NovelManager;

