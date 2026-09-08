import type { NetworkObject } from "@/lib/api-client";
import type { MouseEvent, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Download, Loader2, Save, Sparkles } from "lucide-react";
import CopilotSidebar from "@/components/ai/CopilotSidebar";
import TiptapEditor from "@/components/editor/TiptapEditor";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import type { Chapter, ContextPreview, PlotQualityRun, SlopQualityRun } from "@/types";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { G2EvaluationSubmitDialog } from "./G2EvaluationSubmitDialog";
import { GenerationFeedbackPanel } from "./GenerationFeedbackPanel";
import { ContextSidebarPanel } from "./ContextSidebarPanel";
import {
  formatDateTime,
  plotDimensionLabel,
  plotStatusClass,
  plotStatusText,
  qualityStatusClass,
  qualityStatusText,
  slopModuleLabel,
} from "./shared";

type SidebarTab = "copilot" | "context" | "feedback" | "version" | "export" | "stats" | "goals" | "plot" | "narrative";
type MobilePane = "outline" | "editor" | "sidebar";

type MobileWorkbenchPanelProps = {
  narrativePanel?: ReactNode;
  content: string;
  contextData: NetworkObject | null;
  contextPreview?: ContextPreview | null;
  exportJobs: NetworkObject[];
  exportDownloadingJobId: string;
  focusMode: boolean;
  isPlotBusy: boolean;
  isPlotRevisionBusy: boolean;
  isSlopBusy: boolean;
  isGenerating: boolean;
  isSaving: boolean;
  generationMode: "fast" | "crafted";
  latestGenerationRunId?: string;
  mobilePane: MobilePane;
  onApplyPlotRevision: () => Promise<void> | void;
  onChangeMobilePane: (pane: MobilePane) => void;
  onChangeSidebarTab: (tab: SidebarTab) => void;
  onCreateExportJob: () => Promise<void> | void;
  onDownloadExport: (job: NetworkObject) => Promise<void> | void;
  onEditorChange: (html: string) => void;
  onGeneratePlotRevisionCandidate: () => Promise<void> | void;
  onGenerateScene: () => Promise<void> | void;
  onManualSave: () => Promise<void> | void;
  onSetGenerationMode: (mode: "fast" | "crafted") => void;
  onLoadContextPreview?: () => Promise<unknown> | void;
  onLoadVersions: () => Promise<unknown> | void;
  onRunPlotDiagnosis: () => Promise<void> | void;
  onRunSlopDiagnosis: () => Promise<void> | void;
  onSelectOutlineScene: (sceneId: string, event?: MouseEvent<HTMLButtonElement>) => void;
  outlineChapters: Chapter[];
  selectedManuscriptId: string;
  selectedPlotRun: PlotQualityRun | null;
  selectedQualityRun: SlopQualityRun | null;
  selectedSceneId: string;
  sidebarTab: SidebarTab;
  versions: NetworkObject[];
};

export function MobileWorkbenchPanel({
  content,
  contextData,
  narrativePanel,
  contextPreview = null,
  exportJobs,
  exportDownloadingJobId,
  focusMode,
  isPlotBusy,
  isPlotRevisionBusy,
  isSlopBusy,
  isGenerating,
  isSaving,
  generationMode,
  latestGenerationRunId,
  mobilePane,
  onApplyPlotRevision,
  onChangeMobilePane,
  onChangeSidebarTab,
  onCreateExportJob,
  onDownloadExport,
  onEditorChange,
  onGeneratePlotRevisionCandidate,
  onGenerateScene,
  onManualSave,
  onSetGenerationMode,
  onLoadContextPreview,
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
  const { t } = useTranslation();
  return (
    <Tabs value={mobilePane} onValueChange={(value) => onChangeMobilePane(value as MobilePane)} className="h-full rounded-lg border bg-background p-2">
      <TabsList className="grid grid-cols-3">
        <TabsTrigger value="outline">{t("mobilePanel.outline")}</TabsTrigger>
        <TabsTrigger value="editor">{t("mobilePanel.editor")}</TabsTrigger>
        <TabsTrigger value="sidebar">{t("mobilePanel.reference")}</TabsTrigger>
      </TabsList>
      <TabsContent value="outline" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
        <ScrollArea className="h-full rounded border p-2">
          {outlineChapters.map((chapter, chapterIndex) => (
            <div key={chapter.id} className="mb-2">
              <div className="text-xs font-semibold text-muted-foreground mb-1">{t("mobilePanel.chapterTitle", { count: chapterIndex + 1, title: chapter.title })}</div>
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
        <div className="flex h-full min-h-0 flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2 rounded border bg-muted/30 p-2">
            <Select value={generationMode} onValueChange={(value) => onSetGenerationMode(value as "fast" | "crafted")}><SelectTrigger className="h-8 w-[108px]"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="fast">{t("mobilePanel.fastGenerate")}</SelectItem><SelectItem value="crafted">{t("mobilePanel.craftedGenerate")}</SelectItem></SelectContent></Select>
            <Button size="sm" onClick={() => void onGenerateScene()} disabled={!selectedManuscriptId || !selectedSceneId || isGenerating}>{isGenerating ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : <Sparkles className="mr-1 h-3.5 w-3.5" />}{t("mobilePanel.generate")}</Button>
            <Button size="sm" variant="outline" onClick={() => void onManualSave()} disabled={!selectedManuscriptId || !selectedSceneId || isSaving}>{isSaving ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : <Save className="mr-1 h-3.5 w-3.5" />}{t("common.save")}</Button>
            <G2EvaluationSubmitDialog manuscriptId={selectedManuscriptId} sceneId={selectedSceneId} />
          </div>
          <div className="min-h-0 flex-1 border rounded overflow-hidden">
          <TiptapEditor
            key={`mobile-editor-${focusMode ? "zen" : "normal"}`}
            content={content}
            onChange={onEditorChange}
            className="h-full"
            editable={!!selectedSceneId}
            zenMode={focusMode}
          />
          </div>
        </div>
      </TabsContent>
      <TabsContent value="sidebar" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
        <Tabs value={sidebarTab} onValueChange={(value) => onChangeSidebarTab(value as SidebarTab)} className="h-full flex flex-col">
          <TabsList className="flex h-auto justify-start overflow-x-auto">
            <TabsTrigger className="shrink-0" value="copilot">AI</TabsTrigger>
            <TabsTrigger className="shrink-0" value="context">{t("mobilePanel.context")}</TabsTrigger>
            <TabsTrigger className="shrink-0" value="narrative">{t("narrative.tab")}</TabsTrigger>
            <TabsTrigger className="shrink-0" value="feedback">{t("generationFeedback.tab")}</TabsTrigger>
            <TabsTrigger className="shrink-0" value="plot">{t("mobilePanel.plot")}</TabsTrigger>
            <TabsTrigger className="shrink-0" value="version">{t("mobilePanel.version")}</TabsTrigger>
            <TabsTrigger className="shrink-0" value="export">{t("mobilePanel.export")}</TabsTrigger>
          </TabsList>
          <TabsContent value="copilot" className="flex-1 m-0 mt-2 min-h-0">
            <CopilotSidebar context={contextData ?? undefined} className="h-full border-none" />
          </TabsContent>
          <ContextSidebarPanel contextPreview={contextPreview} onRefresh={() => onLoadContextPreview?.()} />
          {narrativePanel}
          <GenerationFeedbackPanel
            active={mobilePane === "sidebar" && sidebarTab === "feedback"}
            latestRunId={latestGenerationRunId}
            manuscriptId={selectedManuscriptId}
            sceneId={selectedSceneId}
          />
          <TabsContent value="plot" className="flex-1 m-0 mt-2 min-h-0 rounded border p-2 text-xs">
            <div className="flex gap-2 mb-2">
              <Button size="sm" variant="outline" onClick={() => void onRunSlopDiagnosis()} disabled={isSlopBusy || !selectedSceneId || !selectedManuscriptId}>
                {isSlopBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
                {t("mobilePanel.text")}
              </Button>
              <Button size="sm" variant="outline" onClick={() => void onRunPlotDiagnosis()} disabled={isPlotBusy || !selectedSceneId || !selectedManuscriptId}>
                {isPlotBusy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : null}
                {t("mobilePanel.plot")}
              </Button>
              <Button size="sm" variant="secondary" onClick={() => void onGeneratePlotRevisionCandidate()} disabled={isPlotRevisionBusy || !selectedPlotRun}>
                {t("mobilePanel.candidate")}
              </Button>
              <Button size="sm" onClick={() => void onApplyPlotRevision()} disabled={isPlotRevisionBusy || !selectedPlotRun?.revisionCandidateText || selectedPlotRun?.revisionApplied}>
                {t("mobilePanel.adopt")}
              </Button>
            </div>
            <ScrollArea className="h-[calc(100%-2.2rem)]">
              <div className="space-y-2">
                <div className="rounded border p-2">
                  <div className="flex items-center justify-between gap-2">
                    <span>{qualityStatusText(selectedQualityRun)}</span>
                    <Badge variant="outline" className={qualityStatusClass(selectedQualityRun)}>{t("mobilePanel.risk", { score: selectedQualityRun?.overallRiskScore ?? "-" })}</Badge>
                  </div>
                  {!!selectedQualityRun?.safeClaim && <div className="mt-1 text-muted-foreground">{selectedQualityRun.safeClaim}</div>}
                  {!!selectedQualityRun?.evidenceLevel && <div className="mt-1 text-muted-foreground">{t("mobilePanel.evidenceLevel", { level: selectedQualityRun.evidenceLevel })}</div>}
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
                    <Badge variant="outline" className={plotStatusClass(selectedPlotRun)}>{t("mobilePanel.risk", { score: selectedPlotRun?.overallRiskScore ?? "-" })}</Badge>
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
            <Button size="sm" variant="outline" className="mb-2" onClick={() => void onLoadVersions()}>{t("mobilePanel.refreshVersions")}</Button>
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
            <p className="mb-2 text-muted-foreground">{t("exportPanel.retentionNoteShort")}</p>
            <Button size="sm" onClick={() => void onCreateExportJob()} className="mb-2">{t("exportPanel.createExportJob")}</Button>
            <ScrollArea className="h-[calc(100%-2.2rem)]">
              {exportJobs.map((job) => (
                <div key={job.id} className="flex items-center justify-between gap-2 rounded border p-2 mb-2">
                  <span className="min-w-0 truncate">{job.fileName || job.id}</span>
                  {String(job.status).toLowerCase() === "completed" ? (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={exportDownloadingJobId === String(job.id)}
                      onClick={() => void onDownloadExport(job)}
                    >
                      {exportDownloadingJobId === String(job.id)
                        ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
                        : <Download className="mr-1 h-3.5 w-3.5" />} {t("exportPanel.download")}
                    </Button>
                  ) : null}
                </div>
              ))}
            </ScrollArea>
          </TabsContent>
        </Tabs>
      </TabsContent>
    </Tabs>
  );
}
