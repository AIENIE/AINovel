import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { api } from "@/lib/api-client";
import { useWorkbenchLayoutPersistence } from "./useWorkbenchLayoutPersistence";

describe("useWorkbenchLayoutPersistence", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("prefers the local cached layout over the server active layout", async () => {
    localStorage.setItem(
      "ainovel.workbench.layout.v2:story-1:manuscript-1",
      JSON.stringify({
        leftPanelOpen: false,
        leftPanelSize: 18,
        rightPanelOpen: false,
        rightPanelSize: 26,
        sidebarTab: "export",
      }),
    );
    vi.spyOn(api.v2.workspace, "listLayouts").mockResolvedValue([
      {
        id: "layout-1",
        isActive: true,
        layout: {
          leftPanelOpen: true,
          leftPanelSize: 30,
          rightPanelOpen: true,
          rightPanelSize: 35,
          sidebarTab: "context",
        },
      },
    ] as any);
    vi.spyOn(api.v2.workspace, "updateLayout").mockResolvedValue({ id: "layout-1" } as any);
    vi.spyOn(api.v2.workspace, "createLayout").mockResolvedValue({ id: "layout-new" } as any);

    const { result } = renderHook(() =>
      useWorkbenchLayoutPersistence({
        selectedStoryId: "story-1",
        selectedManuscriptId: "manuscript-1",
      }),
    );

    await waitFor(() => {
      expect(api.v2.workspace.listLayouts).toHaveBeenCalled();
    });

    expect(result.current.leftPanelOpen).toBe(false);
    expect(result.current.leftPanelSize).toBe(18);
    expect(result.current.isSidebarOpen).toBe(false);
    expect(result.current.rightPanelSize).toBe(26);
    expect(result.current.sidebarTab).toBe("export");
    expect(result.current.activeLayoutId).toBe("layout-1");
  });

  it("uses the server active layout when there is no local cache", async () => {
    vi.spyOn(api.v2.workspace, "listLayouts").mockResolvedValue([
      {
        id: "layout-2",
        isActive: true,
        layout: {
          leftPanelOpen: false,
          leftPanelSize: 20,
          rightPanelOpen: true,
          rightPanelSize: 32,
          sidebarTab: "version",
        },
      },
    ] as any);
    vi.spyOn(api.v2.workspace, "updateLayout").mockResolvedValue({ id: "layout-2" } as any);
    vi.spyOn(api.v2.workspace, "createLayout").mockResolvedValue({ id: "layout-created" } as any);

    const { result } = renderHook(() =>
      useWorkbenchLayoutPersistence({
        selectedStoryId: "story-2",
        selectedManuscriptId: "manuscript-2",
      }),
    );

    await waitFor(() => {
      expect(result.current.activeLayoutId).toBe("layout-2");
    });

    expect(result.current.leftPanelOpen).toBe(false);
    expect(result.current.leftPanelSize).toBe(20);
    expect(result.current.isSidebarOpen).toBe(true);
    expect(result.current.rightPanelSize).toBe(32);
    expect(result.current.sidebarTab).toBe("version");
  });

  it("syncs layout changes to the active server layout after the debounce window", async () => {
    vi.useFakeTimers();
    vi.spyOn(api.v2.workspace, "listLayouts").mockResolvedValue([
      { id: "layout-3", isActive: true, layout: {} },
    ] as any);
    const updateLayout = vi.spyOn(api.v2.workspace, "updateLayout").mockResolvedValue({ id: "layout-3" } as any);
    vi.spyOn(api.v2.workspace, "createLayout").mockResolvedValue({ id: "layout-created" } as any);

    const { result } = renderHook(() =>
      useWorkbenchLayoutPersistence({
        selectedStoryId: "story-3",
        selectedManuscriptId: "manuscript-3",
      }),
    );

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(result.current.activeLayoutId).toBe("layout-3");

    act(() => {
      result.current.setLeftPanelOpen(false);
      result.current.setSidebarTab("stats");
    });

    act(() => {
      vi.advanceTimersByTime(650);
    });

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(updateLayout).toHaveBeenCalledWith("layout-3", {
      layout: {
        leftPanelOpen: false,
        leftPanelSize: 24,
        rightPanelOpen: true,
        rightPanelSize: 30,
        sidebarTab: "stats",
      },
      isActive: true,
    });
  });
});
