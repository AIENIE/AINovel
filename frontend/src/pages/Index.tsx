import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/contexts/AuthContext";
import { Button } from "@/components/ui/button";
import { ArrowRight, Coins, LayoutDashboard, LogIn, Sparkles, BookOpen, Globe, UserPlus, Zap } from "lucide-react";
import { buildSsoUrl, issueSsoState } from "@/lib/sso";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useState } from "react";

const Index = () => {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [demoOpen, setDemoOpen] = useState(false);

  const goSso = (mode: "login" | "register", nextPath = "/dashboard") => {
    const state = issueSsoState();
    window.location.href = buildSsoUrl(mode, nextPath, state);
  };

  const demoSections = [
    { titleKey: "index.demoIdea", descKey: "index.demoIdeaDesc" },
    { titleKey: "index.demoOutline", descKey: "index.demoOutlineDesc" },
    { titleKey: "index.demoManuscript", descKey: "index.demoManuscriptDesc" },
  ];

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="border-b bg-background/80 backdrop-blur-sm sticky top-0 z-50">
        <div className="container mx-auto px-3 sm:px-4 h-14 sm:h-16 flex items-center justify-between gap-2">
          <div className="flex items-center gap-1.5 sm:gap-2 min-w-0 shrink-0">
            <div className="h-7 w-7 sm:h-8 sm:w-8 rounded-lg bg-primary flex items-center justify-center text-primary-foreground font-bold shrink-0">
              AI
            </div>
            <span className="font-bold text-base sm:text-xl tracking-tight whitespace-nowrap">AINovel</span>
          </div>
          <div className="flex items-center gap-1 sm:gap-2 md:gap-4 min-w-0">
            <LanguageSwitcher showLabel={false} />
            <Button
              variant="ghost"
              size="sm"
              className="px-2 sm:px-3"
              onClick={() => navigate("/pricing")}
              aria-label={t("index.pricing")}
              title={t("index.pricing")}
            >
              <Coins className="h-4 w-4 sm:hidden" aria-hidden="true" />
              <span className="hidden sm:inline">{t("index.pricing")}</span>
            </Button>
            {isAuthenticated ? (
              <Button size="sm" className="px-2 sm:px-4" onClick={() => navigate("/dashboard")}>
                <LayoutDashboard className="h-4 w-4 md:hidden" aria-hidden="true" />
                <span className="hidden md:inline">{t("index.enterDashboard")}</span>
                <ArrowRight className="ml-1 h-4 w-4 hidden md:inline" />
              </Button>
            ) : (
              <>
                <Button
                  variant="ghost"
                  size="sm"
                  className="px-2 sm:px-3"
                  onClick={() => goSso("login")}
                  aria-label={t("index.login")}
                  title={t("index.login")}
                >
                  <LogIn className="h-4 w-4 hidden max-[379px]:inline-flex" aria-hidden="true" />
                  <span className="max-[379px]:hidden">{t("index.login")}</span>
                </Button>
                <Button
                  size="sm"
                  className="px-2 sm:px-4"
                  onClick={() => goSso("register")}
                  aria-label={t("index.register")}
                  title={t("index.register")}
                >
                  <UserPlus className="h-4 w-4 hidden max-[379px]:inline-flex" aria-hidden="true" />
                  <span className="max-[379px]:hidden">{t("index.register")}</span>
                </Button>
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
              <Sparkles className="mr-1 h-3 w-3" /> {t("index.badge")}
            </div>
            <h1 className="text-4xl md:text-6xl font-bold tracking-tight mb-6 bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/70 animate-in slide-in-from-bottom-4 duration-700">
              {t("index.heroTitle1")}<br />
              {t("index.heroTitle2")}
            </h1>
            <p className="text-xl text-muted-foreground mb-10 max-w-2xl mx-auto animate-in slide-in-from-bottom-5 duration-700 delay-100">
              {t("index.heroSubtitle")}
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 animate-in slide-in-from-bottom-6 duration-700 delay-200">
              <Button
                size="lg"
                className="h-12 px-8 text-lg"
                onClick={() => (isAuthenticated ? navigate("/dashboard") : goSso("register"))}
              >
                {isAuthenticated ? t("index.continueCreating") : t("index.freeStart")} <ArrowRight className="ml-2 h-5 w-5" />
              </Button>
              <Button size="lg" variant="outline" className="h-12 px-8 text-lg" onClick={() => setDemoOpen(true)}>
                {t("index.viewDemo")}
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
                <h3 className="text-xl font-bold mb-3">{t("index.featureOutline")}</h3>
                <p className="text-muted-foreground">
                  {t("index.featureOutlineDesc")}
                </p>
              </div>
              <div className="bg-card p-8 rounded-xl border shadow-sm hover:shadow-md transition-all">
                <div className="h-12 w-12 bg-purple-100 dark:bg-purple-900/30 rounded-lg flex items-center justify-center mb-6 text-purple-600 dark:text-purple-400">
                  <Globe className="h-6 w-6" />
                </div>
                <h3 className="text-xl font-bold mb-3">{t("index.featureWorld")}</h3>
                <p className="text-muted-foreground">
                  {t("index.featureWorldDesc")}
                </p>
              </div>
              <div className="bg-card p-8 rounded-xl border shadow-sm hover:shadow-md transition-all">
                <div className="h-12 w-12 bg-amber-100 dark:bg-amber-900/30 rounded-lg flex items-center justify-center mb-6 text-amber-600 dark:text-amber-400">
                  <Zap className="h-6 w-6" />
                </div>
                <h3 className="text-xl font-bold mb-3">{t("index.featurePolish")}</h3>
                <p className="text-muted-foreground">
                  {t("index.featurePolishDesc")}
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t py-8 text-center text-sm text-muted-foreground">
        <div className="container mx-auto px-4">
          <p>{t("index.copyright")}</p>
        </div>
      </footer>

      <Dialog open={demoOpen} onOpenChange={setDemoOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("index.demoTitle")}</DialogTitle>
            <DialogDescription>{t("index.demoDesc")}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 sm:grid-cols-3">
            {demoSections.map((section, index) => (
              <div key={section.titleKey} className="rounded-lg border bg-muted/30 p-4">
                <div className="mb-2 text-xs font-semibold text-primary">0{index + 1}</div>
                <div className="font-medium">{t(section.titleKey)}</div>
                <p className="mt-2 text-sm text-muted-foreground">
                  {t(section.descKey)}
                </p>
              </div>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default Index;
