import { useTranslation } from "react-i18next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { TabsContent } from "@/components/ui/tabs";
import type { ContextPreview } from "@/types";
import { formatDateTime } from "./shared";

type ContextSidebarPanelProps = {
  contextPreview: ContextPreview | null;
  onRefresh: () => Promise<unknown> | void;
};

export function ContextSidebarPanel({ contextPreview, onRefresh }: ContextSidebarPanelProps) {
  const { t } = useTranslation();
  return (
    <TabsContent value="context" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <Button size="sm" variant="outline" className="mb-2" onClick={() => void onRefresh()}>
        {t("contextPanel.refresh")}
      </Button>
      <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 text-xs">
        <div className="space-y-2">
        <div>{`Token: ${contextPreview?.tokenUsed || 0}/${contextPreview?.tokenBudget || 0}`}</div>
        <div>{`${t("contextPanel.promptVersion")}: ${contextPreview?.promptVersion || t("common.none")}`}</div>
        <div className="break-all text-muted-foreground">
          {`${t("contextPanel.contextHash")}: ${contextPreview?.contextHash || t("common.none")}`}
        </div>
        {!!contextPreview?.generatedAt && (
          <div>{`${t("contextPanel.generatedAt")}: ${formatDateTime(contextPreview.generatedAt)}`}</div>
        )}
        <div className="rounded border p-2">
          <div className="font-medium mb-1">{t("contextPanel.sources")}</div>
          {(contextPreview?.sources || []).map((source) => (
            <div key={`${source.sourceType}-${source.sourceId}`} className="mb-2 rounded bg-muted/40 p-2 last:mb-0">
              <div className="flex flex-wrap items-center gap-1">
                <span className="font-medium">{source.label || source.sourceId}</span>
                <Badge variant="outline">{source.sourceType}</Badge>
                {source.truncated && <Badge variant="secondary">{t("contextPanel.truncated")}</Badge>}
              </div>
              <div className="mt-1 text-muted-foreground">{source.reason || t("common.none")}</div>
              <div className="mt-1 text-muted-foreground">
                {t("contextPanel.estimatedTokens", { count: source.estimatedTokens })}
              </div>
            </div>
          ))}
          {!(contextPreview?.sources || []).length && <div className="text-muted-foreground">{t("common.none")}</div>}
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">System Prompt</div>
          {(contextPreview?.systemPromptEntries || []).map((entry: any) => (
            <div key={entry.id} className="mb-1 last:mb-0">
              {entry.displayName}
            </div>
          ))}
          {!(contextPreview?.systemPromptEntries || []).length && <div className="text-muted-foreground">{t("common.none")}</div>}
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">{t("contextPanel.beforeAfterScene")}</div>
          <div>{`${t("contextPanel.before")}: ${(contextPreview?.beforeSceneEntries || []).length} ${t("contextPanel.items")}`}</div>
          <div>{`${t("contextPanel.after")}: ${(contextPreview?.afterSceneEntries || []).length} ${t("contextPanel.items")}`}</div>
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">{t("contextPanel.graphRelations")}</div>
          {(contextPreview?.graphRelations || []).map((relation: string, index: number) => (
            <div key={`${relation}-${index}`} className="mb-1 last:mb-0">
              {relation}
            </div>
          ))}
          {!(contextPreview?.graphRelations || []).length && <div className="text-muted-foreground">{t("common.none")}</div>}
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">{t("contextPanel.activeCharacters")}</div>
          <div>{(contextPreview?.activeCharacters || []).join("、") || t("common.none")}</div>
        </div>
        <div className="rounded border p-2">
          <div className="font-medium mb-1">{t("contextPanel.recentSummary")}</div>
          <div className="text-muted-foreground">{contextPreview?.recentSummary || t("common.none")}</div>
        </div>
        </div>
      </ScrollArea>
    </TabsContent>
  );
}
