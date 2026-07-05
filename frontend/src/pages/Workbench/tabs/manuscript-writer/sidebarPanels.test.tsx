import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Tabs } from "@/components/ui/tabs";
import ManuscriptWriter from "../ManuscriptWriter";
import { PlotSidebarPanel } from "./PlotSidebarPanel";
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
});
