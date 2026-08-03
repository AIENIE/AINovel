import { useState } from "react";
import { Outlet, NavLink, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import {
  LayoutDashboard,
  BookOpen,
  Globe,
  Library,
  Settings,
  LogOut,
  Menu,
  X,
  ChevronRight,
  ShieldAlert
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

const AppLayout = () => {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const navItems = [
    {
      title: "创作首页",
      href: "/dashboard",
      icon: LayoutDashboard,
      description: "项目总览"
    },
    {
      title: "工作台",
      href: "/workbench",
      icon: BookOpen,
      description: "创作中心"
    },
    {
      title: "小说管理",
      href: "/novels",
      icon: BookOpen,
      description: "作品列表"
    },
    {
      title: "世界构建",
      href: "/worlds",
      icon: Globe,
      description: "世界观设定"
    },
    {
      title: "素材库",
      href: "/materials",
      icon: Library,
      description: "灵感与资料"
    },
  ];

  return (
    <div className="min-h-screen bg-background flex">
      {/* Sidebar - Desktop */}
      <aside className="hidden md:flex w-64 flex-col border-r bg-card/50 backdrop-blur-xl fixed inset-y-0 z-50">
        <div className="p-6 flex items-center gap-2 border-b">
          <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center text-primary-foreground font-bold">
            AI
          </div>
          <span className="font-bold text-xl tracking-tight">AINovel</span>
        </div>

        <div className="flex-1 py-6 px-4 space-y-1 overflow-y-auto">
          <div className="text-xs font-semibold text-muted-foreground mb-2 px-2">
            主要功能
          </div>
          {navItems.map((item) => (
            <NavLink
              key={item.href}
              to={item.href}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-all duration-200 group",
                  isActive
                    ? "bg-primary/10 text-primary"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground"
                )
              }
            >
              <item.icon className="h-4 w-4" />
              <div className="flex-1">
                <div>{item.title}</div>
              </div>
              {location.pathname.startsWith(item.href) && (
                <ChevronRight className="h-3 w-3 opacity-50" />
              )}
            </NavLink>
          ))}
        </div>

        <div className="p-4 border-t space-y-2">
          <NavLink
            to="/settings"
            className={({ isActive }) =>
              cn(
                "flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors",
                isActive
                  ? "bg-accent text-accent-foreground"
                  : "text-muted-foreground hover:bg-accent hover:text-foreground"
              )
            }
          >
            <Settings className="h-4 w-4" />
            设置
          </NavLink>
          
          <div className="pt-2 flex items-center gap-3 px-3">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="w-full justify-start p-0 hover:bg-transparent">
                  <div className="flex items-center gap-3">
                    <Avatar className="h-8 w-8">
                      <AvatarImage src={user?.avatar} />
                      <AvatarFallback>{user?.username?.slice(0, 2).toUpperCase()}</AvatarFallback>
                    </Avatar>
                    <div className="flex flex-col items-start text-left">
                      <span className="text-sm font-medium">{user?.username}</span>
                      <span className="text-xs text-muted-foreground truncate w-24">
                        {user?.email}
                      </span>
                    </div>
                  </div>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>我的账户</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {isAdmin && (
                  <>
                    <DropdownMenuItem onClick={() => navigate("/admin/dashboard")} className="text-red-600 focus:text-red-600 focus:bg-red-50">
                      <ShieldAlert className="mr-2 h-4 w-4" />
                      后台管理
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                  </>
                )}
                <DropdownMenuItem onClick={() => navigate("/profile")}>个人中心</DropdownMenuItem>
                <DropdownMenuItem onClick={() => navigate("/settings")}>系统设置</DropdownMenuItem>
                <DropdownMenuItem className="text-destructive" onClick={handleLogout}>
                  <LogOut className="mr-2 h-4 w-4" />
                  退出登录
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </aside>

      {/* Mobile Header */}
      <div className="md:hidden fixed top-0 left-0 right-0 h-16 border-b bg-background/80 backdrop-blur-md z-50 flex items-center justify-between px-4">
        <div className="flex items-center gap-2">
          <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center text-primary-foreground font-bold">
            AI
          </div>
          <span className="font-bold text-lg">AINovel</span>
        </div>
        <Button variant="ghost" size="icon" onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}>
          {isMobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </Button>
      </div>

      {/* Mobile Menu Overlay */}
      {isMobileMenuOpen && (
        <div className="md:hidden fixed inset-0 z-40 bg-background pt-20 px-6">
          <nav className="space-y-4">
            {navItems.map((item) => (
              <NavLink
                key={item.href}
                to={item.href}
                onClick={() => setIsMobileMenuOpen(false)}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-4 p-4 rounded-lg border transition-colors",
                    isActive
                      ? "bg-primary/5 border-primary/20 text-primary"
                      : "bg-card border-border text-muted-foreground"
                  )
                }
              >
                <item.icon className="h-5 w-5" />
                <span className="font-medium">{item.title}</span>
              </NavLink>
            ))}
            <NavLink
              to="/settings"
              onClick={() => setIsMobileMenuOpen(false)}
              className="flex items-center gap-4 p-4 rounded-lg border bg-card text-muted-foreground"
            >
              <Settings className="h-5 w-5" />
              <span className="font-medium">系统设置</span>
            </NavLink>
            <NavLink
              to="/profile"
              onClick={() => setIsMobileMenuOpen(false)}
              className="flex items-center gap-4 p-4 rounded-lg border bg-card text-muted-foreground"
            >
              <Avatar className="h-5 w-5"><AvatarFallback className="text-[9px]">我</AvatarFallback></Avatar>
              <span className="font-medium">个人中心</span>
            </NavLink>
            {isAdmin && (
              <Button variant="outline" className="w-full mt-4 border-red-200 text-red-600 hover:bg-red-50" onClick={() => navigate("/admin/dashboard")}>
                <ShieldAlert className="mr-2 h-4 w-4" /> 进入后台管理
              </Button>
            )}
            <Button 
              variant="destructive" 
              className="w-full mt-8" 
              onClick={handleLogout}
            >
              退出登录
            </Button>
          </nav>
        </div>
      )}

      {/* Main Content Area */}
      <main className="flex-1 md:pl-64 pt-16 md:pt-0 min-h-screen transition-all duration-300">
        <div className="h-full min-w-0 p-4 sm:p-6 md:p-8 max-w-[1600px] mx-auto animate-in fade-in slide-in-from-bottom-4 duration-500">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default AppLayout;
