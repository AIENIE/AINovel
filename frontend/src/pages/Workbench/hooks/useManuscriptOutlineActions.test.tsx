import { act, renderHook } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { Outline } from "@/types";
import { useManuscriptOutlineActions } from "./useManuscriptOutlineActions";

const makeOutline = (): Outline => ({
  id: "outline-1",
  storyId: "story-1",
  title: "主线大纲",
  updatedAt: "2026-07-06T00:00:00Z",
  chapters: [
    {
      id: "chapter-1",
      title: "第一章",
      summary: "",
      scenes: [
        { id: "scene-1", title: "场景一", summary: "" },
        { id: "scene-2", title: "场景二", summary: "" },
      ],
    },
    {
      id: "chapter-2",
      title: "第二章",
      summary: "",
      scenes: [{ id: "scene-3", title: "场景三", summary: "" }],
    },
  ],
});

describe("useManuscriptOutlineActions", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("creates a new scene in the currently selected chapter and opens it", async () => {
    const replaceOutline = vi.fn();
    const toast = vi.fn();
    vi.spyOn(Date, "now").mockReturnValue(1700000000000);
    vi.spyOn(api.outlines, "save").mockImplementation(async (_outlineId, outline) => outline as any);

    const { result } = renderHook(() => {
      const [outlineDraft, setOutlineDraft] = useState<Outline | null>(makeOutline());
      const [openSceneIds, setOpenSceneIds] = useState<string[]>(["scene-1"]);
      const [selectedSceneId, setSelectedSceneId] = useState("scene-1");
      const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>(["scene-1"]);
      const sceneRows = (outlineDraft?.chapters || []).flatMap((chapter) =>
        chapter.scenes.map((scene) => ({ chapterId: chapter.id, id: scene.id })),
      );
      const sceneMap = Object.fromEntries(sceneRows.map((row) => [row.id, row]));

      return {
        outlineDraft,
        openSceneIds,
        selectedSceneId,
        ...useManuscriptOutlineActions({
          batchMoveChapterId: "chapter-2",
          outlineDraft,
          replaceOutline,
          sceneMap,
          sceneRows,
          selectedOutlineId: "outline-1",
          selectedSceneId,
          selectedSceneIds,
          setOpenSceneIds,
          setOutlineDraft,
          setSelectedSceneId,
          setSelectedSceneIds,
          toast,
        }),
      };
    });

    await act(async () => {
      await result.current.createSceneInCurrentChapter();
    });

    expect(api.outlines.save).toHaveBeenCalledTimes(1);
    expect(replaceOutline).toHaveBeenCalledTimes(1);
    expect(result.current.outlineDraft?.chapters[0].scenes.at(-1)).toMatchObject({
      id: "scene-1700000000000",
      title: "新场景 3",
    });
    expect(result.current.selectedSceneId).toBe("scene-1700000000000");
    expect(result.current.openSceneIds).toContain("scene-1700000000000");
  });

  it("moves the selected scenes into the target chapter in one batch", async () => {
    const replaceOutline = vi.fn();
    const toast = vi.fn();
    vi.spyOn(api.outlines, "save").mockImplementation(async (_outlineId, outline) => outline as any);

    const { result } = renderHook(() => {
      const [outlineDraft, setOutlineDraft] = useState<Outline | null>(makeOutline());
      const [openSceneIds, setOpenSceneIds] = useState<string[]>(["scene-1", "scene-2"]);
      const [selectedSceneId, setSelectedSceneId] = useState("scene-1");
      const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>(["scene-1", "scene-2"]);
      const sceneRows = (outlineDraft?.chapters || []).flatMap((chapter) =>
        chapter.scenes.map((scene) => ({ chapterId: chapter.id, id: scene.id })),
      );
      const sceneMap = Object.fromEntries(sceneRows.map((row) => [row.id, row]));

      return {
        outlineDraft,
        ...useManuscriptOutlineActions({
          batchMoveChapterId: "chapter-2",
          outlineDraft,
          replaceOutline,
          sceneMap,
          sceneRows,
          selectedOutlineId: "outline-1",
          selectedSceneId,
          selectedSceneIds,
          setOpenSceneIds,
          setOutlineDraft,
          setSelectedSceneId,
          setSelectedSceneIds,
          toast,
        }),
      };
    });

    await act(async () => {
      await result.current.batchMoveScenes();
    });

    expect(api.outlines.save).toHaveBeenCalledTimes(1);
    expect(result.current.outlineDraft?.chapters[0].scenes.map((scene) => scene.id)).toEqual([]);
    expect(result.current.outlineDraft?.chapters[1].scenes.map((scene) => scene.id)).toEqual([
      "scene-3",
      "scene-1",
      "scene-2",
    ]);
    expect(toast).toHaveBeenCalledWith({ title: "已移动 2 个场景" });
  });
});
