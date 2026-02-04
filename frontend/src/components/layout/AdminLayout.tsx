import { Outlet, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import { cn } from "@/lib/utils";
import { 
  LayoutDashboard, 
  Users, 
  Cpu, 
  FileText, 
  Gift, 
  Mail, 
  Settings, 
  LogOut,
  AlertTriangle,
  Code
} from "lucide-react";
import { Button } from "@/components/ui/button";

const AdminLayout = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const navItems = [
    { title: "仪表盘", href: "/admin/dashboard", icon: LayoutDashboard },
    { title: "模型管理", href: "/admin/models", icon: Cpu },
    { title: "用户管理", href: "/admin/users", icon: Users },
    { title: "积分日志", href: "/admin/logs", icon: FileText },
    { title: "兑换码", href: "/admin/codes", icon: Gift },
    { title: "邮件验证", href: "/admin/email", icon: Mail },
    { title: "接口管理", href: "/admin/api-management", icon: Code },
    { title: "系统设置", href: "/admin/settings", icon: Settings },
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
              {user?.username[0]}
            </div>
            <div className="text-sm">
              <div className="font-medium">{user?.username}</div>
              <div className="text-xs text-zinc-500">Administrator</div>
            </div>
          </div>
          <Button 
            variant="destructive" 
            className="w-full justify-start" 
            size="sm"
            onClick={() => { logout(); navigate("/login"); }}
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
