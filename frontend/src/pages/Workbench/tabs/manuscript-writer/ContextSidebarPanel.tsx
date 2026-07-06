import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { TabsContent } from "@/components/ui/tabs";
import { formatDateTime } from "./shared";

type ContextSidebarPanelProps = {
  contextPreview: any;
  onRefresh: () => Promise<unknown> | void;
};

export function ContextSidebarPanel({ contextPreview, onRefresh }: ContextSidebarPanelProps) {
  return (
    <TabsContent value="context" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <Button size="sm" variant="outline" className="mb-2" onClick={() => void onRefresh()}>
        刷新上下文
      </Button>
      <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 text-xs space-y-2">
        <div>{`Token: ${contextPreview?.tokenUsed || 0}/${contextPreview?.tokenBudget || 0}`}</div>
        <div>{`生成时间: ${formatDateTime(contextPreview?.generatedAt)}`}</div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">System Prompt</div>
          {(contextPreview?.systemPromptEntries || []).map((entry: any) => (
            <div key={entry.id} className="mb-1 last:mb-0">
              {entry.displayName}
            </div>
          ))}
          {!(contextPreview?.systemPromptEntries || []).length && <div className="text-muted-foreground">暂无</div>}
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">场景前 / 场景后</div>
          <div>{`前: ${(contextPreview?.beforeSceneEntries || []).length} 条`}</div>
          <div>{`后: ${(contextPreview?.afterSceneEntries || []).length} 条`}</div>
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">图谱关系</div>
          {(contextPreview?.graphRelations || []).map((relation: string, index: number) => (
            <div key={`${relation}-${index}`} className="mb-1 last:mb-0">
              {relation}
            </div>
          ))}
          {!(contextPreview?.graphRelations || []).length && <div className="text-muted-foreground">暂无</div>}
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">活跃角色</div>
          <div>{(contextPreview?.activeCharacters || []).join("、") || "暂无"}</div>
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">前情摘要</div>
          <div className="text-muted-foreground">{contextPreview?.recentSummary || "暂无"}</div>
        </div>
      </ScrollArea>
    </TabsContent>
  );
}
