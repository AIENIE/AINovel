import type { NetworkObject } from "@/lib/api-client";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import CopilotSidebar from "@/components/ai/CopilotSidebar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import type { ContextPreview, PlotQualityRun, PlotQualityTrend, SlopQualityRun } from "@/types";
import { ContextSidebarPanel } from "./ContextSidebarPanel";
import { ExportSidebarPanel } from "./ExportSidebarPanel";
import { GoalsSidebarPanel } from "./GoalsSidebarPanel";
import { GenerationFeedbackPanel } from "./GenerationFeedbackPanel";
import { PlotSidebarPanel } from "./PlotSidebarPanel";
import { StatsSidebarPanel } from "./StatsSidebarPanel";
import { VersionSidebarPanel } from "./VersionSidebarPanel";

type SidebarTab = "copilot" | "context" | "feedback" | "version" | "export" | "stats" | "goals" | "plot" | "narrative";

type DesktopSidebarPanelProps = {
  narrativePanel?: ReactNode;
  aiDiffSummary: string;
  abandonBranch: (branchId: string) => Promise<void> | void;
  applyPlotRevision: () => Promise<void> | void;
  autoSaveConfig: NetworkObject | null;
  branches: NetworkObject[];
  chapterRange: string;
  checkoutBranch: (branchId: string) => Promise<void> | void;
  contextData: NetworkObject | null;
  contextPreview: ContextPreview | null;
  copySlopRewriteTask: (task: NetworkObject, index: number) => Promise<void> | void;
  createBranch: () => Promise<void> | void;
  createExportJob: () => Promise<void> | void;
  createGoal: () => Promise<void> | void;
  createManualVersion: () => Promise<void> | void;
  createTemplate: () => Promise<void> | void;
  currentBranchId: string;
  dailyHeatmap: NetworkObject[];
  deleteGoal: (goalId: string) => Promise<void> | void;
  deleteTemplate: (templateId: string) => Promise<void> | void;
  downloadExport: (job: NetworkObject) => Promise<void> | void;
  diffResult: NetworkObject | null;
  diffViewMode: "split" | "unified";
  exportAuthorName: string;
  exportFormat: string;
  exportDownloadingJobId: string;
  exportJobs: NetworkObject[];
  exportTemplateId: string;
  exportTemplates: NetworkObject[];
  generatePlotRevisionCandidate: () => Promise<void> | void;
  goalTargetValue: number;
  goalType: string;
  goals: NetworkObject[];
  hasMoreVersions: boolean;
  includeTableOfContents: boolean;
  includeTitlePage: boolean;
  isPlotBusy: boolean;
  isPlotRevisionBusy: boolean;
  isSlopBusy: boolean;
  latestGenerationRunId?: string;
  loadContextPreview: () => Promise<unknown> | void;
  loadPlotQuality: () => Promise<unknown> | void;
  loadStats: () => Promise<unknown> | void;
  loadVersions: () => Promise<unknown> | void;
  mergeBranchId: string;
  mergeConflicts: NetworkObject[];
  mergeSelectedBranch: (resolutions?: Record<string, "target" | "source">) => Promise<void> | void;
  mergeStrategy: "REPLACE_ALL" | "SCENE_SELECT";
  newBranchName: string;
  onChangeSidebarTab: (tab: SidebarTab) => void;
  plotDimensionEntries: Array<[string, number]>;
  plotTrend: PlotQualityTrend | null;
  plotTrendChartData: Array<{ label: string; riskScore: number }>;
  rollbackVersion: (versionId: string) => Promise<void> | void;
  runPlotDiagnosis: () => Promise<void> | void;
  runSlopDiagnosis: () => Promise<void> | void;
  runVersionDiff: () => Promise<void> | void;
  saveAutoSaveConfig: () => Promise<void> | void;
  sceneResolutions: Record<string, "target" | "source">;
  selectedDiffVersions: string[];
  selectedManuscriptId: string;
  selectedPlotRun: PlotQualityRun | null;
  selectedQualityRun: SlopQualityRun | null;
  selectedSceneId: string;
  selectedSceneTitle: string;
  setAutoSaveConfig: (config: NetworkObject | null) => void;
  setChapterRange: (value: string) => void;
  setDiffViewMode: (mode: "split" | "unified") => void;
  setExportAuthorName: (value: string) => void;
  setExportFormat: (value: string) => void;
  setExportTemplateId: (value: string) => void;
  setGoalTargetValue: (value: number) => void;
  setGoalType: (value: string) => void;
  setIncludeTableOfContents: (value: boolean) => void;
  setIncludeTitlePage: (value: boolean) => void;
  setMergeBranchId: (value: string) => void;
  setMergeStrategy: (value: "REPLACE_ALL" | "SCENE_SELECT") => void;
  setNewBranchName: (value: string) => void;
  setSceneResolutions: (value: Record<string, "target" | "source">) => void;
  setTemplateDescription: (value: string) => void;
  setTemplateName: (value: string) => void;
  setTxtEncoding: (value: string) => void;
  setVersionVisibleCount: (value: number | ((prev: number) => number)) => void;
  showRightPanel: boolean;
  sidebarTab: SidebarTab;
  summarizeDiff: () => Promise<void> | void;
  templateDescription: string;
  templateName: string;
  toggleVersionSelection: (versionId: string) => void;
  txtEncoding: string;
  updateGoal: (goalId: string, patch: Record<string, unknown>) => Promise<void> | void;
  updateBranch: (branchId: string, patch: Record<string, unknown>) => Promise<void> | void;
  updateTemplate: (template: NetworkObject) => Promise<void> | void;
  versionPageSize: number;
  visibleVersions: NetworkObject[];
  workspaceStats: NetworkObject | null;
};

export function DesktopSidebarPanel({
  aiDiffSummary,
  abandonBranch,
  applyPlotRevision,
  autoSaveConfig,
  branches,
  chapterRange,
  checkoutBranch,
  contextData,
  narrativePanel,
  contextPreview,
  copySlopRewriteTask,
  createBranch,
  createExportJob,
  createGoal,
  createManualVersion,
  createTemplate,
  currentBranchId,
  dailyHeatmap,
  deleteGoal,
  deleteTemplate,
  downloadExport,
  diffResult,
  diffViewMode,
  exportAuthorName,
  exportFormat,
  exportDownloadingJobId,
  exportJobs,
  exportTemplateId,
  exportTemplates,
  generatePlotRevisionCandidate,
  goalTargetValue,
  goalType,
  goals,
  hasMoreVersions,
  includeTableOfContents,
  includeTitlePage,
  isPlotBusy,
  isPlotRevisionBusy,
  isSlopBusy,
  latestGenerationRunId,
  loadContextPreview,
  loadPlotQuality,
  loadStats,
  loadVersions,
  mergeBranchId,
  mergeConflicts,
  mergeSelectedBranch,
  mergeStrategy,
  newBranchName,
  onChangeSidebarTab,
  plotDimensionEntries,
  plotTrend,
  plotTrendChartData,
  rollbackVersion,
  runPlotDiagnosis,
  runSlopDiagnosis,
  runVersionDiff,
  saveAutoSaveConfig,
  sceneResolutions,
  selectedDiffVersions,
  selectedManuscriptId,
  selectedPlotRun,
  selectedQualityRun,
  selectedSceneId,
  selectedSceneTitle,
  setAutoSaveConfig,
  setChapterRange,
  setDiffViewMode,
  setExportAuthorName,
  setExportFormat,
  setExportTemplateId,
  setGoalTargetValue,
  setGoalType,
  setIncludeTableOfContents,
  setIncludeTitlePage,
  setMergeBranchId,
  setMergeStrategy,
  setNewBranchName,
  setSceneResolutions,
  setTemplateDescription,
  setTemplateName,
  setTxtEncoding,
  setVersionVisibleCount,
  showRightPanel,
  sidebarTab,
  summarizeDiff,
  templateDescription,
  templateName,
  toggleVersionSelection,
  txtEncoding,
  updateGoal,
  updateBranch,
  updateTemplate,
  versionPageSize,
  visibleVersions,
  workspaceStats,
}: DesktopSidebarPanelProps) {
  const { t } = useTranslation();
  return (
    <div className={cn("h-full", !showRightPanel && "invisible")}>
      <Tabs value={sidebarTab} onValueChange={(value) => onChangeSidebarTab(value as SidebarTab)} className="h-full flex flex-col">
        <TabsList className="mx-2 mt-2 flex h-auto justify-start overflow-x-auto">
          <TabsTrigger className="shrink-0" value="copilot">copilot</TabsTrigger>
          <TabsTrigger className="shrink-0" value="context">context</TabsTrigger>
          <TabsTrigger className="shrink-0" value="narrative">{t("narrative.tab")}</TabsTrigger>
          <TabsTrigger className="shrink-0" value="feedback">{t("generationFeedback.tab")}</TabsTrigger>
          <TabsTrigger className="shrink-0" value="plot">plot</TabsTrigger>
          <TabsTrigger className="shrink-0" value="version">version</TabsTrigger>
          <TabsTrigger className="shrink-0" value="export">export</TabsTrigger>
          <TabsTrigger className="shrink-0" value="stats">stats</TabsTrigger>
          <TabsTrigger className="shrink-0" value="goals">goals</TabsTrigger>
        </TabsList>

        <TabsContent value="copilot" className="flex-1 m-0 mt-2 min-h-0"><CopilotSidebar context={contextData ?? undefined} className="h-full border-none" /></TabsContent>
        <ContextSidebarPanel contextPreview={contextPreview} onRefresh={loadContextPreview} />
        {narrativePanel}
        <GenerationFeedbackPanel
          active={showRightPanel && sidebarTab === "feedback"}
          latestRunId={latestGenerationRunId}
          manuscriptId={selectedManuscriptId}
          sceneId={selectedSceneId}
        />

        <PlotSidebarPanel
          isPlotBusy={isPlotBusy}
          isPlotRevisionBusy={isPlotRevisionBusy}
          isSlopBusy={isSlopBusy}
          onApplyPlotRevision={applyPlotRevision}
          onCopySlopRewriteTask={copySlopRewriteTask}
          onGeneratePlotRevisionCandidate={generatePlotRevisionCandidate}
          onRefreshPlotQuality={loadPlotQuality}
          onRunPlotDiagnosis={runPlotDiagnosis}
          onRunSlopDiagnosis={runSlopDiagnosis}
          plotDimensionEntries={plotDimensionEntries}
          plotTrend={plotTrend}
          plotTrendChartData={plotTrendChartData}
          selectedManuscriptId={selectedManuscriptId}
          selectedPlotRun={selectedPlotRun}
          selectedQualityRun={selectedQualityRun}
          selectedSceneId={selectedSceneId}
          selectedSceneTitle={selectedSceneTitle}
        />

        <VersionSidebarPanel
          abandonBranch={abandonBranch}
          aiDiffSummary={aiDiffSummary}
          autoSaveConfig={autoSaveConfig}
          branches={branches}
          createBranch={createBranch}
          createManualVersion={createManualVersion}
          checkoutBranch={checkoutBranch}
          currentBranchId={currentBranchId}
          diffResult={diffResult}
          diffViewMode={diffViewMode}
          hasMoreVersions={hasMoreVersions}
          loadVersions={loadVersions}
          mergeBranchId={mergeBranchId}
          mergeConflicts={mergeConflicts}
          mergeSelectedBranch={mergeSelectedBranch}
          mergeStrategy={mergeStrategy}
          newBranchName={newBranchName}
          rollbackVersion={rollbackVersion}
          runVersionDiff={runVersionDiff}
          saveAutoSaveConfig={saveAutoSaveConfig}
          sceneResolutions={sceneResolutions}
          selectedDiffVersions={selectedDiffVersions}
          selectedManuscriptId={selectedManuscriptId}
          setAutoSaveConfig={setAutoSaveConfig}
          setDiffViewMode={setDiffViewMode}
          setMergeBranchId={setMergeBranchId}
          setMergeStrategy={setMergeStrategy}
          setNewBranchName={setNewBranchName}
          setSceneResolutions={setSceneResolutions}
          setVersionVisibleCount={setVersionVisibleCount}
          summarizeDiff={summarizeDiff}
          toggleVersionSelection={toggleVersionSelection}
          updateBranch={updateBranch}
          versionPageSize={versionPageSize}
          visibleVersions={visibleVersions}
        />

        <ExportSidebarPanel
          chapterRange={chapterRange}
          createExportJob={createExportJob}
          createTemplate={createTemplate}
          deleteTemplate={deleteTemplate}
          downloadExport={downloadExport}
          exportAuthorName={exportAuthorName}
          exportFormat={exportFormat}
          exportDownloadingJobId={exportDownloadingJobId}
          exportJobs={exportJobs}
          exportTemplateId={exportTemplateId}
          exportTemplates={exportTemplates}
          includeTableOfContents={includeTableOfContents}
          includeTitlePage={includeTitlePage}
          selectedManuscriptId={selectedManuscriptId}
          setChapterRange={setChapterRange}
          setExportAuthorName={setExportAuthorName}
          setExportFormat={setExportFormat}
          setExportTemplateId={setExportTemplateId}
          setIncludeTableOfContents={setIncludeTableOfContents}
          setIncludeTitlePage={setIncludeTitlePage}
          setTemplateDescription={setTemplateDescription}
          setTemplateName={setTemplateName}
          setTxtEncoding={setTxtEncoding}
          templateDescription={templateDescription}
          templateName={templateName}
          txtEncoding={txtEncoding}
          updateTemplate={updateTemplate}
        />

        <StatsSidebarPanel dailyHeatmap={dailyHeatmap} onRefresh={loadStats} workspaceStats={workspaceStats} />

        <GoalsSidebarPanel
          createGoal={createGoal}
          deleteGoal={deleteGoal}
          goalTargetValue={goalTargetValue}
          goalType={goalType}
          goals={goals}
          setGoalTargetValue={setGoalTargetValue}
          setGoalType={setGoalType}
          updateGoal={updateGoal}
        />
      </Tabs>
    </div>
  );
}
