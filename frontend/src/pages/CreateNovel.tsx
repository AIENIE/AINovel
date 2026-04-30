import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Sparkles } from "lucide-react";
import { showError, showSuccess } from "@/utils/toast";
import { api } from "@/lib/mock-api";

const GENRES = [
  { value: "fantasy", label: "奇幻 / 魔法" },
  { value: "scifi", label: "科幻 / 赛博朋克" },
  { value: "mystery", label: "悬疑 / 推理" },
  { value: "romance", label: "言情 / 都市" },
  { value: "wuxia", label: "武侠 / 仙侠" },
  { value: "history", label: "历史 / 架空" },
  { value: "other", label: "其他" },
];

const CACHE_PREFIX = "ainovel.plot-planner.conception";

const CreateNovel = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [aiInit, setAiInit] = useState(true);

  const [title, setTitle] = useState("");
  const [genre, setGenre] = useState("fantasy");
  const [synopsis, setSynopsis] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setIsLoading(true);
    try {
      if (aiInit) {
        const res = await api.stories.conception({ title: title.trim(), synopsis, genre, tone: "" });
        const storyId = res?.storyCard?.id;
        if (storyId) {
          try {
            window.sessionStorage.setItem(`${CACHE_PREFIX}:${storyId}`, JSON.stringify(res));
          } catch {
            // Ignore session storage failures.
          }
        }
        showSuccess("小说创建成功！");
        if (storyId) navigate(`/workbench?id=${storyId}&tab=conception`);
        else navigate("/novels");
      } else {
        const story = await api.stories.create({ title: title.trim(), synopsis, genre, tone: "" });
        showSuccess("小说创建成功！");
        navigate(`/workbench?id=${story.id}`);
      }
    } catch (err: unknown) {
      showError(err instanceof Error ? err.message : "创建失败");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="h-14 border-b flex items-center px-4 lg:px-8 bg-background/95 backdrop-blur sticky top-0 z-10">
        <Link to="/novels">
          <Button variant="ghost" size="icon" className="mr-2">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <h1 className="font-semibold">新建小说</h1>
      </header>

      <main className="flex-1 flex justify-center p-4 lg:p-8">
        <div className="w-full max-w-2xl space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="space-y-2">
            <h2 className="text-3xl font-bold tracking-tight">开始一段新的旅程</h2>
            <p className="text-muted-foreground">填写基本信息，我们将为你准备好写作环境。</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-8">
            <div className="space-y-4 p-6 border rounded-xl bg-card shadow-sm">
              <div className="space-y-2">
                <Label htmlFor="title">
                  小说标题 <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="title"
                  placeholder="例如：群星归位之时"
                  required
                  className="text-lg font-medium"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="genre">流派分类</Label>
                <Select value={genre} onValueChange={setGenre}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择流派" />
                  </SelectTrigger>
                  <SelectContent>
                    {GENRES.map((g) => (
                      <SelectItem key={g.value} value={g.value}>
                        {g.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="summary">一句话梗概 (可选)</Label>
                <Textarea
                  id="summary"
                  placeholder="简单描述你的核心创意..."
                  className="resize-none min-h-[100px]"
                  value={synopsis}
                  onChange={(e) => setSynopsis(e.target.value)}
                />
              </div>
            </div>

            <div className="p-6 border rounded-xl bg-purple-50 border-purple-200 space-y-4">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label className="text-base flex items-center gap-2 text-purple-900">
                    <Sparkles className="h-4 w-4 text-purple-600" />
                    AI 辅助初始化
                  </Label>
                  <p className="text-sm text-purple-700">自动生成初始角色建议（示例流程）</p>
                </div>
                <Switch checked={aiInit} onCheckedChange={setAiInit} />
              </div>
            </div>

            <div className="flex gap-4 pt-4">
              <Link to="/novels" className="flex-1">
                <Button variant="outline" type="button" className="w-full h-12">
                  取消
                </Button>
              </Link>
              <Button type="submit" className="flex-[2] h-12 text-lg" disabled={isLoading}>
                {isLoading ? "正在创建..." : "立即创建"}
              </Button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
};

export default CreateNovel;
