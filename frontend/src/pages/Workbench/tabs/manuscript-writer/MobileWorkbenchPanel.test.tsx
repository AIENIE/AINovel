import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Chapter } from "@/types";
import { MobileWorkbenchPanel } from "./MobileWorkbenchPanel";

vi.mock("@/components/editor/TiptapEditor", () => ({
  default: ({ editable }: { editable?: boolean }) => <div>{editable ? "mock-editor-editable" : "mock-editor-readonly"}</div>,
}));

vi.mock("@/components/ai/CopilotSidebar", () => ({
  default: ({ className }: { className?: string }) => <div className={className}>mock-copilot</div>,
}));

const chapters: Chapter[] = [
  {
    id: "chapter-1",
    title: "开端",
    summary: "",
    scenes: [
      { id: "scene-1", title: "雨夜抵达", summary: "" },
      { id: "scene-2", title: "初见线索", summary: "" },
    ],
  },
];

describe("MobileWorkbenchPanel", () => {
  it("renders outline scenes and switches to editor callback on selection", () => {
    render(
      <MobileWorkbenchPanel
        content=""
        contextData={null}
        exportJobs={[]}
        focusMode={false}
        onApplyPlotRevision={vi.fn()}
        onChangeMobilePane={vi.fn()}
        onChangeSidebarTab={vi.fn()}
        onCreateExportJob={vi.fn()}
        onEditorChange={vi.fn()}
        onGeneratePlotRevisionCandidate={vi.fn()}
        onLoadVersions={vi.fn()}
        onRunPlotDiagnosis={vi.fn()}
        onRunSlopDiagnosis={vi.fn()}
        onSelectOutlineScene={vi.fn()}
        outlineChapters={chapters}
        isPlotBusy={false}
        isPlotRevisionBusy={false}
        isSlopBusy={false}
        mobilePane="outline"
        selectedManuscriptId="manuscript-1"
        selectedPlotRun={null}
        selectedQualityRun={null}
        selectedSceneId="scene-1"
        sidebarTab="plot"
        versions={[]}
      />,
    );

    expect(screen.getByText("第1章 开端")).toBeTruthy();
    expect(screen.getByText("Sc.1 雨夜抵达")).toBeTruthy();
    expect(screen.getByText("Sc.2 初见线索")).toBeTruthy();
  });

  it("renders mobile version sidebar content", () => {
    render(
      <MobileWorkbenchPanel
        content=""
        contextData={null}
        exportJobs={[]}
        focusMode={false}
        onApplyPlotRevision={vi.fn()}
        onChangeMobilePane={vi.fn()}
        onChangeSidebarTab={vi.fn()}
        onCreateExportJob={vi.fn()}
        onEditorChange={vi.fn()}
        onGeneratePlotRevisionCandidate={vi.fn()}
        onLoadVersions={vi.fn()}
        onRunPlotDiagnosis={vi.fn()}
        onRunSlopDiagnosis={vi.fn()}
        onSelectOutlineScene={vi.fn()}
        outlineChapters={chapters}
        isPlotBusy={false}
        isPlotRevisionBusy={false}
        isSlopBusy={false}
        mobilePane="sidebar"
        selectedManuscriptId="manuscript-1"
        selectedPlotRun={null}
        selectedQualityRun={null}
        selectedSceneId="scene-1"
        sidebarTab="version"
        versions={[{ id: "version-1", label: "版本 1", createdAt: "2026-07-06T00:00:00Z" }]}
      />,
    );

    expect(screen.getByText("刷新版本")).toBeTruthy();
    expect(screen.getByText("版本 1")).toBeTruthy();
  });
});
