import { useEffect, useMemo } from "react";
import { useLocation } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { buildSsoUrl, issueSsoState } from "@/lib/sso";

const Register = () => {
  const location = useLocation();

  const next = useMemo(() => {
    const qs = new URLSearchParams(location.search);
    const raw = qs.get("next") || "/workbench";
    return raw.startsWith("/") ? raw : "/workbench";
  }, [location.search]);

  const redirectToSso = (mode: "login" | "register") => {
    const state = issueSsoState();
    window.location.replace(buildSsoUrl(mode, next, state));
  };

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (token) {
      window.location.replace(next);
      return;
    }
    redirectToSso("register");
  }, [next]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted/30 px-4">
      <Card className="w-full max-w-md shadow-lg">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-bold text-center">注册 Novel Studio</CardTitle>
          <CardDescription className="text-center">
            将跳转到统一登录服务完成注册
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Button className="w-full" onClick={() => redirectToSso("register")}>
            前往统一登录注册
          </Button>
        </CardContent>
        <CardFooter className="flex flex-col gap-2">
          <div className="text-center text-sm text-muted-foreground">
            已有账号？{" "}
            <button
              type="button"
              className="text-primary hover:underline font-medium"
              onClick={() => redirectToSso("login")}
            >
              去登录（统一登录）
            </button>
          </div>
        </CardFooter>
      </Card>
    </div>
  );
};

export default Register;
