import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Link } from "react-router-dom";
import { PenTool, Globe, ArrowRight, BookOpen, Database } from "lucide-react";
import { api } from "@/lib/api-client";
import { useAuth } from "@/contexts/AuthContext";
import { UserSummary } from "@/types";

const Dashboard = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState<UserSummary | null>(null);

  useEffect(() => {
    api.user.summary().then(setStats).catch(() => setStats({ novelCount: 0, worldCount: 0, totalWords: 0, totalEntries: 0 }));
  }, []);

  const novelCount = stats?.novelCount ?? 0;
  const worldCount = stats?.worldCount ?? 0;

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="h-16 border-b flex items-center justify-between px-6 bg-background/95 backdrop-blur fixed top-0 w-full z-50">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center text-white font-bold">AI</div>
          <span className="font-bold text-lg">工作台</span>
        </div>
        <div className="flex items-center gap-4">
          <div className="text-sm text-muted-foreground hidden md:block">欢迎回来，{user?.username || "作家"}</div>
          <div className="h-8 w-8 rounded-full bg-secondary"></div>
        </div>
      </header>

      <main className="flex-1 flex flex-col md:flex-row pt-16 h-screen">
        <Link
          to="/novels"
          className="flex-1 relative group overflow-hidden border-b md:border-b-0 md:border-r border-border bg-background hover:bg-accent/5 transition-colors"
        >
          <div className="absolute inset-0 bg-gradient-to-br from-purple-500/5 to-purple-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />

          <div className="absolute inset-0 flex flex-col items-center justify-center p-8 text-center z-10">
            <div className="w-24 h-24 rounded-full bg-secondary/50 group-hover:bg-white shadow-lg flex items-center justify-center mb-8 transition-all duration-300 group-hover:scale-110 group-hover:shadow-purple-500/20">
              <PenTool className="w-10 h-10 text-purple-600" />
            </div>

            <h2 className="text-3xl md:text-4xl font-bold mb-3 tracking-tight group-hover:text-purple-600 transition-colors">小说创作</h2>
            <p className="text-muted-foreground max-w-xs mb-8 text-lg">线性叙事的舞台。管理你的小说项目，编排大纲与正文。</p>

            <div className="flex items-center gap-6 text-sm text-muted-foreground mb-8">
              <div className="flex items-center gap-2">
                <BookOpen className="w-4 h-4" />
                <span>{novelCount === 0 ? "开始你的第一部作品" : `${novelCount} 部作品`}</span>
              </div>
              <div className="w-px h-4 bg-border" />
              <div>{(stats?.totalWords ?? 0).toLocaleString()} 字</div>
            </div>

            <Button className="rounded-full px-8 h-12 text-base shadow-lg shadow-purple-500/20 opacity-0 translate-y-4 group-hover:opacity-100 group-hover:translate-y-0 transition-all duration-300">
              进入小说库 <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </Link>

        <Link to="/worlds" className="flex-1 relative group overflow-hidden bg-background hover:bg-accent/5 transition-colors">
          <div className="absolute inset-0 bg-gradient-to-tl from-blue-500/5 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />

          <div className="absolute inset-0 flex flex-col items-center justify-center p-8 text-center z-10">
            <div className="w-24 h-24 rounded-full bg-secondary/50 group-hover:bg-white shadow-lg flex items-center justify-center mb-8 transition-all duration-300 group-hover:scale-110 group-hover:shadow-blue-500/20">
              <Globe className="w-10 h-10 text-blue-600" />
            </div>

            <h2 className="text-3xl md:text-4xl font-bold mb-3 tracking-tight group-hover:text-blue-600 transition-colors">世界构建</h2>
            <p className="text-muted-foreground max-w-xs mb-8 text-lg">网状设定的百科。独立管理你的世界观，构建逻辑自洽的宇宙。</p>

            <div className="flex items-center gap-6 text-sm text-muted-foreground mb-8">
              <div className="flex items-center gap-2">
                <Database className="w-4 h-4" />
                <span>{worldCount === 0 ? "开始构建你的第一个世界" : `${worldCount} 个世界`}</span>
              </div>
              <div className="w-px h-4 bg-border" />
              <div>{(stats?.totalEntries ?? 0).toLocaleString()} 条设定</div>
            </div>

            <Button variant="outline" className="rounded-full px-8 h-12 text-base border-blue-200 hover:bg-blue-50 hover:text-blue-600 opacity-0 translate-y-4 group-hover:opacity-100 group-hover:translate-y-0 transition-all duration-300">
              进入造物主模式 <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </Link>
      </main>
    </div>
  );
};

export default Dashboard;

