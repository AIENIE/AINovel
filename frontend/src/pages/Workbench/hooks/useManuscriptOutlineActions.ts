import { useCallback } from "react";
import type { Dispatch, SetStateAction } from "react";
import { api } from "@/lib/api-client";
import type { Outline } from "@/types";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: string;
}) => void;

type SceneRow = {
  chapterId: string;
  id: string;
};

type UseManuscriptOutlineActionsOptions = {
  batchMoveChapterId: string;
  outlineDraft: Outline | null;
  replaceOutline: (outline: Outline) => void;
  sceneMap: Record<string, SceneRow | undefined>;
  sceneRows: SceneRow[];
  selectedOutlineId: string;
  selectedSceneId: string;
  selectedSceneIds: string[];
  setOpenSceneIds: Dispatch<SetStateAction<string[]>>;
  setOutlineDraft: Dispatch<SetStateAction<Outline | null>>;
  setSelectedSceneId: Dispatch<SetStateAction<string>>;
  setSelectedSceneIds: Dispatch<SetStateAction<string[]>>;
  toast: ToastFn;
};

const cloneOutline = (outline: Outline) => JSON.parse(JSON.stringify(outline)) as Outline;

export function useManuscriptOutlineActions({
  batchMoveChapterId,
  outlineDraft,
  replaceOutline,
  sceneMap,
  sceneRows,
  selectedOutlineId,
  selectedSceneId,
  selectedSceneIds,
  setOpenSceneIds,
  setOutlineDraft,
  setSelectedSceneId,
  setSelectedSceneIds,
  toast,
}: UseManuscriptOutlineActionsOptions) {
  const persistOutlineDraft = useCallback(
    async (nextOutline: Outline) => {
      if (!selectedOutlineId) return;
      try {
        const saved = await api.outlines.save(selectedOutlineId, nextOutline);
        replaceOutline(saved);
      } catch (e: any) {
        toast({ variant: "destructive", title: "保存大纲顺序失败", description: e.message });
      }
    },
    [replaceOutline, selectedOutlineId, toast],
  );

  const moveScene = useCallback(
    async (sourceSceneId: string, targetSceneId: string) => {
      if (!outlineDraft || !sourceSceneId || !targetSceneId || sourceSceneId === targetSceneId) return;
      const sourceRow = sceneMap[sourceSceneId];
      const targetRow = sceneMap[targetSceneId];
      if (!sourceRow || !targetRow) return;

      const nextOutline = cloneOutline(outlineDraft);
      const sourceChapter = nextOutline.chapters.find((chapter) => chapter.id === sourceRow.chapterId);
      const targetChapter = nextOutline.chapters.find((chapter) => chapter.id === targetRow.chapterId);
      if (!sourceChapter || !targetChapter) return;

      const sourceIndex = sourceChapter.scenes.findIndex((scene) => scene.id === sourceSceneId);
      const targetIndex = targetChapter.scenes.findIndex((scene) => scene.id === targetSceneId);
      if (sourceIndex < 0 || targetIndex < 0) return;

      const [moved] = sourceChapter.scenes.splice(sourceIndex, 1);
      targetChapter.scenes.splice(targetIndex, 0, moved);
      setOutlineDraft(nextOutline);
      await persistOutlineDraft(nextOutline);
    },
    [outlineDraft, persistOutlineDraft, sceneMap, setOutlineDraft],
  );

  const moveChapter = useCallback(
    async (sourceChapterId: string, targetChapterId: string) => {
      if (!outlineDraft || !sourceChapterId || !targetChapterId || sourceChapterId === targetChapterId) return;
      const sourceIndex = outlineDraft.chapters.findIndex((chapter) => chapter.id === sourceChapterId);
      const targetIndex = outlineDraft.chapters.findIndex((chapter) => chapter.id === targetChapterId);
      if (sourceIndex < 0 || targetIndex < 0) return;

      const nextOutline = cloneOutline(outlineDraft);
      const [moved] = nextOutline.chapters.splice(sourceIndex, 1);
      nextOutline.chapters.splice(targetIndex, 0, moved);
      setOutlineDraft(nextOutline);
      await persistOutlineDraft(nextOutline);
    },
    [outlineDraft, persistOutlineDraft, setOutlineDraft],
  );

  const createSceneInCurrentChapter = useCallback(async () => {
    if (!outlineDraft || !selectedOutlineId) return;
    const row = sceneMap[selectedSceneId] || sceneRows[0];
    const nextOutline = cloneOutline(outlineDraft);
    const chapter =
      (row ? nextOutline.chapters.find((item) => item.id === row.chapterId) : null) || nextOutline.chapters[0];
    if (!chapter) return;
    const sceneId = `scene-${Date.now()}`;
    chapter.scenes.push({
      id: sceneId,
      title: `新场景 ${chapter.scenes.length + 1}`,
      summary: "",
    });
    setOutlineDraft(nextOutline);
    await persistOutlineDraft(nextOutline);
    setSelectedSceneId(sceneId);
    setOpenSceneIds((prev) => (prev.includes(sceneId) ? prev : [...prev, sceneId]));
  }, [
    outlineDraft,
    persistOutlineDraft,
    sceneMap,
    sceneRows,
    selectedOutlineId,
    selectedSceneId,
    setOpenSceneIds,
    setOutlineDraft,
    setSelectedSceneId,
  ]);

  const deleteSceneFromOutline = useCallback(
    async (sceneId: string, chapterId: string) => {
      if (!outlineDraft) return;
      const nextOutline = cloneOutline(outlineDraft);
      const targetChapter = nextOutline.chapters.find((item) => item.id === chapterId);
      if (!targetChapter) return;
      targetChapter.scenes = targetChapter.scenes.filter((item) => item.id !== sceneId);
      setOutlineDraft(nextOutline);
      setOpenSceneIds((prev) => prev.filter((id) => id !== sceneId));
      if (selectedSceneId === sceneId) setSelectedSceneId("");
      await persistOutlineDraft(nextOutline);
    },
    [outlineDraft, persistOutlineDraft, selectedSceneId, setOpenSceneIds, setOutlineDraft, setSelectedSceneId],
  );

  const batchDeleteScenes = useCallback(async () => {
    if (!outlineDraft || !selectedSceneIds.length) return;
    const ok = window.confirm(`确认删除已选 ${selectedSceneIds.length} 个场景？`);
    if (!ok) return;
    const selected = new Set(selectedSceneIds);
    const nextOutline = cloneOutline(outlineDraft);
    nextOutline.chapters.forEach((chapter) => {
      chapter.scenes = chapter.scenes.filter((scene) => !selected.has(scene.id));
    });
    setOutlineDraft(nextOutline);
    setOpenSceneIds((prev) => prev.filter((id) => !selected.has(id)));
    setSelectedSceneIds([]);
    setSelectedSceneId("");
    await persistOutlineDraft(nextOutline);
  }, [
    outlineDraft,
    persistOutlineDraft,
    selectedSceneIds,
    setOpenSceneIds,
    setOutlineDraft,
    setSelectedSceneId,
    setSelectedSceneIds,
  ]);

  const batchMoveScenes = useCallback(async () => {
    if (!outlineDraft || !selectedSceneIds.length || !batchMoveChapterId) return;
    const selected = new Set(selectedSceneIds);
    const nextOutline = cloneOutline(outlineDraft);
    const moved: any[] = [];
    nextOutline.chapters.forEach((chapter) => {
      const keep: any[] = [];
      chapter.scenes.forEach((scene) => {
        if (selected.has(scene.id)) moved.push(scene);
        else keep.push(scene);
      });
      chapter.scenes = keep;
    });
    const target = nextOutline.chapters.find((chapter) => chapter.id === batchMoveChapterId);
    if (!target || !moved.length) return;
    target.scenes.push(...moved);
    setOutlineDraft(nextOutline);
    await persistOutlineDraft(nextOutline);
    toast({ title: `已移动 ${moved.length} 个场景` });
  }, [
    batchMoveChapterId,
    outlineDraft,
    persistOutlineDraft,
    selectedSceneIds,
    setOutlineDraft,
    toast,
  ]);

  return {
    batchDeleteScenes,
    batchMoveScenes,
    createSceneInCurrentChapter,
    deleteSceneFromOutline,
    moveChapter,
    moveScene,
    persistOutlineDraft,
  };
}
