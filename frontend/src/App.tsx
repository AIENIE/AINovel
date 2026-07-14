import { useEffect, useState } from "react";
import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from "react-router-dom";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import { adminSession, api } from "@/lib/api-client";

// Layouts
import AppLayout from "@/components/layout/AppLayout";
import AdminLayout from "@/components/layout/AdminLayout";

// Pages
import Index from "./pages/Index";
import Login from "./pages/auth/Login";
import Register from "./pages/auth/Register";
import SsoCallback from "./pages/auth/SsoCallback";
import AdminLogin from "./pages/Admin/Login";
import Pricing from "./pages/Pricing";
import NotFound from "./pages/NotFound";
import DashboardHome from "./pages/Dashboard";
import NovelManager from "./pages/NovelManager";
import CreateNovel from "./pages/CreateNovel";
import Workbench from "./pages/Workbench/Workbench";
import WorldManager from "./pages/WorldManager";
import CreateWorld from "./pages/CreateWorld";
import WorldEditor from "./pages/WorldEditor";
import MaterialPage from "./pages/Material/MaterialPage";
import Settings from "./pages/Settings/Settings";
import PromptHelpPage from "./pages/Settings/PromptHelpPage";
import WorldPromptHelpPage from "./pages/Settings/WorldPromptHelpPage";
import ProfilePage from "./pages/Profile/ProfilePage";

// Admin Pages
import DashboardAdmin from "./pages/Admin/Dashboard";
import UserManager from "./pages/Admin/UserManager";
import SystemSettingsPage from "./pages/Admin/SystemSettings";
import CreditsManager from "./pages/Admin/CreditsManager";
import MaterialsGovernance from "./pages/Admin/MaterialsGovernance";
import AssetsAudit from "./pages/Admin/AssetsAudit";
import QualityInspection from "./pages/Admin/QualityInspection";
import OpsObservability from "./pages/Admin/OpsObservability";
import G2EvaluationCampaigns from "./pages/Admin/G2EvaluationCampaigns";
import G2EvaluationReview from "./pages/G2EvaluationReview";

const queryClient = new QueryClient();

type GuardStatus = "loading" | "authorized" | "unauthorized";
type GuardLocation = ReturnType<typeof useLocation>;

type GuardedRouteOptions = {
  loginPath: string;
  loadingClassName: string;
  useGuardStatus: (location: GuardLocation) => GuardStatus;
};

const buildGuardRedirect = (loginPath: string, location: GuardLocation) => {
  const next = encodeURIComponent(`${location.pathname}${location.search}`);
  return `${loginPath}?next=${next}`;
};

const createGuardedRoute = ({ loginPath, loadingClassName, useGuardStatus }: GuardedRouteOptions) => {
  const GuardedRoute = () => {
    const location = useLocation();
    const status = useGuardStatus(location);

    if (status === "loading") {
      return <div className={loadingClassName}>Loading...</div>;
    }

    if (status === "authorized") {
      return <Outlet />;
    }

    return <Navigate to={buildGuardRedirect(loginPath, location)} replace />;
  };

  GuardedRoute.displayName = `GuardedRoute(${loginPath})`;
  return GuardedRoute;
};

const useProtectedRouteStatus = (_location: GuardLocation): GuardStatus => {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return "loading";
  return isAuthenticated ? "authorized" : "unauthorized";
};

const useAdminRouteStatus = (location: GuardLocation): GuardStatus => {
  const [status, setStatus] = useState<GuardStatus>("loading");
  useEffect(() => {
    let cancelled = false;
    const token = adminSession.getToken();
    if (!token) {
      setStatus("unauthorized");
      return () => {
        cancelled = true;
      };
    }
    setStatus("loading");
    api.adminAuth
      .me()
      .then(() => {
        if (!cancelled) setStatus("authorized");
      })
      .catch(() => {
        adminSession.clearToken();
        if (!cancelled) setStatus("unauthorized");
      });
    return () => {
      cancelled = true;
    };
  }, [location.pathname, location.search]);

  return status;
};

const ProtectedRoute = createGuardedRoute({
  loginPath: "/login",
  loadingClassName: "flex items-center justify-center h-screen",
  useGuardStatus: useProtectedRouteStatus,
});

const AdminRoute = createGuardedRoute({
  loginPath: "/admin/login",
  loadingClassName: "flex items-center justify-center h-screen bg-zinc-950 text-zinc-500",
  useGuardStatus: useAdminRouteStatus,
});

const App = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Routes>
            {/* Public Routes */}
            <Route path="/" element={<Index />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/sso/callback" element={<SsoCallback />} />
            <Route path="/admin/login" element={<AdminLogin />} />
            <Route path="/pricing" element={<Pricing />} />

            {/* User Protected Routes */}
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard" element={<DashboardHome />} />
              <Route path="/novels" element={<NovelManager />} />
              <Route path="/novels/create" element={<CreateNovel />} />
              <Route path="/worlds" element={<WorldManager />} />
              <Route path="/worlds/create" element={<CreateWorld />} />
              <Route path="/world-editor" element={<WorldEditor />} />

              <Route element={<AppLayout />}>
                <Route path="/workbench" element={<Workbench />} />
                <Route path="/materials" element={<MaterialPage />} />
                <Route path="/settings" element={<Settings />} />
                <Route path="/settings/prompt-guide" element={<PromptHelpPage />} />
                <Route path="/settings/world-prompts/help" element={<WorldPromptHelpPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/g2-evaluations/:id/review" element={<G2EvaluationReview />} />
              </Route>
            </Route>

            {/* Admin Routes */}
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
              </Route>
            </Route>

            {/* Catch-all */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default App;
