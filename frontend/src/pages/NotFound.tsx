import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { buildSsoUrl, issueSsoState } from "@/lib/sso";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";

const NotFound = () => {
  const { t } = useTranslation();
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-background text-center gap-6 p-6">
      <div className="absolute top-4 right-4">
        <LanguageSwitcher showLabel={false} />
      </div>
      <div className="space-y-2">
        <p className="text-sm text-muted-foreground tracking-widest">404</p>
        <h1 className="text-3xl font-semibold">{t("notFound.title")}</h1>
        <p className="text-muted-foreground max-w-md">
          {t("notFound.desc")}
        </p>
      </div>
      <div className="flex flex-wrap gap-3 justify-center">
        <Link
          to="/"
          className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-white hover:opacity-90 transition"
        >
          {t("notFound.backHome")}
        </Link>
        <button
          type="button"
          onClick={() => {
            const state = issueSsoState();
            window.location.href = buildSsoUrl("login", "/", state);
          }}
          className="inline-flex items-center rounded-md border border-border px-4 py-2 text-sm hover:bg-muted transition"
        >
          {t("notFound.login")}
        </button>
      </div>
    </main>
  );
};

export default NotFound;
