import CopilotSidebar from "@/components/ai/CopilotSidebar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { ContextSidebarPanel } from "./ContextSidebarPanel";
import { ExportSidebarPanel } from "./ExportSidebarPanel";
import { GoalsSidebarPanel } from "./GoalsSidebarPanel";
import { PlotSidebarPanel } from "./PlotSidebarPanel";
import { StatsSidebarPanel } from "./StatsSidebarPanel";
import { VersionSidebarPanel } from "./VersionSidebarPanel";

type SidebarTab = "copilot" | "context" | "version" | "export" | "stats" | "goals" | "plot";

type DesktopSidebarPanelProps = {
  aiDiffSummary: string;
  applyPlotRevision: () => Promise<void> | void;
  autoSaveConfig: any;
  branches: any[];
  chapterRange: string;
  checkoutBranch: (branchId: string) => Promise<void> | void;
  contextData: any;
  contextPreview: any;
  copySlopRewriteTask: (task: any, index: number) => Promise<void> | void;
  createBranch: () => Promise<void> | void;
  createExportJob: () => Promise<void> | void;
  createGoal: () => Promise<void> | void;
  createManualVersion: () => Promise<void> | void;
  createTemplate: () => Promise<void> | void;
  currentBranchId: string;
  dailyHeatmap: any[];
  deleteGoal: (goalId: string) => Promise<void> | void;
  deleteTemplate: (templateId: string) => Promise<void> | void;
  diffResult: any;
  diffViewMode: "split" | "unified";
  exportAuthorName: string;
  exportFormat: string;
  exportJobs: any[];
  exportTemplateId: string;
  exportTemplates: any[];
  generatePlotRevisionCandidate: () => Promise<void> | void;
  goalTargetValue: number;
  goalType: string;
  goals: any[];
  hasMoreVersions: boolean;
  includeTableOfContents: boolean;
  includeTitlePage: boolean;
  isPlotBusy: boolean;
  isPlotRevisionBusy: boolean;
  isSlopBusy: boolean;
  loadContextPreview: () => Promise<unknown> | void;
  loadPlotQuality: () => Promise<unknown> | void;
  loadStats: () => Promise<unknown> | void;
  loadVersions: () => Promise<unknown> | void;
  mergeBranchId: string;
  mergeConflicts: any[];
  mergeSelectedBranch: (resolutions?: Record<string, "target" | "source">) => Promise<void> | void;
  mergeStrategy: "REPLACE_ALL" | "SCENE_SELECT";
  newBranchName: string;
  onChangeSidebarTab: (tab: SidebarTab) => void;
  plotDimensionEntries: Array<[string, number]>;
  plotTrend: any;
  plotTrendChartData: Array<{ label: string; riskScore: number }>;
  rollbackVersion: (versionId: string) => Promise<void> | void;
  runPlotDiagnosis: () => Promise<void> | void;
  runSlopDiagnosis: () => Promise<void> | void;
  runVersionDiff: () => Promise<void> | void;
  saveAutoSaveConfig: () => Promise<void> | void;
  sceneResolutions: Record<string, "target" | "source">;
  selectedDiffVersions: string[];
  selectedManuscriptId: string;
  selectedPlotRun: any;
  selectedQualityRun: any;
  selectedSceneId: string;
  selectedSceneTitle: string;
  setAutoSaveConfig: (config: any) => void;
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
  updateTemplate: (template: any) => Promise<void> | void;
  versionPageSize: number;
  visibleVersions: any[];
  workspaceStats: any;
};

export function DesktopSidebarPanel({
  aiDiffSummary,
  applyPlotRevision,
  autoSaveConfig,
  branches,
  chapterRange,
  checkoutBranch,
  contextData,
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
  diffResult,
  diffViewMode,
  exportAuthorName,
  exportFormat,
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
  updateTemplate,
  versionPageSize,
  visibleVersions,
  workspaceStats,
}: DesktopSidebarPanelProps) {
  return (
    <div className={cn("h-full", !showRightPanel && "invisible")}>
      <Tabs value={sidebarTab} onValueChange={(value) => onChangeSidebarTab(value as SidebarTab)} className="h-full flex flex-col">
        <TabsList className="grid grid-cols-7 mx-2 mt-2">
          <TabsTrigger value="copilot">copilot</TabsTrigger>
          <TabsTrigger value="context">context</TabsTrigger>
          <TabsTrigger value="plot">plot</TabsTrigger>
          <TabsTrigger value="version">version</TabsTrigger>
          <TabsTrigger value="export">export</TabsTrigger>
          <TabsTrigger value="stats">stats</TabsTrigger>
          <TabsTrigger value="goals">goals</TabsTrigger>
        </TabsList>

        <TabsContent value="copilot" className="flex-1 m-0 mt-2 min-h-0"><CopilotSidebar context={contextData} className="h-full border-none" /></TabsContent>
        <ContextSidebarPanel contextPreview={contextPreview} onRefresh={loadContextPreview} />

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
          versionPageSize={versionPageSize}
          visibleVersions={visibleVersions}
        />

        <ExportSidebarPanel
          chapterRange={chapterRange}
          createExportJob={createExportJob}
          createTemplate={createTemplate}
          deleteTemplate={deleteTemplate}
          exportAuthorName={exportAuthorName}
          exportFormat={exportFormat}
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
