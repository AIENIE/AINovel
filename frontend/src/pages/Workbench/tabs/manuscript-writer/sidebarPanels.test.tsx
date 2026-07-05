import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Tabs } from "@/components/ui/tabs";
import ManuscriptWriter from "../ManuscriptWriter";
import { ContextSidebarPanel } from "./ContextSidebarPanel";
import { ExportSidebarPanel } from "./ExportSidebarPanel";
import { GoalsSidebarPanel } from "./GoalsSidebarPanel";
import { PlotSidebarPanel } from "./PlotSidebarPanel";
import { StatsSidebarPanel } from "./StatsSidebarPanel";
import { VersionSidebarPanel } from "./VersionSidebarPanel";

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

describe("manuscript writer sidebar panels", () => {
  it("keeps the manuscript writer shell importable after panel extraction", () => {
    expect(ManuscriptWriter).toBeTruthy();
  });

  it("renders plot sidebar placeholders", () => {
    render(
      <Tabs value="plot" onValueChange={() => undefined}>
        <PlotSidebarPanel
          isPlotBusy={false}
          isPlotRevisionBusy={false}
          isSlopBusy={false}
          onApplyPlotRevision={vi.fn()}
          onCopySlopRewriteTask={vi.fn()}
          onGeneratePlotRevisionCandidate={vi.fn()}
          onRefreshPlotQuality={vi.fn()}
          onRunPlotDiagnosis={vi.fn()}
          onRunSlopDiagnosis={vi.fn()}
          plotDimensionEntries={[]}
          plotTrend={null}
          plotTrendChartData={[]}
          selectedManuscriptId=""
          selectedPlotRun={null}
          selectedQualityRun={null}
          selectedSceneId=""
          selectedSceneTitle=""
        />
      </Tabs>,
    );

    expect(screen.getByText("文本 Slop 风险")).toBeTruthy();
    expect(screen.getByText("当前场景还没有剧情诊断记录。")).toBeTruthy();
  });

  it("renders version sidebar controls", () => {
    render(
      <Tabs value="version" onValueChange={() => undefined}>
        <VersionSidebarPanel
          aiDiffSummary=""
          autoSaveConfig={{ autoSaveIntervalSeconds: 300, maxAutoVersions: 100 }}
          branches={[]}
          createBranch={vi.fn()}
          createManualVersion={vi.fn()}
          checkoutBranch={vi.fn()}
          currentBranchId=""
          diffResult={null}
          diffViewMode="split"
          hasMoreVersions={false}
          loadVersions={vi.fn()}
          mergeBranchId=""
          mergeConflicts={[]}
          mergeSelectedBranch={vi.fn()}
          mergeStrategy="REPLACE_ALL"
          newBranchName=""
          rollbackVersion={vi.fn()}
          runVersionDiff={vi.fn()}
          saveAutoSaveConfig={vi.fn()}
          sceneResolutions={{}}
          selectedDiffVersions={[]}
          selectedManuscriptId="manuscript-1"
          setAutoSaveConfig={vi.fn()}
          setDiffViewMode={vi.fn()}
          setMergeBranchId={vi.fn()}
          setMergeStrategy={vi.fn()}
          setNewBranchName={vi.fn()}
          setSceneResolutions={vi.fn()}
          setVersionVisibleCount={vi.fn()}
          summarizeDiff={vi.fn()}
          toggleVersionSelection={vi.fn()}
          versionPageSize={10}
          visibleVersions={[]}
        />
      </Tabs>,
    );

    expect(screen.getByText("分支管理")).toBeTruthy();
    expect(screen.getByText("自动快照")).toBeTruthy();
  });

  it("renders context and export sidebar controls", () => {
    const { rerender } = render(
      <Tabs value="context" onValueChange={() => undefined}>
        <ContextSidebarPanel
          contextPreview={{
            tokenUsed: 120,
            tokenBudget: 1200,
            generatedAt: "2026-07-06T00:00:00Z",
            systemPromptEntries: [],
            beforeSceneEntries: [],
            afterSceneEntries: [],
            graphRelations: [],
            activeCharacters: [],
            recentSummary: "",
          }}
          onRefresh={vi.fn()}
        />
      </Tabs>,
    );

    expect(screen.getByText("刷新上下文")).toBeTruthy();

    rerender(
      <Tabs value="export" onValueChange={() => undefined}>
        <ExportSidebarPanel
          chapterRange=""
          createExportJob={vi.fn()}
          createTemplate={vi.fn()}
          deleteTemplate={vi.fn()}
          exportAuthorName=""
          exportFormat="txt"
          exportJobs={[]}
          exportTemplateId=""
          exportTemplates={[]}
          includeTableOfContents={true}
          includeTitlePage={true}
          selectedManuscriptId="manuscript-1"
          setChapterRange={vi.fn()}
          setExportAuthorName={vi.fn()}
          setExportFormat={vi.fn()}
          setExportTemplateId={vi.fn()}
          setIncludeTableOfContents={vi.fn()}
          setIncludeTitlePage={vi.fn()}
          setTemplateDescription={vi.fn()}
          setTemplateName={vi.fn()}
          setTxtEncoding={vi.fn()}
          templateDescription=""
          templateName=""
          txtEncoding="UTF-8"
          updateTemplate={vi.fn()}
        />
      </Tabs>,
    );

    expect(screen.getByText("模板管理")).toBeTruthy();
  });

  it("renders stats and goals sidebar controls", () => {
    const { rerender } = render(
      <Tabs value="stats" onValueChange={() => undefined}>
        <StatsSidebarPanel dailyHeatmap={[]} onRefresh={vi.fn()} workspaceStats={{ totalSessions: 0, totalNetWords: 0 }} />
      </Tabs>,
    );

    expect(screen.getByText("刷新统计")).toBeTruthy();

    rerender(
      <Tabs value="goals" onValueChange={() => undefined}>
        <GoalsSidebarPanel
          createGoal={vi.fn()}
          deleteGoal={vi.fn()}
          goalTargetValue={2000}
          goalType="daily_words"
          goals={[]}
          setGoalTargetValue={vi.fn()}
          setGoalType={vi.fn()}
          updateGoal={vi.fn()}
        />
      </Tabs>,
    );

    expect(screen.getByText("创建目标")).toBeTruthy();
  });
});
