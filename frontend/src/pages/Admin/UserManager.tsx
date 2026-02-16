import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { User } from "@/types";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Ban, MoreHorizontal, CheckCircle } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";

const UserManager = () => {
  const [users, setUsers] = useState<User[]>([]);
  const { toast } = useToast();

  const fetchUsers = () => {
    api.admin.getUsers().then(setUsers);
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleToggleBan = async (user: User) => {
    try {
      if (user.isBanned) {
        await api.admin.unbanUser(user.id);
        toast({ title: "已解除封禁" });
      } else {
        await api.admin.banUser(user.id);
        toast({ title: "已封禁账号" });
      }
      fetchUsers();
    } catch (e: any) {
      toast({ variant: "destructive", title: "操作失败", description: e.message });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold">用户管理</h1>
      </div>

      <div className="border border-zinc-800 rounded-md bg-zinc-900">
        <Table>
          <TableHeader>
            <TableRow className="border-zinc-800 hover:bg-zinc-900">
              <TableHead className="text-zinc-400">用户</TableHead>
              <TableHead className="text-zinc-400">角色</TableHead>
              <TableHead className="text-zinc-400">积分余额</TableHead>
              <TableHead className="text-zinc-400">状态</TableHead>
              <TableHead className="text-right text-zinc-400">操作</TableHead>
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
                  <Badge variant="outline" className={user.role === 'admin' ? "border-red-500 text-red-500" : "border-zinc-700 text-zinc-400"}>
                    {user.role}
                  </Badge>
                </TableCell>
                <TableCell className="text-zinc-300 font-mono">{user.credits.toLocaleString()}</TableCell>
                <TableCell>
                  {user.isBanned ? (
                    <Badge variant="destructive">已封禁</Badge>
                  ) : (
                    <Badge variant="outline" className="border-green-900 text-green-500 bg-green-900/20">正常</Badge>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" className="text-zinc-400 hover:text-zinc-100">
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="bg-zinc-900 border-zinc-800 text-zinc-200">
                      <DropdownMenuItem
                        onClick={() => handleToggleBan(user)}
                        className={user.isBanned ? "text-green-400 focus:bg-green-900/20 focus:text-green-300" : "text-red-500 focus:bg-red-900/20 focus:text-red-400"}
                      >
                        {user.isBanned ? <CheckCircle className="mr-2 h-4 w-4" /> : <Ban className="mr-2 h-4 w-4" />}
                        {user.isBanned ? "解除封禁" : "封禁账号"}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
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
