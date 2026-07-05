import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Chapter, Manuscript, Outline, Story } from "@/types";
import { SceneOutlinePanel } from "./SceneOutlinePanel";

const stories: Story[] = [
  {
    id: "story-1",
    title: "主线故事",
    synopsis: "",
    genre: "fantasy",
    tone: "serious",
    status: "draft",
    updatedAt: "2026-07-06T00:00:00Z",
  },
];

const outlines: Outline[] = [
  {
    id: "outline-1",
    storyId: "story-1",
    title: "主线大纲",
    chapters: [],
    updatedAt: "2026-07-06T00:00:00Z",
  },
];

const manuscripts: Manuscript[] = [
  {
    id: "manuscript-1",
    outlineId: "outline-1",
    title: "初稿",
    sections: {},
    updatedAt: "2026-07-06T00:00:00Z",
  },
];

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
  {
    id: "chapter-2",
    title: "分歧",
    summary: "",
    scenes: [{ id: "scene-3", title: "意见冲突", summary: "" }],
  },
];

describe("SceneOutlinePanel", () => {
  it("renders selectors, batch actions, chapters, and scenes", () => {
    render(
      <SceneOutlinePanel
        batchMoveChapterId=""
        chapters={chapters}
        dirtyScenes={{ "scene-2": true }}
        dragOverChapterId=""
        dragOverSceneId=""
        draggingChapterId=""
        draggingSceneId=""
        expandedChapterIds={{}}
        manuscripts={manuscripts}
        onBatchDeleteScenes={vi.fn()}
        onBatchMoveScenes={vi.fn()}
        onDeleteScene={vi.fn()}
        onHandleSceneSelect={vi.fn()}
        onMoveChapter={vi.fn()}
        onMoveScene={vi.fn()}
        onOpenBatchExport={vi.fn()}
        onSelectManuscript={vi.fn()}
        onSelectOutline={vi.fn()}
        onSelectStory={vi.fn()}
        onSetBatchMoveChapterId={vi.fn()}
        onSetDragOverChapterId={vi.fn()}
        onSetDragOverSceneId={vi.fn()}
        onSetDraggingChapterId={vi.fn()}
        onSetDraggingSceneId={vi.fn()}
        onSetSceneStatus={vi.fn()}
        onToggleChapterExpanded={vi.fn()}
        outlines={outlines}
        sceneStatusClass={{
          todo: "bg-zinc-300",
          in_progress: "bg-amber-400",
          done: "bg-emerald-500",
        }}
        sceneStatuses={{ "scene-1": "done" }}
        selectedManuscriptId="manuscript-1"
        selectedOutlineId="outline-1"
        selectedSceneId="scene-1"
        selectedSceneIds={["scene-1", "scene-2"]}
        selectedStoryId="story-1"
        showLeftPanel={true}
        stories={stories}
      />,
    );

    expect(screen.getByText("主线故事")).toBeTruthy();
    expect(screen.getByText("已多选 2 个场景")).toBeTruthy();
    expect(screen.getByText("第1章 开端")).toBeTruthy();
    expect(screen.getByText("雨夜抵达")).toBeTruthy();
    expect(screen.getByText("Sc.2")).toBeTruthy();
    expect(screen.getByText("意见冲突")).toBeTruthy();
  });
});
