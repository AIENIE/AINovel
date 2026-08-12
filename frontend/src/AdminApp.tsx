import { useEffect, useState } from "react";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from "react-router-dom";
import { api } from "@/lib/api-client";
import { SafeErrorBoundary } from "@/components/SafeErrorBoundary";

// Admin 布局与页面（不导入任何 i18n / locale 模块，保持固定简中）
import AdminLayout from "@/components/layout/AdminLayout";
import AdminLogin from "@/pages/Admin/Login";
import DashboardAdmin from "@/pages/Admin/Dashboard";
import UserManager from "@/pages/Admin/UserManager";
import SystemSettingsPage from "@/pages/Admin/SystemSettings";
import CreditsManager from "@/pages/Admin/CreditsManager";
import MaterialsGovernance from "@/pages/Admin/MaterialsGovernance";
import AssetsAudit from "@/pages/Admin/AssetsAudit";
import QualityInspection from "@/pages/Admin/QualityInspection";
import OpsObservability from "@/pages/Admin/OpsObservability";
import G2EvaluationCampaigns from "@/pages/Admin/G2EvaluationCampaigns";
import AdminSecurity from "@/pages/Admin/Security";

const adminQueryClient = new QueryClient();

type GuardStatus = "loading" | "authorized" | "unauthorized";
type GuardLocation = ReturnType<typeof useLocation>;

const useAdminRouteStatus = (location: GuardLocation): GuardStatus => {
  const [status, setStatus] = useState<GuardStatus>("loading");
  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    api.adminAuth
      .me()
      .then((session) => {
        if (cancelled) return;
        if (session.sessionScope === "RECOVERY") {
          setStatus("unauthorized");
          return;
        }
        setStatus("authorized");
      })
      .catch(() => {
        if (!cancelled) setStatus("unauthorized");
      });
    return () => {
      cancelled = true;
    };
  }, [location.pathname, location.search]);

  return status;
};

const AdminRoute = () => {
  const location = useLocation();
  const status = useAdminRouteStatus(location);

  if (status === "loading") {
    return <div className="flex items-center justify-center h-screen bg-zinc-950 text-zinc-500">Loading...</div>;
  }

  if (status === "authorized") {
    return <Outlet />;
  }

  const next = encodeURIComponent(`${location.pathname}${location.search}`);
  return <Navigate to={`/admin/login?next=${next}`} replace />;
};

const AdminApp = () => (
  <QueryClientProvider client={adminQueryClient}>
    <TooltipProvider>
      <Toaster />
      <BrowserRouter>
        <Routes>
          <Route path="/admin/login" element={<AdminLogin />} />
          <Route path="/admin" element={<AdminRoute />}>
            <Route element={<AdminLayout />}>
              <Route index element={<Navigate to="/admin/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardAdmin />} />
              <Route path="users" element={<UserManager />} />
              <Route path="materials" element={<MaterialsGovernance />} />
              <Route path="assets" element={<AssetsAudit />} />
              <Route path="quality" element={<QualityInspection />} />
              <Route path="g2-evaluations" element={<G2EvaluationCampaigns />} />
              <Route path="credits" element={<CreditsManager />} />
              <Route path="ops" element={<OpsObservability />} />
              <Route path="settings" element={<SystemSettingsPage />} />
              <Route path="security" element={<AdminSecurity />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export function AdminEntry() {
  return (
    <SafeErrorBoundary>
      <AdminApp />
    </SafeErrorBoundary>
  );
}

export default AdminApp;
