import { useCallback, useEffect, useMemo } from "react";
import { useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";
import { buildSsoUrl, issueSsoState } from "@/lib/sso";

const Register = () => {
  const location = useLocation();
  const { t } = useTranslation();

  const next = useMemo(() => {
    const qs = new URLSearchParams(location.search);
    const raw = qs.get("next") || "/dashboard";
    return raw.startsWith("/") ? raw : "/dashboard";
  }, [location.search]);

  const redirectToSso = useCallback((mode: "login" | "register") => {
    const state = issueSsoState();
    window.location.replace(buildSsoUrl(mode, next, state));
  }, [next]);

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (token) {
      window.location.replace(next);
      return;
    }
    redirectToSso("register");
  }, [next, redirectToSso]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted/30 px-4">
      <div className="absolute top-4 right-4">
        <LanguageSwitcher showLabel={false} />
      </div>
      <Card className="w-full max-w-md shadow-lg">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-bold text-center">{t("auth.registerTitle")}</CardTitle>
          <CardDescription className="text-center">
            {t("auth.registerDesc")}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Button className="w-full" onClick={() => redirectToSso("register")}>
            {t("auth.registerAction")}
          </Button>
        </CardContent>
        <CardFooter className="flex flex-col gap-2">
          <div className="text-center text-sm text-muted-foreground">
            {t("auth.haveAccount")}{" "}
            <button
              type="button"
              className="text-primary hover:underline font-medium"
              onClick={() => redirectToSso("login")}
            >
              {t("auth.toLogin")}
            </button>
          </div>
        </CardFooter>
      </Card>
    </div>
  );
};

export default Register;
