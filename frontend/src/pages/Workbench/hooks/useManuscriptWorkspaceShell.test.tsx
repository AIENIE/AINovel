import { act, renderHook, waitFor } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { useManuscriptWorkspaceShell } from "./useManuscriptWorkspaceShell";

describe("useManuscriptWorkspaceShell", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("jumps to the next scene and opens its tab when needed", async () => {
    vi.spyOn(api.v2.workspace, "listShortcuts").mockResolvedValue([] as any);
    window.innerWidth = 1440;

    const { result } = renderHook(() => {
      const [leftOpen, setLeftOpen] = useState(true);
      const [rightOpen, setRightOpen] = useState(true);
      const [openSceneIds, setOpenSceneIds] = useState<string[]>(["scene-1"]);
      const [selectedSceneId, setSelectedSceneId] = useState("scene-1");
      const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>(["scene-1"]);

      return {
        leftOpen,
        rightOpen,
        openSceneIds,
        selectedSceneId,
        selectedSceneIds,
        ...useManuscriptWorkspaceShell({
          closeSceneTab: vi.fn(),
          createSceneInCurrentChapter: vi.fn(),
          handleManualSave: vi.fn(),
          isSidebarOpen: rightOpen,
          leftPanelOpen: leftOpen,
          openSceneIds,
          selectedSceneId,
          sceneRows: [{ id: "scene-1" }, { id: "scene-2" }],
          setIsSidebarOpen: setRightOpen,
          setLeftPanelOpen: setLeftOpen,
          setOpenSceneIds,
          setSelectedSceneId,
          setSelectedSceneIds,
          setSidebarTab: vi.fn(),
        }),
      };
    });

    await waitFor(() => {
      expect(api.v2.workspace.listShortcuts).toHaveBeenCalled();
    });

    act(() => {
      result.current.jumpScene(1);
    });

    expect(result.current.selectedSceneId).toBe("scene-2");
    expect(result.current.selectedSceneIds).toEqual(["scene-2"]);
    expect(result.current.openSceneIds).toEqual(["scene-1", "scene-2"]);
  });

  it("restores panel visibility after leaving focus mode", async () => {
    vi.spyOn(api.v2.workspace, "listShortcuts").mockResolvedValue([] as any);
    window.innerWidth = 1440;

    const { result } = renderHook(() => {
      const [leftOpen, setLeftOpen] = useState(true);
      const [rightOpen, setRightOpen] = useState(true);
      const [openSceneIds, setOpenSceneIds] = useState<string[]>(["scene-1"]);
      const [selectedSceneId, setSelectedSceneId] = useState("scene-1");
      const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>(["scene-1"]);

      return {
        leftOpen,
        rightOpen,
        ...useManuscriptWorkspaceShell({
          closeSceneTab: vi.fn(),
          createSceneInCurrentChapter: vi.fn(),
          handleManualSave: vi.fn(),
          isSidebarOpen: rightOpen,
          leftPanelOpen: leftOpen,
          openSceneIds,
          selectedSceneId,
          sceneRows: [{ id: "scene-1" }, { id: "scene-2" }],
          setIsSidebarOpen: setRightOpen,
          setLeftPanelOpen: setLeftOpen,
          setOpenSceneIds,
          setSelectedSceneId,
          setSelectedSceneIds,
          setSidebarTab: vi.fn(),
        }),
      };
    });

    await waitFor(() => {
      expect(api.v2.workspace.listShortcuts).toHaveBeenCalled();
    });
    vi.useFakeTimers();

    act(() => {
      result.current.toggleFocusMode();
    });

    expect(result.current.focusMode).toBe(true);
    expect(result.current.leftOpen).toBe(false);
    expect(result.current.rightOpen).toBe(false);

    act(() => {
      result.current.toggleFocusMode();
      vi.runAllTimers();
    });

    expect(result.current.focusMode).toBe(false);
    expect(result.current.leftOpen).toBe(true);
    expect(result.current.rightOpen).toBe(true);
  });
});
