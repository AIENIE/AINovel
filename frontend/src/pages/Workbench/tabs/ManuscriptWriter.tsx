import { useEffect, useMemo, useRef, useState } from "react";
import type { ImperativePanelHandle } from "react-resizable-panels";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import { cn } from "@/lib/utils";
import { useToast } from "@/components/ui/use-toast";
import { useManuscriptEditorState } from "@/pages/Workbench/hooks/useManuscriptEditorState";
import { useManuscriptOutlineActions } from "@/pages/Workbench/hooks/useManuscriptOutlineActions";
import { useManuscriptQuality } from "@/pages/Workbench/hooks/useManuscriptQuality";
import { useManuscriptSceneGeneration } from "@/pages/Workbench/hooks/useManuscriptSceneGeneration";
import { useManuscriptSelectionData } from "@/pages/Workbench/hooks/useManuscriptSelectionData";
import { useManuscriptWorkspaceShell } from "@/pages/Workbench/hooks/useManuscriptWorkspaceShell";
import { useWorkbenchLayoutPersistence } from "@/pages/Workbench/hooks/useWorkbenchLayoutPersistence";
import { useManuscriptSidebarData } from "@/pages/Workbench/hooks/useManuscriptSidebarData";
import { SceneOutlinePanel } from "./manuscript-writer/SceneOutlinePanel";
import { MobileWorkbenchPanel } from "./manuscript-writer/MobileWorkbenchPanel";
import { DesktopEditorPanel } from "./manuscript-writer/DesktopEditorPanel";
import { DesktopSidebarPanel } from "./manuscript-writer/DesktopSidebarPanel";
import { WorkbenchOverlays } from "./manuscript-writer/WorkbenchOverlays";

interface ManuscriptWriterProps {
  initialStoryId?: string;
}

type SceneStatus = "todo" | "in_progress" | "done";

const sceneStatusClass: Record<SceneStatus, string> = {
  todo: "bg-zinc-300",
  in_progress: "bg-amber-400",
  done: "bg-emerald-500",
};

const ManuscriptWriter = ({ initialStoryId }: ManuscriptWriterProps) => {
  const { toast } = useToast();
  const [sceneStatuses, setSceneStatuses] = useState<Record<string, SceneStatus>>({});
  const [draggingChapterId, setDraggingChapterId] = useState("");
  const [dragOverChapterId, setDragOverChapterId] = useState("");
  const [draggingSceneId, setDraggingSceneId] = useState("");
  const [dragOverSceneId, setDragOverSceneId] = useState("");
  const [draggingTabId, setDraggingTabId] = useState("");
  const leftPanelRef = useRef<ImperativePanelHandle | null>(null);
  const rightPanelRef = useRef<ImperativePanelHandle | null>(null);
  const leftPanelVisibleRef = useRef<boolean | null>(null);
  const rightPanelVisibleRef = useRef<boolean | null>(null);

  const {
    batchMoveChapterId,
    chapters,
    characters,
    closeSceneTab,
    expandedChapterIds,
    handleSceneSelect,
    manuscripts,
    openSceneIds,
    outlineDraft,
    outlines,
    reorderOpenTabs,
    replaceOutline,
    replaceManuscript,
    sceneMap,
    sceneRows,
    selectedManuscript,
    selectedManuscriptId,
    selectedOutlineId,
    selectedSceneId,
    selectedSceneIds,
    selectedStory,
    selectedStoryId,
    setBatchMoveChapterId,
    setOpenSceneIds,
    setOutlineDraft,
    setSelectedManuscriptId,
    setSelectedOutlineId,
    setSelectedSceneId,
    setSelectedSceneIds,
    setSelectedStoryId,
    stories,
    toggleChapterExpanded,
  } = useManuscriptSelectionData({
    initialStoryId,
    toast,
  });

  const {
    isSidebarOpen,
    leftPanelOpen,
    leftPanelSize,
    rightPanelSize,
    setIsSidebarOpen,
    setLeftPanelOpen,
    setLeftPanelSize,
    setRightPanelSize,
    setSidebarTab,
    sidebarTab,
  } = useWorkbenchLayoutPersistence({
    selectedStoryId,
    selectedManuscriptId,
  });

  const {
    applyFetchedManuscript,
    content,
    currentWordCount,
    dirtyScenes,
    handleEditorChange,
    handleManualSave,
    isSaving,
    lastSavedAt,
    persistSection,
    sessionDurationSeconds,
    sessionNetWords,
    setContent,
  } = useManuscriptEditorState({
    autoSaveIntervalSeconds: autoSaveConfig?.autoSaveIntervalSeconds,
    replaceManuscript,
    selectedManuscript,
    selectedManuscriptId,
    selectedSceneId,
    selectedStoryId,
    toast,
  });

  const {
    aiDiffSummary,
    autoSaveConfig,
    branches,
    chapterRange,
    checkoutBranch,
    contextPreview,
    createBranch,
    createExportJob,
    createGoal,
    createManualVersion,
    createTemplate,
    currentBranchId,
    deleteGoal,
    deleteTemplate,
    diffResult,
    diffViewMode,
    exportAuthorName,
    exportFormat,
    exportJobs,
    exportTemplateId,
    exportTemplates,
    goalTargetValue,
    goalType,
    goals,
    hasMoreVersions,
    includeTableOfContents,
    includeTitlePage,
    loadContextPreview,
    loadStats,
    loadVersions,
    mergeBranchId,
    mergeConflicts,
    mergeSelectedBranch,
    mergeStrategy,
    newBranchName,
    rollbackVersion,
    runVersionDiff,
    saveAutoSaveConfig,
    sceneResolutions,
    selectedDiffVersions,
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
    summarizeDiff,
    templateDescription,
    templateName,
    toggleVersionSelection,
    txtEncoding,
    updateGoal,
    updateTemplate,
    versionPageSize,
    versions,
    visibleVersions,
    workspaceStats,
  } = useManuscriptSidebarData({
    applyFetchedManuscript,
    isSidebarOpen,
    selectedManuscriptId,
    selectedSceneIds,
    selectedStoryId,
    sidebarTab,
    toast,
  });

  const activeGoal = goals.find((goal) => String(goal.status || "active").toLowerCase() !== "archived");
  const dailyHeatmap = useMemo(() => (workspaceStats?.dailySeries || []).slice(-30), [workspaceStats]);

  const {
    applyPlotRevision,
    copySlopRewriteTask,
    generatePlotRevisionCandidate,
    isPlotBusy,
    isPlotRevisionBusy,
    isSlopBusy,
    loadPlotQuality,
    loadSlopQuality,
    plotTrend,
    runPlotDiagnosis,
    runSlopDiagnosis,
    selectedPlotRun,
    selectedQualityRun,
  } = useManuscriptQuality({
    applyFetchedManuscript,
    content,
    dirtyScenes,
    persistSection,
    selectedManuscriptId,
    selectedSceneId,
    setContent,
    setSidebarTab,
    toast,
  });

  const plotTrendChartData = useMemo(
    () =>
      (plotTrend?.points || []).map((point) => ({
        ...point,
        label: `${point.chapterOrder + 1}-${point.sceneOrder + 1}`,
      })),
    [plotTrend],
  );
  const plotDimensionEntries = useMemo(
    () => Object.entries(plotTrend?.dimensionCounts || {}).sort((a, b) => b[1] - a[1]),
    [plotTrend],
  );

  const {
    batchDeleteScenes,
    batchMoveScenes,
    createSceneInCurrentChapter,
    deleteSceneFromOutline,
    moveChapter,
    moveScene,
  } = useManuscriptOutlineActions({
    batchMoveChapterId,
    outlineDraft,
    replaceOutline,
    sceneMap,
    sceneRows,
    selectedOutlineId,
    selectedSceneId,
    selectedSceneIds,
    setOpenSceneIds,
    setOutlineDraft,
    setSelectedSceneId,
    setSelectedSceneIds,
    toast,
  });

  const {
    commandQuery,
    focusMode,
    isCommandOpen,
    isMobile,
    jumpScene,
    mobilePane,
    selectedWordCount,
    setCommandQuery,
    setIsCommandOpen,
    setMobilePane,
    shortcuts,
    toggleFocusMode,
  } = useManuscriptWorkspaceShell({
    closeSceneTab,
    createSceneInCurrentChapter,
    handleManualSave,
    isSidebarOpen,
    leftPanelOpen,
    openSceneIds,
    selectedSceneId,
    sceneRows,
    setIsSidebarOpen,
    setLeftPanelOpen,
    setOpenSceneIds,
    setSelectedSceneId,
    setSelectedSceneIds,
    setSidebarTab,
  });

  const showLeftPanel = leftPanelOpen && !focusMode;
  const showRightPanel = isSidebarOpen && !focusMode;

  useEffect(() => {
    if (leftPanelVisibleRef.current === showLeftPanel) return;
    if (showLeftPanel) leftPanelRef.current?.expand();
    else leftPanelRef.current?.collapse();
    leftPanelVisibleRef.current = showLeftPanel;
  }, [showLeftPanel]);

  useEffect(() => {
    if (rightPanelVisibleRef.current === showRightPanel) return;
    if (showRightPanel) rightPanelRef.current?.expand();
    else rightPanelRef.current?.collapse();
    rightPanelVisibleRef.current = showRightPanel;
  }, [showRightPanel]);

  const {
    generateScene,
    isGenerating,
  } = useManuscriptSceneGeneration({
    loadPlotQuality,
    loadSlopQuality,
    replaceManuscript,
    selectedManuscriptId,
    selectedSceneId,
    setContent,
    toast,
  });

  const contextData = useMemo(() => {
    const currentChapter = outlineDraft?.chapters?.find((c) => c.scenes.some((s) => s.id === selectedSceneId));
    const currentScene = currentChapter?.scenes?.find((s) => s.id === selectedSceneId);
    return {
      storyTitle: selectedStory?.title,
      outlineTitle: outlineDraft?.title,
      currentChapter: currentChapter?.title,
      currentScene: currentScene?.title,
      sceneSummary: currentScene?.summary,
      currentContent: content,
    };
  }, [selectedStory, outlineDraft, selectedSceneId, content]);

  const setSceneStatus = (sceneId: string, status: SceneStatus) => {
    setSceneStatuses((prev) => ({ ...prev, [sceneId]: status }));
  };

  return (
    <div className="relative h-[calc(100vh-180px)]">
      {isMobile ? (
        <MobileWorkbenchPanel
          content={content}
          contextData={contextData}
          exportJobs={exportJobs}
          focusMode={focusMode}
          isPlotBusy={isPlotBusy}
          isPlotRevisionBusy={isPlotRevisionBusy}
          isSlopBusy={isSlopBusy}
          mobilePane={mobilePane}
          onApplyPlotRevision={applyPlotRevision}
          onChangeMobilePane={setMobilePane}
          onChangeSidebarTab={setSidebarTab}
          onCreateExportJob={createExportJob}
          onEditorChange={handleEditorChange}
          onGeneratePlotRevisionCandidate={generatePlotRevisionCandidate}
          onLoadVersions={loadVersions}
          onRunPlotDiagnosis={runPlotDiagnosis}
          onRunSlopDiagnosis={runSlopDiagnosis}
          onSelectOutlineScene={handleSceneSelect}
          outlineChapters={outlineDraft?.chapters || []}
          selectedManuscriptId={selectedManuscriptId}
          selectedPlotRun={selectedPlotRun}
          selectedQualityRun={selectedQualityRun}
          selectedSceneId={selectedSceneId}
          sidebarTab={sidebarTab}
          versions={versions}
        />
      ) : (
        <ResizablePanelGroup direction="horizontal" className="h-full rounded-lg border bg-background">
          <ResizablePanel
            ref={leftPanelRef}
            collapsible
            collapsedSize={0}
            defaultSize={leftPanelSize}
            minSize={16}
            maxSize={35}
            onResize={(size) => setLeftPanelSize(Math.round(size))}
          >
            <SceneOutlinePanel
              batchMoveChapterId={batchMoveChapterId}
              chapters={chapters}
              dirtyScenes={dirtyScenes}
              dragOverChapterId={dragOverChapterId}
              dragOverSceneId={dragOverSceneId}
              draggingChapterId={draggingChapterId}
              draggingSceneId={draggingSceneId}
              expandedChapterIds={expandedChapterIds}
              manuscripts={manuscripts}
              onBatchDeleteScenes={batchDeleteScenes}
              onBatchMoveScenes={batchMoveScenes}
              onDeleteScene={deleteSceneFromOutline}
              onHandleSceneSelect={handleSceneSelect}
              onMoveChapter={moveChapter}
              onMoveScene={moveScene}
              onOpenBatchExport={() => {
                setIsSidebarOpen(true);
                setSidebarTab("export");
              }}
              onSelectManuscript={setSelectedManuscriptId}
              onSelectOutline={setSelectedOutlineId}
              onSelectStory={setSelectedStoryId}
              onSetBatchMoveChapterId={setBatchMoveChapterId}
              onSetDragOverChapterId={setDragOverChapterId}
              onSetDragOverSceneId={setDragOverSceneId}
              onSetDraggingChapterId={setDraggingChapterId}
              onSetDraggingSceneId={setDraggingSceneId}
              onSetSceneStatus={setSceneStatus}
              onToggleChapterExpanded={toggleChapterExpanded}
              outlines={outlines}
              sceneStatusClass={sceneStatusClass}
              sceneStatuses={sceneStatuses}
              selectedManuscriptId={selectedManuscriptId}
              selectedOutlineId={selectedOutlineId}
              selectedSceneId={selectedSceneId}
              selectedSceneIds={selectedSceneIds}
              selectedStoryId={selectedStoryId}
              showLeftPanel={showLeftPanel}
              stories={stories}
            />
          </ResizablePanel>
          <ResizableHandle withHandle className={cn(!showLeftPanel && "pointer-events-none opacity-0")} />

        <ResizablePanel minSize={35}>
          <DesktopEditorPanel
            activeGoal={activeGoal}
            content={content}
            currentWordCount={currentWordCount}
            dirtyScenes={dirtyScenes}
            draggingTabId={draggingTabId}
            focusMode={focusMode}
            isGenerating={isGenerating}
            isSaving={isSaving}
            isSidebarOpen={isSidebarOpen}
            lastSavedAt={lastSavedAt}
            onCloseSceneTab={closeSceneTab}
            onEditorChange={handleEditorChange}
            onGenerateScene={generateScene}
            onHandleManualSave={handleManualSave}
            onOpenVersionPanel={() => {
              setIsSidebarOpen(true);
              setSidebarTab("version");
            }}
            onReorderOpenTabs={reorderOpenTabs}
            onSelectScene={setSelectedSceneId}
            onSetDraggingTabId={setDraggingTabId}
            onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
            openSceneIds={openSceneIds}
            sceneMap={sceneMap}
            selectedManuscriptId={selectedManuscriptId}
            selectedPlotRun={selectedPlotRun}
            selectedQualityRun={selectedQualityRun}
            selectedSceneId={selectedSceneId}
            selectedWordCount={selectedWordCount}
            sessionDurationSeconds={sessionDurationSeconds}
            sessionNetWords={sessionNetWords}
          />
        </ResizablePanel>

            <ResizableHandle withHandle className={cn(!showRightPanel && "pointer-events-none opacity-0")} />
            <ResizablePanel
              ref={rightPanelRef}
              collapsible
              collapsedSize={0}
              defaultSize={rightPanelSize}
              minSize={20}
              maxSize={45}
              onResize={(size) => setRightPanelSize(Math.round(size))}
            >
              <DesktopSidebarPanel
                aiDiffSummary={aiDiffSummary}
                applyPlotRevision={applyPlotRevision}
                autoSaveConfig={autoSaveConfig}
                branches={branches}
                chapterRange={chapterRange}
                checkoutBranch={checkoutBranch}
                contextData={contextData}
                contextPreview={contextPreview}
                copySlopRewriteTask={copySlopRewriteTask}
                createBranch={createBranch}
                createExportJob={createExportJob}
                createGoal={createGoal}
                createManualVersion={createManualVersion}
                createTemplate={createTemplate}
                currentBranchId={currentBranchId}
                dailyHeatmap={dailyHeatmap}
                deleteGoal={deleteGoal}
                deleteTemplate={deleteTemplate}
                diffResult={diffResult}
                diffViewMode={diffViewMode}
                exportAuthorName={exportAuthorName}
                exportFormat={exportFormat}
                exportJobs={exportJobs}
                exportTemplateId={exportTemplateId}
                exportTemplates={exportTemplates}
                generatePlotRevisionCandidate={generatePlotRevisionCandidate}
                goalTargetValue={goalTargetValue}
                goalType={goalType}
                goals={goals}
                hasMoreVersions={hasMoreVersions}
                includeTableOfContents={includeTableOfContents}
                includeTitlePage={includeTitlePage}
                isPlotBusy={isPlotBusy}
                isPlotRevisionBusy={isPlotRevisionBusy}
                isSlopBusy={isSlopBusy}
                loadContextPreview={loadContextPreview}
                loadPlotQuality={loadPlotQuality}
                loadStats={loadStats}
                loadVersions={loadVersions}
                mergeBranchId={mergeBranchId}
                mergeConflicts={mergeConflicts}
                mergeSelectedBranch={mergeSelectedBranch}
                mergeStrategy={mergeStrategy}
                newBranchName={newBranchName}
                onChangeSidebarTab={setSidebarTab}
                plotDimensionEntries={plotDimensionEntries}
                plotTrend={plotTrend}
                plotTrendChartData={plotTrendChartData}
                rollbackVersion={rollbackVersion}
                runPlotDiagnosis={runPlotDiagnosis}
                runSlopDiagnosis={runSlopDiagnosis}
                runVersionDiff={runVersionDiff}
                saveAutoSaveConfig={saveAutoSaveConfig}
                sceneResolutions={sceneResolutions}
                selectedDiffVersions={selectedDiffVersions}
                selectedManuscriptId={selectedManuscriptId}
                selectedPlotRun={selectedPlotRun}
                selectedQualityRun={selectedQualityRun}
                selectedSceneId={selectedSceneId}
                selectedSceneTitle={sceneMap[selectedSceneId]?.scene?.title || ""}
                setAutoSaveConfig={setAutoSaveConfig}
                setChapterRange={setChapterRange}
                setDiffViewMode={setDiffViewMode}
                setExportAuthorName={setExportAuthorName}
                setExportFormat={setExportFormat}
                setExportTemplateId={setExportTemplateId}
                setGoalTargetValue={setGoalTargetValue}
                setGoalType={setGoalType}
                setIncludeTableOfContents={setIncludeTableOfContents}
                setIncludeTitlePage={setIncludeTitlePage}
                setMergeBranchId={setMergeBranchId}
                setMergeStrategy={setMergeStrategy}
                setNewBranchName={setNewBranchName}
                setSceneResolutions={setSceneResolutions}
                setTemplateDescription={setTemplateDescription}
                setTemplateName={setTemplateName}
                setTxtEncoding={setTxtEncoding}
                setVersionVisibleCount={setVersionVisibleCount}
                showRightPanel={showRightPanel}
                sidebarTab={sidebarTab}
                summarizeDiff={summarizeDiff}
                templateDescription={templateDescription}
                templateName={templateName}
                toggleVersionSelection={toggleVersionSelection}
                txtEncoding={txtEncoding}
                updateGoal={updateGoal}
                updateTemplate={updateTemplate}
                versionPageSize={versionPageSize}
                visibleVersions={visibleVersions}
                workspaceStats={workspaceStats}
              />
            </ResizablePanel>
        </ResizablePanelGroup>
      )}

      <WorkbenchOverlays
        characters={characters}
        commandQuery={commandQuery}
        focusMode={focusMode}
        isCommandOpen={isCommandOpen}
        isMobile={isMobile}
        leftPanelOpen={leftPanelOpen}
        onChangeCommandOpen={setIsCommandOpen}
        onChangeCommandQuery={setCommandQuery}
        onHandleManualSave={handleManualSave}
        onJumpScene={jumpScene}
        onOpenCharacterContext={(name) => {
          setIsSidebarOpen(true);
          setSidebarTab("context");
          setCommandQuery(name);
        }}
        onSelectCommandScene={setSelectedSceneId}
        onToggleFocusMode={toggleFocusMode}
        onToggleLeftPanelOpen={() => setLeftPanelOpen((prev) => !prev)}
        onToggleSidebarOpen={() => setIsSidebarOpen((prev) => !prev)}
        sceneRows={sceneRows.map((row) => ({ id: row.id, displayName: row.displayName }))}
        shortcuts={shortcuts}
      />
    </div>
  );
};

export default ManuscriptWriter;
