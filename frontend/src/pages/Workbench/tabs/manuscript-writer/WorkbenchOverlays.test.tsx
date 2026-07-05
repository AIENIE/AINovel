import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WorkbenchOverlays } from "./WorkbenchOverlays";

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.stubGlobal("ResizeObserver", ResizeObserverMock);
window.HTMLElement.prototype.scrollIntoView = vi.fn();

describe("WorkbenchOverlays", () => {
  it("renders desktop floating controls and command palette entries", () => {
    render(
      <WorkbenchOverlays
        characters={[{ id: "character-1", name: "林雾" }]}
        commandQuery=""
        focusMode={false}
        isCommandOpen={true}
        isMobile={false}
        leftPanelOpen={true}
        onChangeCommandQuery={vi.fn()}
        onChangeCommandOpen={vi.fn()}
        onHandleManualSave={vi.fn()}
        onJumpScene={vi.fn()}
        onOpenCharacterContext={vi.fn()}
        onSelectCommandScene={vi.fn()}
        onToggleFocusMode={vi.fn()}
        onToggleLeftPanelOpen={vi.fn()}
        onToggleSidebarOpen={vi.fn()}
        sceneRows={[{ id: "scene-1", displayName: "第1章 Sc.1 雨夜抵达" }]}
        shortcuts={{
          save: "Ctrl+S",
          toggle_right_panel: "Ctrl+\\",
          focus_mode: "Ctrl+Shift+F",
          next_chapter: "Alt+]",
          prev_chapter: "Alt+[",
        }}
      />,
    );

    expect(screen.getByTitle("收起左栏")).toBeTruthy();
    expect(screen.getByTitle("专注模式")).toBeTruthy();
    expect(screen.getByText("快捷操作")).toBeTruthy();
    expect(screen.getByText("第1章 Sc.1 雨夜抵达")).toBeTruthy();
    expect(screen.getByText("林雾")).toBeTruthy();
  });
});
