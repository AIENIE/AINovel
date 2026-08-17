import { ArrowLeft, Coins, History, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/auth-state";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";

const Pricing = () => {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();
  const actionHref = isAuthenticated ? "/profile" : "/login?next=%2Fprofile";

  const sections = [
    { icon: Coins, titleKey: "pricing.itemCredits", descKey: "pricing.itemCreditsDesc" },
    { icon: Sparkles, titleKey: "pricing.itemConsume", descKey: "pricing.itemConsumeDesc" },
    { icon: History, titleKey: "pricing.itemHistory", descKey: "pricing.itemHistoryDesc" },
  ];

  return (
    <main className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-5">
          <Link to="/" className="font-semibold tracking-tight">AINovel</Link>
          <div className="flex items-center gap-2">
            <LanguageSwitcher showLabel={false} />
            <Link to="/" className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
              <ArrowLeft className="h-4 w-4" />{t("pricing.backHome")}
            </Link>
          </div>
        </div>
      </header>
      <section className="mx-auto max-w-5xl px-5 py-16 sm:py-24">
        <div className="max-w-2xl">
          <p className="mb-4 text-sm font-medium text-primary">{t("index.pricing")}</p>
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">{t("pricing.title")}</h1>
          <p className="mt-5 text-lg leading-8 text-muted-foreground">
            {t("pricing.desc")}
          </p>
        </div>
        <div className="mt-14 divide-y border-y">
          {sections.map(({ icon: Icon, titleKey, descKey }) => (
            <div key={titleKey} className="grid gap-3 py-7 sm:grid-cols-[48px_180px_1fr] sm:items-center">
              <Icon className="h-5 w-5 text-primary" />
              <h2 className="font-medium">{t(titleKey)}</h2>
              <p className="text-sm leading-6 text-muted-foreground">{t(descKey)}</p>
            </div>
          ))}
        </div>
        <div className="mt-10 flex flex-wrap items-center gap-4">
          <Button asChild size="lg"><Link to={actionHref}>{isAuthenticated ? t("pricing.viewCredits") : t("pricing.loginViewCredits")}</Link></Button>
          <p className="text-sm text-muted-foreground">{t("pricing.footnote")}</p>
        </div>
      </section>
    </main>
  );
};

export default Pricing;
