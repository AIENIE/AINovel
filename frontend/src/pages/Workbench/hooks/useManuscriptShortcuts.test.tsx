import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, renderHook, waitFor } from "@testing-library/react";
import { api } from "@/lib/api-client";
import { useManuscriptShortcuts } from "./useManuscriptShortcuts";

describe("useManuscriptShortcuts", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
  });

  it("loads and merges persisted shortcut overrides", async () => {
    vi.spyOn(api.v2.workspace, "listShortcuts").mockResolvedValue([
      { action: "save", shortcut: "Ctrl+Shift+S" },
      { action: "next_tab", shortcut: "Alt+L" },
    ] as any);

    const { result } = renderHook(() =>
      useManuscriptShortcuts({
        focusMode: false,
        onAction: vi.fn(),
        onExitFocusMode: vi.fn(),
      }),
    );

    await waitFor(() => {
      expect(result.current.shortcuts.save).toBe("Ctrl+Shift+S");
    });
    expect(result.current.shortcuts.next_tab).toBe("Alt+L");
    expect(result.current.shortcuts.focus_mode).toBe("Ctrl+Shift+F");
  });

  it("dispatches matched shortcuts and ignores blocked editable targets", async () => {
    vi.spyOn(api.v2.workspace, "listShortcuts").mockResolvedValue([] as any);
    const onAction = vi.fn();

    renderHook(() =>
      useManuscriptShortcuts({
        focusMode: false,
        onAction,
        onExitFocusMode: vi.fn(),
      }),
    );

    await waitFor(() => {
      expect(api.v2.workspace.listShortcuts).toHaveBeenCalled();
    });

    const input = document.createElement("input");
    document.body.appendChild(input);

    fireEvent.keyDown(input, { key: "f", ctrlKey: true });
    expect(onAction).not.toHaveBeenCalled();

    fireEvent.keyDown(window, { key: "f", ctrlKey: true });
    expect(onAction).toHaveBeenCalledWith("search_manuscript");
  });

  it("exits focus mode on escape", async () => {
    vi.spyOn(api.v2.workspace, "listShortcuts").mockResolvedValue([] as any);
    const onExitFocusMode = vi.fn();

    renderHook(() =>
      useManuscriptShortcuts({
        focusMode: true,
        onAction: vi.fn(),
        onExitFocusMode,
      }),
    );

    await waitFor(() => {
      expect(api.v2.workspace.listShortcuts).toHaveBeenCalled();
    });

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onExitFocusMode).toHaveBeenCalledTimes(1);
  });
});
