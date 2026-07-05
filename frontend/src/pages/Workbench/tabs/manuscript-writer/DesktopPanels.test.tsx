import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DesktopEditorPanel } from "./DesktopEditorPanel";
import { DesktopSidebarPanel } from "./DesktopSidebarPanel";

vi.mock("@/components/editor/TiptapEditor", () => ({
  default: ({ editable }: { editable?: boolean }) => <div>{editable ? "mock-desktop-editor-editable" : "mock-desktop-editor-readonly"}</div>,
}));

vi.mock("@/components/ai/CopilotSidebar", () => ({
  default: ({ className }: { className?: string }) => <div className={className}>mock-copilot</div>,
}));

vi.mock("recharts", () => {
  const Wrapper = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
  return {
    ResponsiveContainer: Wrapper,
    LineChart: Wrapper,
    CartesianGrid: () => null,
    XAxis: () => null,
    YAxis: () => null,
    Tooltip: () => null,
    Line: () => null,
  };
});

describe("desktop manuscript workbench panels", () => {
  it("renders desktop editor shell controls and stats", () => {
    render(
      <DesktopEditorPanel
        activeGoal={{ currentValue: 200, targetValue: 1000 }}
        content=""
        currentWordCount={120}
        dirtyScenes={{ "scene-1": true }}
        draggingTabId=""
        focusMode={false}
        isGenerating={false}
        isSaving={false}
        isSidebarOpen={true}
        lastSavedAt="2026-07-06 10:00"
        onCloseSceneTab={vi.fn()}
        onEditorChange={vi.fn()}
        onGenerateScene={vi.fn()}
        onHandleManualSave={vi.fn()}
        onOpenVersionPanel={vi.fn()}
        onReorderOpenTabs={vi.fn()}
        onSelectScene={vi.fn()}
        onSetDraggingTabId={vi.fn()}
        onToggleSidebar={vi.fn()}
        openSceneIds={["scene-1"]}
        sceneMap={{ "scene-1": { scene: { title: "雨夜抵达" } } }}
        selectedManuscriptId="manuscript-1"
        selectedPlotRun={null}
        selectedQualityRun={null}
        selectedSceneId="scene-1"
        selectedWordCount={30}
        sessionDurationSeconds={125}
        sessionNetWords={88}
      />,
    );

    expect(screen.getByText("历史版本")).toBeTruthy();
    expect(screen.getByText(/雨夜抵达/)).toBeTruthy();
    expect(screen.getByText("字数 120")).toBeTruthy();
    expect(screen.getByText("目标 200/1000")).toBeTruthy();
  });

  it("renders desktop sidebar tabs and plot placeholder", () => {
    render(
      <DesktopSidebarPanel
        aiDiffSummary=""
        applyPlotRevision={vi.fn()}
        autoSaveConfig={{ autoSaveIntervalSeconds: 300, maxAutoVersions: 100 }}
        branches={[]}
        chapterRange=""
        checkoutBranch={vi.fn()}
        contextData={null}
        contextPreview={null}
        copySlopRewriteTask={vi.fn()}
        createBranch={vi.fn()}
        createExportJob={vi.fn()}
        createGoal={vi.fn()}
        createManualVersion={vi.fn()}
        createTemplate={vi.fn()}
        currentBranchId=""
        dailyHeatmap={[]}
        deleteGoal={vi.fn()}
        deleteTemplate={vi.fn()}
        diffResult={null}
        diffViewMode="split"
        exportAuthorName=""
        exportFormat="txt"
        exportJobs={[]}
        exportTemplateId=""
        exportTemplates={[]}
        generatePlotRevisionCandidate={vi.fn()}
        goalTargetValue={2000}
        goalType="daily_words"
        goals={[]}
        hasMoreVersions={false}
        includeTableOfContents={true}
        includeTitlePage={true}
        isPlotBusy={false}
        isPlotRevisionBusy={false}
        isSlopBusy={false}
        loadContextPreview={vi.fn()}
        loadPlotQuality={vi.fn()}
        loadStats={vi.fn()}
        loadVersions={vi.fn()}
        mergeBranchId=""
        mergeConflicts={[]}
        mergeSelectedBranch={vi.fn()}
        mergeStrategy="REPLACE_ALL"
        newBranchName=""
        onChangeSidebarTab={vi.fn()}
        plotDimensionEntries={[]}
        plotTrend={null}
        plotTrendChartData={[]}
        rollbackVersion={vi.fn()}
        runPlotDiagnosis={vi.fn()}
        runSlopDiagnosis={vi.fn()}
        runVersionDiff={vi.fn()}
        saveAutoSaveConfig={vi.fn()}
        sceneResolutions={{}}
        selectedDiffVersions={[]}
        selectedManuscriptId="manuscript-1"
        selectedPlotRun={null}
        selectedQualityRun={null}
        selectedSceneId="scene-1"
        selectedSceneTitle="雨夜抵达"
        setAutoSaveConfig={vi.fn()}
        setChapterRange={vi.fn()}
        setDiffViewMode={vi.fn()}
        setExportAuthorName={vi.fn()}
        setExportFormat={vi.fn()}
        setExportTemplateId={vi.fn()}
        setGoalTargetValue={vi.fn()}
        setGoalType={vi.fn()}
        setIncludeTableOfContents={vi.fn()}
        setIncludeTitlePage={vi.fn()}
        setMergeBranchId={vi.fn()}
        setMergeStrategy={vi.fn()}
        setNewBranchName={vi.fn()}
        setSceneResolutions={vi.fn()}
        setTemplateDescription={vi.fn()}
        setTemplateName={vi.fn()}
        setTxtEncoding={vi.fn()}
        setVersionVisibleCount={vi.fn()}
        showRightPanel={true}
        sidebarTab="plot"
        summarizeDiff={vi.fn()}
        templateDescription=""
        templateName=""
        toggleVersionSelection={vi.fn()}
        txtEncoding="UTF-8"
        updateGoal={vi.fn()}
        updateTemplate={vi.fn()}
        versionPageSize={10}
        visibleVersions={[]}
        workspaceStats={{ totalSessions: 0, totalNetWords: 0 }}
      />,
    );

    expect(screen.getByText("plot")).toBeTruthy();
    expect(screen.getByText("文本 Slop 风险")).toBeTruthy();
  });
});
