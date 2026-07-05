import type { MouseEvent } from "react";
import { Loader2 } from "lucide-react";
import CopilotSidebar from "@/components/ai/CopilotSidebar";
import TiptapEditor from "@/components/editor/TiptapEditor";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import type { Chapter, PlotQualityRun, SlopQualityRun } from "@/types";
import {
  formatDateTime,
  plotDimensionLabel,
  plotStatusClass,
  plotStatusText,
  qualityStatusClass,
  qualityStatusText,
  slopModuleLabel,
} from "./shared";

type SidebarTab = "copilot" | "context" | "version" | "export" | "stats" | "goals" | "plot";
type MobilePane = "outline" | "editor" | "sidebar";

type MobileWorkbenchPanelProps = {
  content: string;
  contextData: any;
  exportJobs: any[];
  focusMode: boolean;
  isPlotBusy: boolean;
  isPlotRevisionBusy: boolean;
  isSlopBusy: boolean;
  mobilePane: MobilePane;
  onApplyPlotRevision: () => Promise<void> | void;
  onChangeMobilePane: (pane: MobilePane) => void;
  onChangeSidebarTab: (tab: SidebarTab) => void;
  onCreateExportJob: () => Promise<void> | void;
  onEditorChange: (html: string) => void;
  onGeneratePlotRevisionCandidate: () => Promise<void> | void;
  onLoadVersions: () => Promise<void> | void;
  onRunPlotDiagnosis: () => Promise<void> | void;
  onRunSlopDiagnosis: () => Promise<void> | void;
  onSelectOutlineScene: (sceneId: string, event?: MouseEvent<HTMLButtonElement>) => void;
  outlineChapters: Chapter[];
  selectedManuscriptId: string;
  selectedPlotRun: PlotQualityRun | null;
  selectedQualityRun: SlopQualityRun | null;
  selectedSceneId: string;
  sidebarTab: SidebarTab;
  versions: any[];
};

export function MobileWorkbenchPanel({
  content,
  contextData,
  exportJobs,
  focusMode,
  isPlotBusy,
  isPlotRevisionBusy,
  isSlopBusy,
  mobilePane,
  onApplyPlotRevision,
  onChangeMobilePane,
  onChangeSidebarTab,
  onCreateExportJob,
  onEditorChange,
  onGeneratePlotRevisionCandidate,
  onLoadVersions,
  onRunPlotDiagnosis,
  onRunSlopDiagnosis,
  onSelectOutlineScene,
  outlineChapters,
  selectedManuscriptId,
  selectedPlotRun,
  selectedQualityRun,
  selectedSceneId,
  sidebarTab,
  versions,
}: MobileWorkbenchPanelProps) {
  return (
    <Tabs value={mobilePane} onValueChange={(value) => onChangeMobilePane(value as MobilePane)} className="h-full rounded-lg border bg-background p-2">
      <TabsList className="grid grid-cols-3">
        <TabsTrigger value="outline">大纲</TabsTrigger>
        <TabsTrigger value="editor">编辑</TabsTrigger>
        <TabsTrigger value="sidebar">参考</TabsTrigger>
      </TabsList>
      <TabsContent value="outline" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
        <ScrollArea className="h-full rounded border p-2">
          {outlineChapters.map((chapter, chapterIndex) => (
            <div key={chapter.id} className="mb-2">
              <div className="text-xs font-semibold text-muted-foreground mb-1">{`第${chapterIndex + 1}章 ${chapter.title}`}</div>
              <div className="space-y-1">
                {chapter.scenes.map((scene, sceneIndex) => (
                  <button
                    key={scene.id}
                    className={cn("w-full rounded border px-2 py-1 text-left text-xs", selectedSceneId === scene.id ? "bg-secondary border-primary/40" : "hover:bg-muted")}
                    onClick={(event) => {
                      onSelectOutlineScene(scene.id, event);
                      onChangeMobilePane("editor");
                    }}
                  >
                    {`Sc.${sceneIndex + 1} ${scene.title}`}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </ScrollArea>
      </TabsContent>
      <TabsContent value="editor" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
        <div className="h-full border rounded overflow-hidden">
          <TiptapEditor
            key={`mobile-editor-${focusMode ? "zen" : "normal"}`}
            content={content}
            onChange={onEditorChange}
            className="h-full"
            editable={!!selectedSceneId}
            zenMode={focusMode}
          />
        </div>
      </TabsContent>
      <TabsContent value="sidebar" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
        <Tabs value={sidebarTab} onValueChange={(value) => onChangeSidebarTab(value as SidebarTab)} className="h-full flex flex-col">
          <TabsList className="grid grid-cols-4">
            <TabsTrigger value="copilot">AI</TabsTrigger>
            <TabsTrigger value="plot">剧情</TabsTrigger>
            <TabsTrigger value="version">版本</TabsTrigger>
            <TabsTrigger value="export">导出</TabsTrigger>
          </TabsList>
          <TabsContent value="copilot" className="flex-1 m-0 mt-2 min-h-0">
            <CopilotSidebar context={contextData} className="h-full border-none" />
          </TabsContent>
          <TabsContent value="plot" className="flex-1 m-0 mt-2 min-h-0 rounded border p-2 text-xs">
            <div className="flex gap-2 mb-2">
              <Button size="sm" variant="outline" onClick={() => void onRunSlopDiagnosis()} disabled={isSlopBusy || !selectedSceneId || !selectedManuscriptId}>
                {isSlopBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
                文本
              </Button>
              <Button size="sm" variant="outline" onClick={() => void onRunPlotDiagnosis()} disabled={isPlotBusy || !selectedSceneId || !selectedManuscriptId}>
                {isPlotBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
                剧情
              </Button>
              <Button size="sm" variant="secondary" onClick={() => void onGeneratePlotRevisionCandidate()} disabled={isPlotRevisionBusy || !selectedPlotRun}>
                候选
              </Button>
              <Button size="sm" onClick={() => void onApplyPlotRevision()} disabled={isPlotRevisionBusy || !selectedPlotRun?.revisionCandidateText || selectedPlotRun?.revisionApplied}>
                采纳
              </Button>
            </div>
            <ScrollArea className="h-[calc(100%-2.2rem)]">
              <div className="space-y-2">
                <div className="rounded border p-2">
                  <div className="flex items-center justify-between gap-2">
                    <span>{qualityStatusText(selectedQualityRun)}</span>
                    <Badge variant="outline" className={qualityStatusClass(selectedQualityRun)}>风险 {selectedQualityRun?.overallRiskScore ?? "-"}</Badge>
                  </div>
                  {!!selectedQualityRun?.safeClaim && <div className="mt-1 text-muted-foreground">{selectedQualityRun.safeClaim}</div>}
                  {!!selectedQualityRun?.evidenceLevel && <div className="mt-1 text-muted-foreground">证据等级 {selectedQualityRun.evidenceLevel}</div>}
                </div>
                {(selectedQualityRun?.issues || []).slice(0, 3).map((issue) => (
                  <div key={issue.id} className="rounded border p-2">
                    <div>{slopModuleLabel(issue.module)} · {issue.evidenceLevel || issue.severity}</div>
                    {!!issue.quote && <div className="mt-1">{issue.quote}</div>}
                    {!!issue.repairHint && <div className="text-muted-foreground mt-1">{issue.repairHint}</div>}
                  </div>
                ))}
                <div className="rounded border p-2">
                  <div className="flex items-center justify-between gap-2">
                    <span>{plotStatusText(selectedPlotRun)}</span>
                    <Badge variant="outline" className={plotStatusClass(selectedPlotRun)}>风险 {selectedPlotRun?.overallRiskScore ?? "-"}</Badge>
                  </div>
                  {!!selectedPlotRun?.summary && <div className="mt-1 text-muted-foreground">{selectedPlotRun.summary}</div>}
                </div>
                {(selectedPlotRun?.issues || []).map((issue) => (
                  <div key={issue.id} className="rounded border p-2">
                    <div>{plotDimensionLabel(issue.dimension)} · {issue.severity}</div>
                    {!!issue.minimalFix && <div className="text-muted-foreground mt-1">{issue.minimalFix}</div>}
                  </div>
                ))}
                {!!selectedPlotRun?.revisionCandidateText && (
                  <div className="rounded border p-2 whitespace-pre-wrap">{selectedPlotRun.revisionCandidateText}</div>
                )}
              </div>
            </ScrollArea>
          </TabsContent>
          <TabsContent value="version" className="flex-1 m-0 mt-2 min-h-0 rounded border p-2 text-xs">
            <Button size="sm" variant="outline" className="mb-2" onClick={() => void onLoadVersions()}>刷新版本</Button>
            <ScrollArea className="h-[calc(100%-2.2rem)]">
              {versions.map((version) => (
                <div key={version.id} className="rounded border p-2 mb-2">
                  <div>{version.label}</div>
                  <div className="text-muted-foreground">{formatDateTime(version.createdAt)}</div>
                </div>
              ))}
            </ScrollArea>
          </TabsContent>
          <TabsContent value="export" className="flex-1 m-0 mt-2 min-h-0 rounded border p-2 text-xs">
            <Button size="sm" onClick={() => void onCreateExportJob()} className="mb-2">创建导出任务</Button>
            <ScrollArea className="h-[calc(100%-2.2rem)]">
              {exportJobs.map((job) => (
                <div key={job.id} className="rounded border p-2 mb-2">{job.fileName || job.id}</div>
              ))}
            </ScrollArea>
          </TabsContent>
        </Tabs>
      </TabsContent>
    </Tabs>
  );
}
