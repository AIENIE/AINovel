import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { Material } from "@/types";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Search, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/use-toast";

const MaterialList = () => {
  const [materials, setMaterials] = useState<Material[]>([]);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Material | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editContent, setEditContent] = useState("");
  const [editTags, setEditTags] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    api.materials.list().then(setMaterials).catch((error: any) => {
      toast({ variant: "destructive", title: "加载素材失败", description: error.message });
    });
  }, [toast]);

  const openEdit = (material: Material) => {
    setEditing(material);
    setEditTitle(material.title);
    setEditContent(material.content || "");
    setEditTags(material.tags.join(", "));
  };

  const saveEdit = async () => {
    if (!editing || !editTitle.trim() || !editContent.trim()) return;
    setIsSaving(true);
    try {
      const updated = await api.materials.update(editing.id, {
        title: editTitle.trim(),
        content: editContent,
        tags: editTags.split(",").map((tag) => tag.trim()).filter(Boolean),
      });
      setMaterials((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      setEditing(null);
      toast({ title: "素材已更新" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "更新素材失败", description: error.message });
    } finally {
      setIsSaving(false);
    }
  };

  const deleteMaterial = async (material: Material) => {
    if (!window.confirm(`确认删除素材“${material.title}”？`)) return;
    try {
      await api.materials.delete(material.id);
      setMaterials((prev) => prev.filter((item) => item.id !== material.id));
      toast({ title: "素材已删除" });
    } catch (error: any) {
      toast({ variant: "destructive", title: "删除素材失败", description: error.message });
    }
  };

  const filteredMaterials = materials.filter(m => 
    m.title.toLowerCase().includes(search.toLowerCase()) || 
    m.tags.some(t => t.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 max-w-sm">
        <Search className="h-4 w-4 text-muted-foreground" />
        <Input 
          placeholder="筛选素材..." 
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="border rounded-md">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>标题</TableHead>
              <TableHead>类型</TableHead>
              <TableHead>标签</TableHead>
              <TableHead>状态</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredMaterials.map((material) => (
              <TableRow key={material.id}>
                <TableCell className="font-medium">{material.title}</TableCell>
                <TableCell>{material.type}</TableCell>
                <TableCell>
                  <div className="flex gap-1 flex-wrap">
                    {material.tags.slice(0, 3).map(tag => (
                      <Badge key={tag} variant="secondary" className="text-xs">{tag}</Badge>
                    ))}
                  </div>
                </TableCell>
                <TableCell>
                  <Badge variant={material.status === 'approved' ? 'default' : 'secondary'}>
                    {material.status === 'approved' ? '已入库' : material.status}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" aria-label={`操作 ${material.title}`}>
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onSelect={() => openEdit(material)}>编辑</DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => void deleteMaterial(material)}>删除</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <Dialog open={Boolean(editing)} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑素材</DialogTitle>
            <DialogDescription>更新素材内容和标签后会立即重新建立检索索引。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2"><Label>标题</Label><Input value={editTitle} onChange={(event) => setEditTitle(event.target.value)} /></div>
            <div className="space-y-2"><Label>内容</Label><Textarea value={editContent} onChange={(event) => setEditContent(event.target.value)} className="min-h-[180px]" /></div>
            <div className="space-y-2"><Label>标签</Label><Input value={editTags} onChange={(event) => setEditTags(event.target.value)} placeholder="用逗号分隔" /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>取消</Button>
            <Button onClick={() => void saveEdit()} disabled={isSaving || !editTitle.trim() || !editContent.trim()}>{isSaving ? "保存中..." : "保存"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default MaterialList;
