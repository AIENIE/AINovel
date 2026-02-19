import { useNavigate } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import { Button } from "@/components/ui/button";
import { ArrowRight, Sparkles, BookOpen, Globe, Zap } from "lucide-react";
import { MadeWithDyad } from "@/components/made-with-dyad";
import { buildSsoUrl, issueSsoState } from "@/lib/sso";

const Index = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const goSso = (mode: "login" | "register", nextPath = "/workbench") => {
    const state = issueSsoState();
    window.location.href = buildSsoUrl(mode, nextPath, state);
  };

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="border-b bg-background/80 backdrop-blur-sm sticky top-0 z-50">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center text-primary-foreground font-bold">
              AI
            </div>
            <span className="font-bold text-xl">Novel Studio</span>
          </div>
          <div className="flex items-center gap-4">
            {isAuthenticated ? (
              <Button onClick={() => navigate("/dashboard")}>
                进入工作台 <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            ) : (
              <>
                <Button variant="ghost" onClick={() => goSso("login")}>
                  登录
                </Button>
                <Button onClick={() => goSso("register")}>注册</Button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1">
        <section className="py-20 md:py-32 relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-b from-primary/5 to-transparent -z-10" />
          <div className="container mx-auto px-4 text-center max-w-4xl">
            <div className="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 border-transparent bg-primary text-primary-foreground hover:bg-primary/80 mb-8 animate-in fade-in zoom-in duration-500">
              <Sparkles className="mr-1 h-3 w-3" /> AI 驱动的次世代写作体验
            </div>
            <h1 className="text-4xl md:text-6xl font-bold tracking-tight mb-6 bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/70 animate-in slide-in-from-bottom-4 duration-700">
              从灵感到完本，<br />
              全流程 AI 辅助创作
            </h1>
            <p className="text-xl text-muted-foreground mb-10 max-w-2xl mx-auto animate-in slide-in-from-bottom-5 duration-700 delay-100">
              不再担心卡文与设定崩坏。Novel Studio 提供结构化的大纲管理、
              世界观一致性检查以及智能润色功能，让你的故事栩栩如生。
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 animate-in slide-in-from-bottom-6 duration-700 delay-200">
              <Button
                size="lg"
                className="h-12 px-8 text-lg"
                onClick={() => (isAuthenticated ? navigate("/dashboard") : goSso("register"))}
              >
                {isAuthenticated ? "继续创作" : "免费开始"} <ArrowRight className="ml-2 h-5 w-5" />
              </Button>
              <Button size="lg" variant="outline" className="h-12 px-8 text-lg">
                查看演示
              </Button>
            </div>
          </div>
        </section>

        {/* Features Grid */}
        <section className="py-20 bg-muted/30">
          <div className="container mx-auto px-4">
            <div className="grid md:grid-cols-3 gap-8">
              <div className="bg-card p-8 rounded-xl border shadow-sm hover:shadow-md transition-all">
                <div className="h-12 w-12 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center mb-6 text-blue-600 dark:text-blue-400">
                  <BookOpen className="h-6 w-6" />
                </div>
                <h3 className="text-xl font-bold mb-3">结构化大纲</h3>
                <p className="text-muted-foreground">
                  可视化的章节与场景管理，拖拽排序，自动检测逻辑漏洞，确保故事节奏张弛有度。
                </p>
              </div>
              <div className="bg-card p-8 rounded-xl border shadow-sm hover:shadow-md transition-all">
                <div className="h-12 w-12 bg-purple-100 dark:bg-purple-900/30 rounded-lg flex items-center justify-center mb-6 text-purple-600 dark:text-purple-400">
                  <Globe className="h-6 w-6" />
                </div>
                <h3 className="text-xl font-bold mb-3">世界观构建</h3>
                <p className="text-muted-foreground">
                  模块化的世界设定工具，从地理到魔法体系，AI 自动补全细节并确保设定不冲突。
                </p>
              </div>
              <div className="bg-card p-8 rounded-xl border shadow-sm hover:shadow-md transition-all">
                <div className="h-12 w-12 bg-amber-100 dark:bg-amber-900/30 rounded-lg flex items-center justify-center mb-6 text-amber-600 dark:text-amber-400">
                  <Zap className="h-6 w-6" />
                </div>
                <h3 className="text-xl font-bold mb-3">智能润色</h3>
                <p className="text-muted-foreground">
                  一键扩写、润色、转换风格。内置多种网文风格模型，让你的文字更具感染力。
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t py-8 text-center text-sm text-muted-foreground">
        <div className="container mx-auto px-4">
          <p>&copy; 2024 Novel Studio. All rights reserved.</p>
          <MadeWithDyad />
        </div>
      </footer>
    </div>
  );
};

export default Index;
