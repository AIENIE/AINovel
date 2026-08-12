import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from "react-router-dom";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
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

type GuardStatus = "loading" | "authorized" | "unauthorized";
type GuardLocation = ReturnType<typeof useLocation>;

const buildGuardRedirect = (loginPath: string, location: GuardLocation) => {
  const next = encodeURIComponent(`${location.pathname}${location.search}`);
  return `${loginPath}?next=${next}`;
};

const useProtectedRouteStatus = (_location: GuardLocation): GuardStatus => {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return "loading";
  return isAuthenticated ? "authorized" : "unauthorized";
};

const ProtectedRoute = () => {
  const location = useLocation();
  const status = useProtectedRouteStatus(location);

  if (status === "loading") {
    return <div className="flex items-center justify-center h-screen">Loading...</div>;
  }

  if (status === "authorized") {
    return <Outlet />;
  }

  return <Navigate to={buildGuardRedirect("/login", location)} replace />;
};

const App = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <AiOperationProgressPanel />
          <Routes>
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
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default App;
