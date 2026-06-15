import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { User } from "@/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";

type ProjectUser = User & {
  storyCount?: number;
  worldCount?: number;
};

const UserManager = () => {
  const [users, setUsers] = useState<ProjectUser[]>([]);

  useEffect(() => {
    api.admin.getUsers().then((items: any[]) => setUsers(items));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">项目用户</h1>
        <p className="text-sm text-zinc-500 mt-1">AINovel 本地用户镜像、资产与创作活动。</p>
      </div>

      <div className="border border-zinc-800 rounded-md bg-zinc-900">
        <Table>
          <TableHeader>
            <TableRow className="border-zinc-800 hover:bg-zinc-900">
              <TableHead className="text-zinc-400">用户</TableHead>
              <TableHead className="text-zinc-400">角色</TableHead>
              <TableHead className="text-zinc-400">项目积分</TableHead>
              <TableHead className="text-zinc-400">通用积分</TableHead>
              <TableHead className="text-zinc-400">创作资产</TableHead>
              <TableHead className="text-zinc-400">状态</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user: any) => (
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
                <TableCell className="text-zinc-300 font-mono">{user.projectCredits.toLocaleString()}</TableCell>
                <TableCell className="text-zinc-300 font-mono">{user.publicCredits.toLocaleString()}</TableCell>
                <TableCell className="text-zinc-300 text-sm">
                  故事 {user.storyCount ?? 0} / 世界观 {user.worldCount ?? 0}
                </TableCell>
                <TableCell>
                  {user.isBanned ? (
                    <Badge variant="destructive">本地禁用</Badge>
                  ) : (
                    <Badge variant="outline" className="border-green-900 text-green-500 bg-green-900/20">
                      正常
                    </Badge>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
};

export default UserManager;
