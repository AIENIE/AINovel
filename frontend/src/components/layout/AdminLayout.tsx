import { useEffect, useState } from "react";
import { Outlet, NavLink, useLocation, useNavigate } from "react-router-dom";
import { cn } from "@/lib/utils";
import { 
  LayoutDashboard, 
  Users, 
  Settings, 
  Coins,
  LogOut,
  FileCheck,
  Library,
  ShieldAlert,
  Menu,
  Activity
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { adminSession, api } from "@/lib/api-client";

const navItems = [
  { title: "运营概览", href: "/admin/dashboard", icon: LayoutDashboard },
  { title: "项目用户", href: "/admin/users", icon: Users },
  { title: "素材治理", href: "/admin/materials", icon: FileCheck },
  { title: "创作资产", href: "/admin/assets", icon: Library },
  { title: "质量巡检", href: "/admin/quality", icon: ShieldAlert },
  { title: "专属积分", href: "/admin/credits", icon: Coins },
  { title: "运维观测", href: "/admin/ops", icon: Activity },
  { title: "系统维护", href: "/admin/settings", icon: Settings },
];

const AdminLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
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

  const activeTitle = navItems.find((item) => location.pathname.startsWith(item.href))?.title || "运营概览";

  const renderNav = (closeOnClick = false) => (
    <div className="space-y-1">
      {navItems.map((item) => {
        const link = (
          <NavLink
            key={item.href}
            to={item.href}
            className={({ isActive }) =>
              cn(
                "flex min-h-10 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-red-600/10 text-red-400"
                  : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
              )
            }
          >
            <item.icon className="h-4 w-4 shrink-0" />
            <span className="truncate">{item.title}</span>
          </NavLink>
        );
        return closeOnClick ? (
          <SheetClose asChild key={item.href}>
            {link}
          </SheetClose>
        ) : (
          link
        );
      })}
    </div>
  );

  const userBlock = (
    <div className="border-t border-zinc-800 p-4">
      <div className="mb-4 flex items-center gap-3 px-2">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-700">
          {username[0]?.toUpperCase() || "A"}
        </div>
        <div className="min-w-0 text-sm">
          <div className="truncate font-medium">{username}</div>
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
  );

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-zinc-800 bg-zinc-950/95 px-4 backdrop-blur lg:hidden">
        <div className="flex min-w-0 items-center gap-3">
          <Sheet>
            <SheetTrigger asChild>
              <Button size="icon" variant="outline" className="border-zinc-800 bg-zinc-900 text-zinc-200">
                <Menu className="h-4 w-4" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="border-zinc-800 bg-zinc-900 p-0 text-zinc-100">
              <SheetHeader className="border-b border-zinc-800 p-5 text-left">
                <SheetTitle className="text-zinc-100">AINovel Admin</SheetTitle>
              </SheetHeader>
              <div className="flex h-[calc(100vh-73px)] flex-col">
                <div className="flex-1 px-3 py-5">{renderNav(true)}</div>
                {userBlock}
              </div>
            </SheetContent>
          </Sheet>
          <div className="min-w-0">
            <div className="truncate text-sm text-zinc-500">AINovel Admin</div>
            <div className="truncate text-base font-semibold">{activeTitle}</div>
          </div>
        </div>
      </header>

      <aside className="fixed inset-y-0 hidden w-64 flex-col border-r border-zinc-800 bg-zinc-900 lg:flex">
        <div className="flex items-center gap-2 border-b border-zinc-800 p-6">
          <div className="flex h-8 w-8 items-center justify-center rounded bg-red-600 font-bold text-white">
            AD
          </div>
          <span className="text-lg font-bold">Admin Panel</span>
        </div>

        <div className="flex-1 px-3 py-6">{renderNav()}</div>
        {userBlock}
      </aside>

      <main className="min-h-screen bg-zinc-950 lg:ml-64">
        <div className="p-4 sm:p-6 lg:p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default AdminLayout;
