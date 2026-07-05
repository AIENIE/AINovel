import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { Material } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Check, X, RefreshCw } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";

const ReviewDashboard = () => {
  const [pendingMaterials, setPendingMaterials] = useState<Material[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { toast } = useToast();

  const fetchPending = async () => {
    const data = await api.materials.getPending();
    setPendingMaterials(data);
    if (data.length > 0 && !selectedId) {
      setSelectedId(data[0].id);
    }
  };

  useEffect(() => {
    fetchPending();
  }, []);

  const handleReview = async (action: 'approve' | 'reject') => {
    if (!selectedId) return;
    await api.materials.review(selectedId, action);
    toast({ 
      title: action === 'approve' ? "已批准" : "已驳回",
      variant: action === 'approve' ? "default" : "destructive"
    });
    fetchPending();
    setSelectedId(null);
  };

  const selectedMaterial = pendingMaterials.find(m => m.id === selectedId);

  return (
    <div className="flex h-[calc(100vh-200px)] gap-6">
      {/* List */}
      <div className="w-1/3 flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold">待审队列 ({pendingMaterials.length})</h3>
          <Button variant="ghost" size="sm" onClick={fetchPending}><RefreshCw className="h-4 w-4" /></Button>
        </div>
        <ScrollArea className="flex-1 border rounded-md bg-muted/10">
          <div className="p-2 space-y-2">
            {pendingMaterials.map(m => (
              <div 
                key={m.id}
                className={`p-3 rounded-md cursor-pointer transition-colors ${selectedId === m.id ? 'bg-primary/10 border-primary border' : 'bg-card hover:bg-accent'}`}
                onClick={() => setSelectedId(m.id)}
              >
                <div className="font-medium text-sm truncate">{m.title}</div>
                <div className="text-xs text-muted-foreground mt-1 truncate">{m.content}</div>
              </div>
            ))}
            {pendingMaterials.length === 0 && (
              <div className="text-center py-8 text-muted-foreground text-sm">暂无待审素材</div>
            )}
          </div>
        </ScrollArea>
      </div>

      {/* Detail & Action */}
      <div className="flex-1">
        {selectedMaterial ? (
          <Card className="h-full flex flex-col">
            <CardHeader>
              <div className="flex justify-between">
                <CardTitle>{selectedMaterial.title}</CardTitle>
                <Badge variant="outline">待审核</Badge>
              </div>
            </CardHeader>
            <CardContent className="flex-1 overflow-y-auto space-y-4">
              <div className="space-y-2">
                <div className="text-sm font-medium text-muted-foreground">内容预览</div>
                <div className="p-4 bg-muted/30 rounded-md text-sm leading-relaxed whitespace-pre-wrap">
                  {selectedMaterial.content}
                </div>
              </div>
              <div className="space-y-2">
                <div className="text-sm font-medium text-muted-foreground">自动标签</div>
                <div className="flex gap-2">
                  {selectedMaterial.tags.map(t => <Badge key={t} variant="secondary">{t}</Badge>)}
                </div>
              </div>
            </CardContent>
            <CardFooter className="border-t p-4 flex justify-end gap-3">
              <Button variant="destructive" onClick={() => handleReview('reject')}>
                <X className="mr-2 h-4 w-4" /> 驳回
              </Button>
              <Button onClick={() => handleReview('approve')} className="bg-green-600 hover:bg-green-700">
                <Check className="mr-2 h-4 w-4" /> 批准入库
              </Button>
            </CardFooter>
          </Card>
        ) : (
          <div className="h-full flex items-center justify-center text-muted-foreground border-2 border-dashed rounded-lg">
            选择左侧素材进行审核
          </div>
        )}
      </div>
    </div>
  );
};

export default ReviewDashboard;