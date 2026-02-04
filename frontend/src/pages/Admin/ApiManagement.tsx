import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import SwaggerUI from "swagger-ui-react";
import "swagger-ui-react/swagger-ui.css";

const resolveOpenApiUrl = () => {
  if (typeof window === "undefined") return "/api/v3/api-docs";
  return `${window.location.origin}/api/v3/api-docs`;
};

const resolveBundleUrl = () => {
  if (typeof window === "undefined") return "/api/admin/api-management/bundle";
  return `${window.location.origin}/api/admin/api-management/bundle`;
};

const ApiManagement = () => {
  const openapiUrl = useMemo(() => resolveOpenApiUrl(), []);
  const bundleUrl = useMemo(() => resolveBundleUrl(), []);
  const [bundle, setBundle] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showSwagger, setShowSwagger] = useState(false);

  const fetchBundle = async () => {
    setLoading(true);
    setError("");
    try {
      const token = localStorage.getItem("token");
      const res = await fetch(bundleUrl, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      const text = await res.text();
      if (!res.ok) throw new Error(text || `HTTP ${res.status}`);
      setBundle(text);
    } catch (e: any) {
      setError(e?.message || "获取失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <Card className="bg-zinc-900/60 border-zinc-800">
        <CardHeader>
          <CardTitle className="text-zinc-50">接口管理</CardTitle>
          <CardDescription className="text-zinc-400">
            REST 使用 Swagger UI（前端内嵌）；机器可读接口用于 AI Agent 拉取 OpenAPI 与接口清单。
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <Button
              className="bg-red-600 hover:bg-red-700 text-white"
              onClick={() => setShowSwagger((v) => !v)}
            >
              {showSwagger ? "隐藏 Swagger UI" : "显示 Swagger UI"}
            </Button>
            <Button
              variant="secondary"
              className="bg-white/5 text-zinc-50 hover:bg-white/10"
              disabled={loading}
              onClick={fetchBundle}
            >
              {loading ? "获取中…" : "获取 Bundle"}
            </Button>
          </div>

          <div className="text-xs text-zinc-500 font-mono break-all">
            OpenAPI: {openapiUrl}
            <br />
            Bundle: {bundleUrl}
          </div>

          {error ? <div className="text-sm text-red-400">{error}</div> : null}

          <Textarea
            className="min-h-[280px] bg-zinc-950/60 border-zinc-800 text-zinc-200 font-mono text-xs"
            readOnly
            value={bundle}
            placeholder="点击「获取 Bundle」后在这里查看返回的 JSON。"
          />

          {showSwagger ? (
            <div className="rounded-md border border-zinc-800 overflow-hidden bg-white">
              <SwaggerUI
                url={openapiUrl}
                requestInterceptor={(req) => {
                  try {
                    const token = localStorage.getItem("token");
                    if (token) {
                      req.headers = req.headers || {};
                      (req.headers as any).Authorization = `Bearer ${token}`;
                    }
                  } catch {
                    // ignore
                  }
                  return req;
                }}
              />
            </div>
          ) : null}
        </CardContent>
        <CardFooter className="text-xs text-zinc-500">
          提示：Bundle 默认仅管理员可访问（需要已登录的 Bearer token）。
        </CardFooter>
      </Card>
    </div>
  );
};

export default ApiManagement;
