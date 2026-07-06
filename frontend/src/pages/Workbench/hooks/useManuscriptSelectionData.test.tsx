import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import { useManuscriptSelectionData } from "./useManuscriptSelectionData";

const makeStory = (id: string, title: string) => ({
  id,
  title,
  synopsis: `${title} synopsis`,
  genre: "fantasy",
  tone: "calm",
  status: "draft" as const,
  updatedAt: "2026-07-06T00:00:00Z",
});

const makeOutline = (id: string, storyId: string) => ({
  id,
  storyId,
  title: `outline-${id}`,
  updatedAt: "2026-07-06T00:00:00Z",
  chapters: [
    {
      id: "chapter-1",
      title: "第一章",
      summary: "",
      scenes: [
        { id: "scene-1", title: "场景一", summary: "summary-1" },
        { id: "scene-2", title: "场景二", summary: "summary-2" },
        { id: "scene-3", title: "场景三", summary: "summary-3" },
      ],
    },
  ],
});

const makeManuscript = (id: string, outlineId: string) => ({
  id,
  outlineId,
  title: `manuscript-${id}`,
  updatedAt: "2026-07-06T00:00:00Z",
  sections: {
    "scene-1": "<p>scene 1</p>",
    "scene-2": "<p>scene 2</p>",
    "scene-3": "<p>scene 3</p>",
  },
});

describe("useManuscriptSelectionData", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("selects the requested story and auto-creates the first outline and manuscript when missing", async () => {
    vi.spyOn(api.stories, "list").mockResolvedValue([
      makeStory("story-1", "故事一"),
      makeStory("story-2", "故事二"),
    ] as any);
    vi.spyOn(api.stories, "listCharacters").mockResolvedValue([{ id: "character-1", name: "主角" }] as any);
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([] as any);
    vi.spyOn(api.outlines, "create").mockResolvedValue(makeOutline("outline-1", "story-2") as any);
    vi.spyOn(api.manuscripts, "listByOutline").mockResolvedValue([] as any);
    vi.spyOn(api.manuscripts, "create").mockResolvedValue(makeManuscript("manuscript-1", "outline-1") as any);

    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const { result } = renderHook(
      () =>
        useManuscriptSelectionData({
          initialStoryId: "story-2",
          toast: vi.fn(),
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.selectedSceneId).toBe("scene-1");
    });

    expect(api.outlines.create).toHaveBeenCalledWith("story-2", { title: "主线大纲" });
    expect(api.manuscripts.create).toHaveBeenCalledWith("outline-1", { title: "正文稿" });
    expect(result.current.selectedStoryId).toBe("story-2");
    expect(result.current.selectedOutlineId).toBe("outline-1");
    expect(result.current.selectedManuscriptId).toBe("manuscript-1");
    expect(result.current.batchMoveChapterId).toBe("chapter-1");
    expect(result.current.selectedSceneIds).toEqual(["scene-1"]);
    expect(result.current.openSceneIds).toEqual(["scene-1"]);
    expect(result.current.characters).toEqual([{ id: "character-1", name: "主角" }]);
  });

  it("supports shift-range scene selection across the current outline order", async () => {
    vi.spyOn(api.stories, "list").mockResolvedValue([makeStory("story-1", "故事一")] as any);
    vi.spyOn(api.stories, "listCharacters").mockResolvedValue([] as any);
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([makeOutline("outline-1", "story-1")] as any);
    vi.spyOn(api.manuscripts, "listByOutline").mockResolvedValue([makeManuscript("manuscript-1", "outline-1")] as any);

    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const { result } = renderHook(
      () =>
        useManuscriptSelectionData({
          toast: vi.fn(),
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.selectedSceneId).toBe("scene-1");
    });

    act(() => {
      result.current.handleSceneSelect("scene-1");
    });
    act(() => {
      result.current.handleSceneSelect("scene-3", {
        ctrlKey: false,
        metaKey: false,
        shiftKey: true,
      });
    });

    expect(result.current.selectedSceneId).toBe("scene-3");
    expect(result.current.selectedSceneIds).toEqual(["scene-1", "scene-2", "scene-3"]);

    await waitFor(() => {
      expect(result.current.openSceneIds).toContain("scene-3");
    });
  });

  it("reuses cached selection queries when the same workbench selection remounts", async () => {
    const storiesSpy = vi.spyOn(api.stories, "list").mockResolvedValue([makeStory("story-1", "故事一")] as any);
    const charactersSpy = vi.spyOn(api.stories, "listCharacters").mockResolvedValue([] as any);
    const outlinesSpy = vi.spyOn(api.outlines, "listByStory").mockResolvedValue([makeOutline("outline-1", "story-1")] as any);
    const manuscriptsSpy = vi
      .spyOn(api.manuscripts, "listByOutline")
      .mockResolvedValue([makeManuscript("manuscript-1", "outline-1")] as any);
    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);

    const firstRender = renderHook(
      () =>
        useManuscriptSelectionData({
          toast: vi.fn(),
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(firstRender.result.current.selectedSceneId).toBe("scene-1");
    });

    firstRender.unmount();

    const secondRender = renderHook(
      () =>
        useManuscriptSelectionData({
          toast: vi.fn(),
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(secondRender.result.current.selectedSceneId).toBe("scene-1");
    });

    expect(storiesSpy).toHaveBeenCalledTimes(1);
    expect(charactersSpy).toHaveBeenCalledTimes(1);
    expect(outlinesSpy).toHaveBeenCalledTimes(1);
    expect(manuscriptsSpy).toHaveBeenCalledTimes(1);
  });
});
