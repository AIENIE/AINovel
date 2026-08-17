import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Chapter } from "@/types";
import { MobileWorkbenchPanel } from "./MobileWorkbenchPanel";

vi.mock("@/components/editor/TiptapEditor", () => ({
  default: ({ editable }: { editable?: boolean }) => <div>{editable ? "mock-editor-editable" : "mock-editor-readonly"}</div>,
}));

vi.mock("@/components/ai/CopilotSidebar", () => ({
  default: ({ className }: { className?: string }) => <div className={className}>mock-copilot</div>,
}));

vi.mock("./G2EvaluationSubmitDialog", () => ({
  G2EvaluationSubmitDialog: () => <button>提交 G2 盲测</button>,
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
  it("exposes generation, save, and G2 actions in the mobile editor", () => {
    const onGenerateScene = vi.fn();
    const onManualSave = vi.fn();

    render(
      <MobileWorkbenchPanel
        content=""
        contextData={null}
        exportDownloadingJobId=""
        exportJobs={[]}
        focusMode={false}
        generationMode="fast"
        isGenerating={false}
        isPlotBusy={false}
        isPlotRevisionBusy={false}
        isSaving={false}
        isSlopBusy={false}
        mobilePane="editor"
        onApplyPlotRevision={vi.fn()}
        onChangeMobilePane={vi.fn()}
        onChangeSidebarTab={vi.fn()}
        onCreateExportJob={vi.fn()}
        onDownloadExport={vi.fn()}
        onEditorChange={vi.fn()}
        onGeneratePlotRevisionCandidate={vi.fn()}
        onGenerateScene={onGenerateScene}
        onLoadVersions={vi.fn()}
        onManualSave={onManualSave}
        onRunPlotDiagnosis={vi.fn()}
        onRunSlopDiagnosis={vi.fn()}
        onSelectOutlineScene={vi.fn()}
        onSetGenerationMode={vi.fn()}
        outlineChapters={chapters}
        selectedManuscriptId="manuscript-1"
        selectedPlotRun={null}
        selectedQualityRun={null}
        selectedSceneId="scene-1"
        sidebarTab="plot"
        versions={[]}
      />,
    );

    expect(screen.getByText("快速生成")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "生成" }));
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(onGenerateScene).toHaveBeenCalledTimes(1);
    expect(onManualSave).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "提交 G2 盲测" })).toBeTruthy();
  });

  it("renders outline scenes and switches to editor callback on selection", () => {
    render(
      <MobileWorkbenchPanel
        content=""
        contextData={null}
        exportDownloadingJobId=""
        exportJobs={[]}
        focusMode={false}
        generationMode="fast"
        isGenerating={false}
        isSaving={false}
        onApplyPlotRevision={vi.fn()}
        onChangeMobilePane={vi.fn()}
        onChangeSidebarTab={vi.fn()}
        onCreateExportJob={vi.fn()}
        onDownloadExport={vi.fn()}
        onEditorChange={vi.fn()}
        onGeneratePlotRevisionCandidate={vi.fn()}
        onGenerateScene={vi.fn()}
        onLoadVersions={vi.fn()}
        onManualSave={vi.fn()}
        onRunPlotDiagnosis={vi.fn()}
        onRunSlopDiagnosis={vi.fn()}
        onSelectOutlineScene={vi.fn()}
        onSetGenerationMode={vi.fn()}
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
        exportDownloadingJobId=""
        exportJobs={[]}
        focusMode={false}
        generationMode="fast"
        isGenerating={false}
        isSaving={false}
        onApplyPlotRevision={vi.fn()}
        onChangeMobilePane={vi.fn()}
        onChangeSidebarTab={vi.fn()}
        onCreateExportJob={vi.fn()}
        onDownloadExport={vi.fn()}
        onEditorChange={vi.fn()}
        onGeneratePlotRevisionCandidate={vi.fn()}
        onGenerateScene={vi.fn()}
        onLoadVersions={vi.fn()}
        onManualSave={vi.fn()}
        onRunPlotDiagnosis={vi.fn()}
        onRunSlopDiagnosis={vi.fn()}
        onSelectOutlineScene={vi.fn()}
        onSetGenerationMode={vi.fn()}
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
    expect(screen.getByText("上下文")).toBeTruthy();
    expect(screen.getByText("反馈")).toBeTruthy();
    expect(screen.getByText("版本 1")).toBeTruthy();
  });

  it("renders the scene-scoped context preview on narrow screens", () => {
    render(
      <MobileWorkbenchPanel
        content=""
        contextData={null}
        contextPreview={{
          promptVersion: "scene-generation-v3",
          contextHash: "ctx-hash",
          tokenBudget: 1200,
          tokenUsed: 800,
          sources: [{
            sourceType: "outline_scene",
            sourceId: "scene-1",
            label: "本场大纲",
            reason: "约束场景目标",
            estimatedTokens: 120,
            truncated: false,
          }],
        }}
        exportDownloadingJobId=""
        exportJobs={[]}
        focusMode={false}
        generationMode="fast"
        isGenerating={false}
        isSaving={false}
        onApplyPlotRevision={vi.fn()}
        onChangeMobilePane={vi.fn()}
        onChangeSidebarTab={vi.fn()}
        onCreateExportJob={vi.fn()}
        onDownloadExport={vi.fn()}
        onEditorChange={vi.fn()}
        onGeneratePlotRevisionCandidate={vi.fn()}
        onGenerateScene={vi.fn()}
        onLoadContextPreview={vi.fn()}
        onLoadVersions={vi.fn()}
        onManualSave={vi.fn()}
        onRunPlotDiagnosis={vi.fn()}
        onRunSlopDiagnosis={vi.fn()}
        onSelectOutlineScene={vi.fn()}
        onSetGenerationMode={vi.fn()}
        outlineChapters={chapters}
        isPlotBusy={false}
        isPlotRevisionBusy={false}
        isSlopBusy={false}
        mobilePane="sidebar"
        selectedManuscriptId="manuscript-1"
        selectedPlotRun={null}
        selectedQualityRun={null}
        selectedSceneId="scene-1"
        sidebarTab="context"
        versions={[]}
      />,
    );

    expect(screen.getByText(/scene-generation-v3/)).toBeTruthy();
    expect(screen.getByText("约束场景目标")).toBeTruthy();
  });
});
