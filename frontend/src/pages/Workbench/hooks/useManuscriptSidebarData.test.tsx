import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import { useManuscriptSidebarData } from "./useManuscriptSidebarData";

describe("useManuscriptSidebarData", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads versions and seeds active branch state when the version tab opens", async () => {
    vi.spyOn(api.v2.workspace, "listGoals").mockResolvedValue([] as any);
    vi.spyOn(api.v2.version, "listVersions").mockResolvedValue([{ id: "version-2" }, { id: "version-1" }] as any);
    vi.spyOn(api.v2.version, "getAutoSave").mockResolvedValue({ autoSaveIntervalSeconds: 300, maxAutoVersions: 100 } as any);
    vi.spyOn(api.v2.version, "listBranches").mockResolvedValue([
      { id: "branch-main", status: "active", isMain: true },
      { id: "branch-feature", status: "active", isMain: false },
    ] as any);

    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const { result } = renderHook(
      () =>
        useManuscriptSidebarData({
          applyFetchedManuscript: vi.fn(),
          isSidebarOpen: true,
          selectedManuscriptId: "manuscript-1",
          selectedSceneIds: [],
          selectedStoryId: "story-1",
          sidebarTab: "version",
          toast: vi.fn(),
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.versions).toEqual([{ id: "version-2" }, { id: "version-1" }]);
    });

    expect(api.v2.version.listVersions).toHaveBeenCalledWith("manuscript-1");
    expect(result.current.autoSaveConfig).toEqual({ autoSaveIntervalSeconds: 300, maxAutoVersions: 100 });
    expect(result.current.currentBranchId).toBe("branch-main");
    expect(result.current.mergeBranchId).toBe("branch-feature");
    expect(result.current.visibleVersions).toEqual([{ id: "version-2" }, { id: "version-1" }]);
    expect(result.current.hasMoreVersions).toBe(false);
  });

  it("rejects invalid export chapter ranges before creating a job", async () => {
    vi.spyOn(api.v2.workspace, "listGoals").mockResolvedValue([] as any);
    const createJob = vi.spyOn(api.v2.export, "createJob").mockResolvedValue({} as any);
    const toast = vi.fn();

    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const { result } = renderHook(
      () =>
        useManuscriptSidebarData({
          applyFetchedManuscript: vi.fn(),
          isSidebarOpen: false,
          selectedManuscriptId: "manuscript-2",
          selectedSceneIds: ["scene-1", "scene-2"],
          selectedStoryId: "story-2",
          sidebarTab: "export",
          toast,
        }),
      { wrapper },
    );

    act(() => {
      result.current.setChapterRange("3-a");
    });

    await act(async () => {
      await result.current.createExportJob();
    });

    expect(createJob).not.toHaveBeenCalled();
    expect(toast).toHaveBeenCalledWith(
      expect.objectContaining({
        variant: "destructive",
        title: "章节范围格式错误",
      }),
    );
  });

  it("reuses cached sidebar queries when the same tab remounts", async () => {
    const goalsSpy = vi.spyOn(api.v2.workspace, "listGoals").mockResolvedValue([] as any);
    const versionsSpy = vi.spyOn(api.v2.version, "listVersions").mockResolvedValue([{ id: "version-1" }] as any);
    const autoSaveSpy = vi
      .spyOn(api.v2.version, "getAutoSave")
      .mockResolvedValue({ autoSaveIntervalSeconds: 300, maxAutoVersions: 100 } as any);
    const branchesSpy = vi
      .spyOn(api.v2.version, "listBranches")
      .mockResolvedValue([{ id: "branch-main", status: "active", isMain: true }] as any);
    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const options = {
      applyFetchedManuscript: vi.fn(),
      isSidebarOpen: true,
      selectedManuscriptId: "manuscript-1",
      selectedSceneIds: [],
      selectedStoryId: "story-1",
      sidebarTab: "version" as const,
      toast: vi.fn(),
    };

    const firstRender = renderHook(() => useManuscriptSidebarData(options), { wrapper });

    await waitFor(() => {
      expect(firstRender.result.current.versions).toEqual([{ id: "version-1" }]);
    });

    firstRender.unmount();

    const secondRender = renderHook(() => useManuscriptSidebarData(options), { wrapper });

    await waitFor(() => {
      expect(secondRender.result.current.versions).toEqual([{ id: "version-1" }]);
    });

    expect(goalsSpy).toHaveBeenCalledTimes(1);
    expect(versionsSpy).toHaveBeenCalledTimes(1);
    expect(autoSaveSpy).toHaveBeenCalledTimes(1);
    expect(branchesSpy).toHaveBeenCalledTimes(1);
  });
});
