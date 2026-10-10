import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, createRoutesFromElements, RouterProvider, Route, Navigate, Outlet, useLocation } from "react-router-dom";
import { AuthProvider } from "@/contexts/AuthContext";
import { useAuth } from "@/contexts/auth-state";
import { AiOperationProgressPanel } from "@/components/ai/AiOperationProgressPanel";

// Layouts
import AppLayout from "@/components/layout/AppLayout";

// Pages
import Index from "./pages/Index";
import Login from "./pages/auth/Login";
import Register from "./pages/auth/Register";
import SsoCallback from "./pages/auth/SsoCallback";
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
import G2EvaluationReview from "./pages/G2EvaluationReview";
import GuidedCreationPage from "./pages/GuidedCreation/GuidedCreationPage";

const queryClient = new QueryClient();

type GuardStatus = "loading" | "authorized" | "unauthorized" | "unavailable";
type GuardLocation = ReturnType<typeof useLocation>;

const buildGuardRedirect = (loginPath: string, location: GuardLocation) => {
  const next = encodeURIComponent(`${location.pathname}${location.search}${location.hash}`);
  return `${loginPath}?next=${next}`;
};

const useProtectedRouteStatus = (_location: GuardLocation): GuardStatus => {
  const { isAuthenticated, isLoading, authUnavailable } = useAuth();
  if (isLoading) return "loading";
  if (!isAuthenticated && authUnavailable) return "unavailable";
  return isAuthenticated ? "authorized" : "unauthorized";
};

const ProtectedRoute = () => {
  const location = useLocation();
  const status = useProtectedRouteStatus(location);
  const { retryAuth, authError } = useAuth();

  if (status === "loading") {
    return <div className="flex items-center justify-center h-screen">Loading...</div>;
  }

  if (status === "unavailable") {
    return <div role="alert" className="flex min-h-screen flex-col items-center justify-center gap-4"><p>{authError || "暂时无法校验登录，请稍后重试。"}</p><button onClick={() => void retryAuth?.()}>重试</button></div>;
  }

  if (status === "authorized") {
    return <Outlet />;
  }

  return <Navigate to={buildGuardRedirect("/login", location)} replace />;
};

const router = createBrowserRouter(createRoutesFromElements(
  <Route element={<><AiOperationProgressPanel /><Outlet /></>}>

            {/* Public Routes */}
            <Route path="/" element={<Index />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/sso/callback" element={<SsoCallback />} />
            <Route path="/pricing" element={<Pricing />} />

            {/* User Protected Routes */}
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/dashboard" element={<DashboardHome />} />
                <Route path="/novels" element={<NovelManager />} />
                <Route path="/novels/create" element={<CreateNovel />} />
                <Route path="/novels/quick-create" element={<GuidedCreationPage />} />
                <Route path="/worlds" element={<WorldManager />} />
                <Route path="/worlds/create" element={<CreateWorld />} />
                <Route path="/world-editor" element={<WorldEditor />} />
                <Route path="/workbench" element={<Workbench />} />
                <Route path="/materials" element={<MaterialPage />} />
                <Route path="/settings" element={<Settings />} />
                <Route path="/settings/prompt-guide" element={<PromptHelpPage />} />
                <Route path="/settings/world-prompts/help" element={<WorldPromptHelpPage />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/g2-evaluations/:id/review" element={<G2EvaluationReview />} />
              </Route>
            </Route>

            {/* Catch-all */}
            <Route path="*" element={<NotFound />} />

  </Route>
));

const App = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <RouterProvider router={router} />
      </TooltipProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default App;
