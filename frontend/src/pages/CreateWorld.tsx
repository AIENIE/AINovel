import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Globe } from "lucide-react";
import { showError, showSuccess } from "@/utils/toast";
import { api } from "@/lib/api-client";

const CreateWorld = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [name, setName] = useState("");
  const [tagline, setTagline] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setIsLoading(true);
    try {
      const world = await api.worlds.create({ name: name.trim(), tagline: tagline.trim() || "待编辑" });
      showSuccess("世界构建成功！");
      navigate(`/world-editor?id=${world.id}`);
    } catch (err: any) {
      showError(err?.message || "创建失败");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="h-14 border-b flex items-center px-4 lg:px-8 bg-background/95 backdrop-blur sticky top-0 z-10">
        <Link to="/worlds">
          <Button variant="ghost" size="icon" className="mr-2">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <h1 className="font-semibold">构建新世界</h1>
      </header>

      <main className="flex-1 flex justify-center p-4 lg:p-8">
        <div className="w-full max-w-2xl space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="space-y-2">
            <h2 className="text-3xl font-bold tracking-tight flex items-center gap-3">
              <Globe className="h-8 w-8 text-blue-600" />
              定义你的宇宙
            </h2>
            <p className="text-muted-foreground">创建一个独立的世界观容器，用于存放地理、历史、势力与法则。</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-8">
            <div className="space-y-4 p-6 border rounded-xl bg-card shadow-sm">
              <div className="space-y-2">
                <Label htmlFor="title">
                  世界名称 <span className="text-red-500">*</span>
                </Label>
                <Input id="title" placeholder="例如：中土大陆 / 赛博朋克2077" required className="text-lg font-medium" value={name} onChange={(e) => setName(e.target.value)} />
              </div>

              <div className="space-y-2">
                <Label htmlFor="desc">世界简介</Label>
                <Textarea id="desc" placeholder="描述这个世界的核心基调..." className="resize-none min-h-[150px]" value={tagline} onChange={(e) => setTagline(e.target.value)} />
              </div>
            </div>

            <div className="flex gap-4 pt-4">
              <Link to="/worlds" className="flex-1">
                <Button variant="outline" type="button" className="w-full h-12">
                  取消
                </Button>
              </Link>
              <Button type="submit" className="flex-[2] h-12 text-lg bg-blue-600 hover:bg-blue-700" disabled={isLoading}>
                {isLoading ? "正在构建..." : "开始构建"}
              </Button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
};

export default CreateWorld;

