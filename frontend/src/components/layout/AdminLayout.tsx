import { useEffect, useState } from "react";
import { Outlet, NavLink, useNavigate } from "react-router-dom";
import { cn } from "@/lib/utils";
import { 
  LayoutDashboard, 
  Users, 
  Settings, 
  Coins,
  LogOut,
  FileCheck,
  Library,
  ShieldAlert
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { adminSession, api } from "@/lib/mock-api";

const AdminLayout = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState("admin");

  useEffect(() => {
    let cancelled = false;
    api.adminAuth
      .me()
      .then((res) => {
        if (!cancelled) setUsername(res.username || "admin");
      })
      .catch(() => {
        adminSession.clearToken();
        if (!cancelled) navigate("/admin/login", { replace: true });
      });
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const handleLogout = async () => {
    try {
      await api.adminAuth.logout();
    } catch {
      // Stateless logout, token cleanup on client side is enough.
    }
    adminSession.clearToken();
    navigate("/admin/login", { replace: true });
  };

  const navItems = [
    { title: "运营概览", href: "/admin/dashboard", icon: LayoutDashboard },
    { title: "项目用户", href: "/admin/users", icon: Users },
    { title: "素材治理", href: "/admin/materials", icon: FileCheck },
    { title: "创作资产", href: "/admin/assets", icon: Library },
    { title: "质量巡检", href: "/admin/quality", icon: ShieldAlert },
    { title: "专属积分", href: "/admin/credits", icon: Coins },
    { title: "系统维护", href: "/admin/settings", icon: Settings },
  ];

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex">
      {/* Admin Sidebar */}
      <aside className="w-64 border-r border-zinc-800 bg-zinc-900 flex flex-col fixed inset-y-0">
        <div className="p-6 flex items-center gap-2 border-b border-zinc-800">
          <div className="h-8 w-8 rounded bg-red-600 flex items-center justify-center font-bold text-white">
            AD
          </div>
          <span className="font-bold text-lg">Admin Panel</span>
        </div>

        <div className="flex-1 py-6 px-3 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.href}
              to={item.href}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors",
                  isActive
                    ? "bg-red-600/10 text-red-500"
                    : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.title}
            </NavLink>
          ))}
        </div>

        <div className="p-4 border-t border-zinc-800">
          <div className="flex items-center gap-3 mb-4 px-2">
            <div className="h-8 w-8 rounded-full bg-zinc-700 flex items-center justify-center">
              {username[0]?.toUpperCase() || "A"}
            </div>
            <div className="text-sm">
              <div className="font-medium">{username}</div>
              <div className="text-xs text-zinc-500">Administrator</div>
            </div>
          </div>
          <Button 
            variant="destructive" 
            className="w-full justify-start" 
            size="sm"
            onClick={() => { void handleLogout(); }}
          >
            <LogOut className="mr-2 h-4 w-4" /> 退出管理
          </Button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 ml-64 bg-zinc-950 min-h-screen">
        <div className="p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default AdminLayout;
