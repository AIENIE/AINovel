import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Link } from "react-router-dom";
import { PenTool, Globe, ArrowRight, BookOpen, Database } from "lucide-react";
import { api } from "@/lib/api-client";
import { useAuth } from "@/contexts/AuthContext";
import { UserSummary } from "@/types";

const Dashboard = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
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
          <span className="font-bold text-lg">{t("dashboard.headerTitle")}</span>
        </div>
        <div className="flex items-center gap-4">
          <div className="text-sm text-muted-foreground hidden md:block">{t("dashboard.welcome", { name: user?.username || t("dashboard.writer") })}</div>
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

            <h2 className="text-3xl md:text-4xl font-bold mb-3 tracking-tight group-hover:text-purple-600 transition-colors">{t("dashboard.novels")}</h2>
            <p className="text-muted-foreground max-w-xs mb-8 text-lg">{t("dashboard.novelsDesc")}</p>

            <div className="flex items-center gap-6 text-sm text-muted-foreground mb-8">
              <div className="flex items-center gap-2">
                <BookOpen className="w-4 h-4" />
                <span>{novelCount === 0 ? t("dashboard.startFirstNovel") : t("dashboard.novelCount", { count: novelCount, value: novelCount })}</span>
              </div>
              <div className="w-px h-4 bg-border" />
              <div>{t("dashboard.wordCount", { count: (stats?.totalWords ?? 0), value: (stats?.totalWords ?? 0).toLocaleString() })}</div>
            </div>

            <Button className="rounded-full px-8 h-12 text-base shadow-lg shadow-purple-500/20 opacity-0 translate-y-4 group-hover:opacity-100 group-hover:translate-y-0 transition-all duration-300">
              {t("dashboard.enterNovels")} <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </Link>

        <Link to="/worlds" className="flex-1 relative group overflow-hidden bg-background hover:bg-accent/5 transition-colors">
          <div className="absolute inset-0 bg-gradient-to-tl from-blue-500/5 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />

          <div className="absolute inset-0 flex flex-col items-center justify-center p-8 text-center z-10">
            <div className="w-24 h-24 rounded-full bg-secondary/50 group-hover:bg-white shadow-lg flex items-center justify-center mb-8 transition-all duration-300 group-hover:scale-110 group-hover:shadow-blue-500/20">
              <Globe className="w-10 h-10 text-blue-600" />
            </div>

            <h2 className="text-3xl md:text-4xl font-bold mb-3 tracking-tight group-hover:text-blue-600 transition-colors">{t("dashboard.worlds")}</h2>
            <p className="text-muted-foreground max-w-xs mb-8 text-lg">{t("dashboard.worldsDesc")}</p>

            <div className="flex items-center gap-6 text-sm text-muted-foreground mb-8">
              <div className="flex items-center gap-2">
                <Database className="w-4 h-4" />
                <span>{worldCount === 0 ? t("dashboard.startFirstWorld") : t("dashboard.worldCount", { count: worldCount, value: worldCount })}</span>
              </div>
              <div className="w-px h-4 bg-border" />
              <div>{t("dashboard.entryCount", { count: (stats?.totalEntries ?? 0), value: (stats?.totalEntries ?? 0).toLocaleString() })}</div>
            </div>

            <Button variant="outline" className="rounded-full px-8 h-12 text-base border-blue-200 hover:bg-blue-50 hover:text-blue-600 opacity-0 translate-y-4 group-hover:opacity-100 group-hover:translate-y-0 transition-all duration-300">
              {t("dashboard.enterWorlds")} <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </Link>
      </main>
    </div>
  );
};

export default Dashboard;
