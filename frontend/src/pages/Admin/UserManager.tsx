import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { User } from "@/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel, AdminSearchToolbar } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

type ProjectUser = User & {
  storyCount?: number;
  worldCount?: number;
};

const UserManager = () => {
  const [users, setUsers] = useState<ProjectUser[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async (keyword = search) => {
    setIsLoading(true);
    setError("");
    try {
      const items = await api.admin.getUsers(keyword);
      setUsers(items as ProjectUser[]);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "项目用户加载失败"));
    } finally {
      setIsLoading(false);
    }
  }, [search]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load(search);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [load, search]);

  return (
    <div className="space-y-6">
      <AdminPageHeader title="项目用户" description="AINovel 本地用户镜像、资产与创作活动。" />

      <AdminPanel title="用户列表" description="支持按用户名或邮箱查询；账号封禁、邮箱与 SSO 管理由 user-service 负责。">
        <div className="space-y-4">
          <AdminSearchToolbar value={search} onChange={setSearch} placeholder="搜索用户名或邮箱" />
          {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
          {isLoading ? <AdminLoadingState rows={5} /> : null}
          {!isLoading && !error && users.length === 0 ? <AdminEmptyState title="没有匹配的项目用户" description="调整搜索词后重试。" /> : null}
          {!isLoading && !error && users.length > 0 ? (
            <div className="overflow-x-auto rounded-md border border-zinc-800">
              <Table>
                <TableHeader>
                  <TableRow className="border-zinc-800 hover:bg-zinc-900">
                    <TableHead className="min-w-[220px] text-zinc-400">用户</TableHead>
                    <TableHead className="text-zinc-400">角色</TableHead>
                    <TableHead className="text-right text-zinc-400">项目积分</TableHead>
                    <TableHead className="text-right text-zinc-400">通用积分</TableHead>
                    <TableHead className="min-w-[150px] text-zinc-400">创作资产</TableHead>
                    <TableHead className="text-zinc-400">状态</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.id} className="border-zinc-800 hover:bg-zinc-800/50">
                      <TableCell className="font-medium text-zinc-200">
                        <div className="flex flex-col">
                          <span>{user.username}</span>
                          <span className="text-xs text-zinc-500">{user.email}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={user.role === "admin" ? "border-red-500 text-red-500" : "border-zinc-700 text-zinc-400"}>
                          {user.role}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-mono text-zinc-300">{user.projectCredits.toLocaleString()}</TableCell>
                      <TableCell className="text-right font-mono text-zinc-300">{user.publicCredits.toLocaleString()}</TableCell>
                      <TableCell className="text-sm text-zinc-300">
                        故事 {user.storyCount ?? 0} / 世界观 {user.worldCount ?? 0}
                      </TableCell>
                      <TableCell>
                        {user.isBanned ? (
                          <Badge variant="destructive">本地禁用</Badge>
                        ) : (
                          <Badge variant="outline" className="border-green-900 bg-green-900/20 text-green-500">
                            正常
                          </Badge>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : null}
        </div>
      </AdminPanel>
    </div>
  );
};

export default UserManager;
