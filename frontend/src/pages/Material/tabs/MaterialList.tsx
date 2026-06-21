import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { Material } from "@/types";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Search, MoreHorizontal, ArrowDownUp, SlidersHorizontal } from "lucide-react";
import {
  filterAndSortMaterials,
  type MaterialSortBy,
  type MaterialSortDirection,
  type MaterialStatusFilter,
  type MaterialTypeFilter,
} from "../material-list-utils";

const MaterialList = () => {
  const [materials, setMaterials] = useState<Material[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<MaterialStatusFilter>("all");
  const [type, setType] = useState<MaterialTypeFilter>("all");
  const [tag, setTag] = useState("");
  const [sortBy, setSortBy] = useState<MaterialSortBy>("createdAt");
  const [sortDirection, setSortDirection] = useState<MaterialSortDirection>("desc");

  useEffect(() => {
    api.materials.list().then(setMaterials);
  }, []);

  const filteredMaterials = filterAndSortMaterials(materials, { search, status, type, tag, sortBy, sortDirection });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-[240px] flex-1 items-center gap-2">
          <Search className="h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="按标题、正文或标签搜索..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="h-4 w-4 text-muted-foreground" />
          <Select value={status} onValueChange={(value) => setStatus(value as MaterialStatusFilter)}>
            <SelectTrigger className="w-[120px]">
              <SelectValue placeholder="状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              <SelectItem value="pending">待审核</SelectItem>
              <SelectItem value="approved">已入库</SelectItem>
              <SelectItem value="rejected">已驳回</SelectItem>
            </SelectContent>
          </Select>
          <Select value={type} onValueChange={(value) => setType(value as MaterialTypeFilter)}>
            <SelectTrigger className="w-[120px]">
              <SelectValue placeholder="类型" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部类型</SelectItem>
              <SelectItem value="text">文本</SelectItem>
              <SelectItem value="image">图片</SelectItem>
              <SelectItem value="link">链接</SelectItem>
            </SelectContent>
          </Select>
          <Input
            className="w-[160px]"
            placeholder="标签关键词"
            value={tag}
            onChange={(e) => setTag(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          <ArrowDownUp className="h-4 w-4 text-muted-foreground" />
          <Select value={sortBy} onValueChange={(value) => setSortBy(value as MaterialSortBy)}>
            <SelectTrigger className="w-[120px]">
              <SelectValue placeholder="排序" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="createdAt">创建时间</SelectItem>
              <SelectItem value="title">标题</SelectItem>
            </SelectContent>
          </Select>
          <Select value={sortDirection} onValueChange={(value) => setSortDirection(value as MaterialSortDirection)}>
            <SelectTrigger className="w-[112px]">
              <SelectValue placeholder="方向" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="desc">降序</SelectItem>
              <SelectItem value="asc">升序</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="border rounded-md">
        <Table className="min-w-[760px]">
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
            {filteredMaterials.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-sm text-muted-foreground">
                  没有匹配的素材
                </TableCell>
              </TableRow>
            ) : (
              filteredMaterials.map((material) => (
                <TableRow key={material.id}>
                  <TableCell className="font-medium">{material.title}</TableCell>
                  <TableCell className="whitespace-nowrap">{typeLabel(material.type)}</TableCell>
                  <TableCell>
                    <div className="flex gap-1 flex-wrap">
                      {material.tags.slice(0, 3).map((tag) => (
                        <Badge key={tag} variant="secondary" className="whitespace-nowrap text-xs">{tag}</Badge>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={material.status === "approved" ? "default" : "secondary"}
                      className="whitespace-nowrap"
                    >
                      {statusLabel(material.status)}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="icon">
                      <MoreHorizontal className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
};

function statusLabel(status: Material["status"]): string {
  if (status === "approved") return "已入库";
  if (status === "pending") return "待审核";
  return "已驳回";
}

function typeLabel(type: Material["type"]): string {
  if (type === "text") return "文本";
  if (type === "image") return "图片";
  return "链接";
}

export default MaterialList;
