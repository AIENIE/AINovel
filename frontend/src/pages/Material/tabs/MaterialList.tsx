import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
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
import { localizedErrorMessage } from "@/lib/error-messages";

const MaterialList = () => {
  const [materials, setMaterials] = useState<Material[]>([]);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Material | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editContent, setEditContent] = useState("");
  const [editTags, setEditTags] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const { toast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    api.materials.list().then(setMaterials).catch((error: unknown) => {
      toast({ variant: "destructive", title: t("material.loadFailed"), description: localizedErrorMessage(error, "material.loadFailed") });
    });
  }, [toast, t]);

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
      toast({ title: t("material.updated") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("material.updateFailed"), description: localizedErrorMessage(error, "material.updateFailed") });
    } finally {
      setIsSaving(false);
    }
  };

  const deleteMaterial = async (material: Material) => {
    if (!window.confirm(t("material.deleteConfirm", { title: material.title }))) return;
    try {
      await api.materials.delete(material.id);
      setMaterials((prev) => prev.filter((item) => item.id !== material.id));
      toast({ title: t("material.deleted") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("material.deleteFailed"), description: localizedErrorMessage(error, "material.deleteFailed") });
    }
  };

  const filteredMaterials = materials.filter(m => 
    m.title.toLowerCase().includes(search.toLowerCase()) || 
    m.tags.some(tag => tag.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 max-w-sm">
        <Search className="h-4 w-4 text-muted-foreground" />
        <Input 
          placeholder={t("material.searchPlaceholder")}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="border rounded-md">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("common.title")}</TableHead>
              <TableHead>{t("common.type")}</TableHead>
              <TableHead>{t("material.colTags")}</TableHead>
              <TableHead>{t("common.status")}</TableHead>
              <TableHead className="text-right">{t("common.actions")}</TableHead>
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
                    {material.status === 'approved' ? t("material.statusApproved") : material.status}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" aria-label={`${t("common.actions")} ${material.title}`}>
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onSelect={() => openEdit(material)}>{t("common.edit")}</DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => void deleteMaterial(material)}>{t("common.delete")}</DropdownMenuItem>
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
            <DialogTitle>{t("material.editDialogTitle")}</DialogTitle>
            <DialogDescription>{t("material.editDialogDesc")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2"><Label>{t("common.title")}</Label><Input value={editTitle} onChange={(event) => setEditTitle(event.target.value)} /></div>
            <div className="space-y-2"><Label>{t("common.content")}</Label><Textarea value={editContent} onChange={(event) => setEditContent(event.target.value)} className="min-h-[180px]" /></div>
            <div className="space-y-2"><Label>{t("material.colTags")}</Label><Input value={editTags} onChange={(event) => setEditTags(event.target.value)} placeholder={t("material.tagPlaceholder")} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>{t("common.cancel")}</Button>
            <Button onClick={() => void saveEdit()} disabled={isSaving || !editTitle.trim() || !editContent.trim()}>{isSaving ? t("common.saving") : t("common.save")}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default MaterialList;
