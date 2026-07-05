import type { MouseEvent } from "react";
import { ChevronDown, ChevronRight, GripVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from "@/components/ui/context-menu";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import type { Chapter, Manuscript, Outline, Story } from "@/types";

type SceneStatus = "todo" | "in_progress" | "done";

type SceneOutlinePanelProps = {
  batchMoveChapterId: string;
  chapters: Chapter[];
  dirtyScenes: Record<string, boolean>;
  dragOverChapterId: string;
  dragOverSceneId: string;
  draggingChapterId: string;
  draggingSceneId: string;
  expandedChapterIds: Record<string, boolean>;
  manuscripts: Manuscript[];
  onBatchDeleteScenes: () => Promise<void> | void;
  onBatchMoveScenes: () => Promise<void> | void;
  onDeleteScene: (sceneId: string, chapterId: string) => Promise<void> | void;
  onHandleSceneSelect: (sceneId: string, event?: MouseEvent<HTMLElement>) => void;
  onMoveChapter: (sourceChapterId: string, targetChapterId: string) => Promise<void> | void;
  onMoveScene: (sourceSceneId: string, targetSceneId: string) => Promise<void> | void;
  onOpenBatchExport: () => void;
  onSelectManuscript: (manuscriptId: string) => void;
  onSelectOutline: (outlineId: string) => void;
  onSelectStory: (storyId: string) => void;
  onSetBatchMoveChapterId: (chapterId: string) => void;
  onSetDragOverChapterId: (chapterId: string) => void;
  onSetDragOverSceneId: (sceneId: string) => void;
  onSetDraggingChapterId: (chapterId: string) => void;
  onSetDraggingSceneId: (sceneId: string) => void;
  onSetSceneStatus: (sceneId: string, status: SceneStatus) => void;
  onToggleChapterExpanded: (chapterId: string) => void;
  outlines: Outline[];
  sceneStatusClass: Record<SceneStatus, string>;
  sceneStatuses: Record<string, SceneStatus>;
  selectedManuscriptId: string;
  selectedOutlineId: string;
  selectedSceneId: string;
  selectedSceneIds: string[];
  selectedStoryId: string;
  showLeftPanel: boolean;
  stories: Story[];
};

export function SceneOutlinePanel({
  batchMoveChapterId,
  chapters,
  dirtyScenes,
  dragOverChapterId,
  dragOverSceneId,
  draggingChapterId,
  draggingSceneId,
  expandedChapterIds,
  manuscripts,
  onBatchDeleteScenes,
  onBatchMoveScenes,
  onDeleteScene,
  onHandleSceneSelect,
  onMoveChapter,
  onMoveScene,
  onOpenBatchExport,
  onSelectManuscript,
  onSelectOutline,
  onSelectStory,
  onSetBatchMoveChapterId,
  onSetDragOverChapterId,
  onSetDragOverSceneId,
  onSetDraggingChapterId,
  onSetDraggingSceneId,
  onSetSceneStatus,
  onToggleChapterExpanded,
  outlines,
  sceneStatusClass,
  sceneStatuses,
  selectedManuscriptId,
  selectedOutlineId,
  selectedSceneId,
  selectedSceneIds,
  selectedStoryId,
  showLeftPanel,
  stories,
}: SceneOutlinePanelProps) {
  return (
    <div className={cn("h-full flex flex-col gap-2 border-r pr-2", !showLeftPanel && "invisible")}>
      <div className="px-2 pt-2 space-y-2">
        <Select value={selectedStoryId} onValueChange={onSelectStory}>
          <SelectTrigger><SelectValue placeholder="选择故事" /></SelectTrigger>
          <SelectContent>{stories.map((story) => <SelectItem key={story.id} value={story.id}>{story.title}</SelectItem>)}</SelectContent>
        </Select>
        <Select value={selectedOutlineId} onValueChange={onSelectOutline} disabled={!selectedStoryId}>
          <SelectTrigger><SelectValue placeholder="选择大纲" /></SelectTrigger>
          <SelectContent>{outlines.map((outline) => <SelectItem key={outline.id} value={outline.id}>{outline.title}</SelectItem>)}</SelectContent>
        </Select>
        <Select value={selectedManuscriptId} onValueChange={onSelectManuscript} disabled={!selectedOutlineId}>
          <SelectTrigger><SelectValue placeholder="选择稿件" /></SelectTrigger>
          <SelectContent>{manuscripts.map((manuscript) => <SelectItem key={manuscript.id} value={manuscript.id}>{manuscript.title}</SelectItem>)}</SelectContent>
        </Select>
      </div>

      {selectedSceneIds.length > 1 && (
        <div className="px-2 py-2 border-y bg-muted/40 text-xs space-y-2">
          <div className="text-muted-foreground">已多选 {selectedSceneIds.length} 个场景</div>
          <div className="flex flex-wrap items-center gap-2">
            <Button size="sm" variant="outline" className="h-7" onClick={onOpenBatchExport}>
              批量导出
            </Button>
            <Select value={batchMoveChapterId} onValueChange={onSetBatchMoveChapterId}>
              <SelectTrigger className="h-7 w-[130px]">
                <SelectValue placeholder="目标章节" />
              </SelectTrigger>
              <SelectContent>
                {chapters.map((chapter, index) => (
                  <SelectItem key={chapter.id} value={chapter.id}>{`第${index + 1}章`}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button size="sm" variant="outline" className="h-7" onClick={() => void onBatchMoveScenes()}>
              批量移动
            </Button>
            <Button size="sm" variant="destructive" className="h-7" onClick={() => void onBatchDeleteScenes()}>
              批量删除
            </Button>
          </div>
        </div>
      )}

      <ScrollArea className="flex-1 mt-1">
        {chapters.map((chapter, chapterIndex) => (
          <div
            key={chapter.id}
            className={cn(
              "px-2 pb-1 rounded-md",
              dragOverChapterId === chapter.id && draggingChapterId !== chapter.id ? "border border-dashed border-primary" : "",
            )}
            onDragOver={(event) => {
              if (!draggingChapterId || draggingChapterId === chapter.id) return;
              event.preventDefault();
              onSetDragOverChapterId(chapter.id);
            }}
            onDrop={(event) => {
              if (!draggingChapterId) return;
              event.preventDefault();
              void onMoveChapter(draggingChapterId, chapter.id);
              onSetDraggingChapterId("");
              onSetDragOverChapterId("");
            }}
          >
            <button
              type="button"
              draggable
              onDragStart={() => onSetDraggingChapterId(chapter.id)}
              onDragEnd={() => {
                onSetDraggingChapterId("");
                onSetDragOverChapterId("");
              }}
              className="w-full text-left text-xs font-semibold text-muted-foreground flex items-center gap-1"
              onClick={() => onToggleChapterExpanded(chapter.id)}
            >
              <GripVertical className="h-3.5 w-3.5 text-muted-foreground/70" />
              {expandedChapterIds[chapter.id] === false ? <ChevronRight className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
              {`第${chapterIndex + 1}章 ${chapter.title}`}
            </button>

            {expandedChapterIds[chapter.id] !== false && (
              <div className="space-y-1 mt-1">
                {chapter.scenes.map((scene, sceneIndex) => {
                  const selected = selectedSceneIds.includes(scene.id);
                  const active = selectedSceneId === scene.id;
                  return (
                    <ContextMenu key={scene.id}>
                      <ContextMenuTrigger asChild>
                        <div
                          role="button"
                          tabIndex={0}
                          draggable
                          onDragStart={() => onSetDraggingSceneId(scene.id)}
                          onDragOver={(event) => {
                            event.preventDefault();
                            onSetDragOverSceneId(scene.id);
                          }}
                          onDrop={(event) => {
                            event.preventDefault();
                            void onMoveScene(draggingSceneId, scene.id);
                            onSetDraggingSceneId("");
                            onSetDragOverSceneId("");
                          }}
                          onDragEnd={() => {
                            onSetDraggingSceneId("");
                            onSetDragOverSceneId("");
                          }}
                          onKeyDown={(event) => {
                            if (event.key !== "Enter" && event.key !== " ") return;
                            event.preventDefault();
                            onHandleSceneSelect(scene.id);
                          }}
                          className={cn(
                            "w-full rounded-md border px-2 py-1 text-left text-sm flex items-center gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                            active ? "bg-secondary border-primary/40" : "hover:bg-muted",
                            dragOverSceneId === scene.id && draggingSceneId !== scene.id ? "border-primary border-dashed" : "",
                          )}
                          onClick={(event) => onHandleSceneSelect(scene.id, event)}
                        >
                          <GripVertical className="h-3.5 w-3.5 text-muted-foreground/70" />
                          <Checkbox checked={selected} onCheckedChange={() => onHandleSceneSelect(scene.id)} />
                          <span className={cn("h-2 w-2 rounded-full", sceneStatusClass[sceneStatuses[scene.id] || "todo"])} />
                          <span className="text-xs text-muted-foreground">{`Sc.${sceneIndex + 1}`}</span>
                          <span className="truncate">{scene.title}</span>
                          {dirtyScenes[scene.id] && <span className="h-2 w-2 rounded-full bg-amber-500 ml-auto" />}
                        </div>
                      </ContextMenuTrigger>
                      <ContextMenuContent className="w-40">
                        <ContextMenuItem onClick={() => onHandleSceneSelect(scene.id)}>打开场景</ContextMenuItem>
                        <ContextMenuItem onClick={() => onSetSceneStatus(scene.id, "todo")}>标记为待写</ContextMenuItem>
                        <ContextMenuItem onClick={() => onSetSceneStatus(scene.id, "in_progress")}>标记为进行中</ContextMenuItem>
                        <ContextMenuItem onClick={() => onSetSceneStatus(scene.id, "done")}>标记为已完成</ContextMenuItem>
                        <ContextMenuItem onClick={() => void onDeleteScene(scene.id, chapter.id)}>删除场景</ContextMenuItem>
                      </ContextMenuContent>
                    </ContextMenu>
                  );
                })}
              </div>
            )}
          </div>
        ))}
      </ScrollArea>
    </div>
  );
}
