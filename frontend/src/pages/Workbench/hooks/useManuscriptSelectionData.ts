import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { MouseEvent } from "react";
import { api } from "@/lib/api-client";
import type { Manuscript, Outline, Story } from "@/types";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: string;
}) => void;

type SceneSelectEvent = Pick<MouseEvent<HTMLElement>, "ctrlKey" | "metaKey" | "shiftKey">;

type SceneRow = {
  chapterId: string;
  chapterTitle: string;
  sceneIndex: number;
  chapterIndex: number;
  scene: any;
  id: string;
  displayName: string;
};

type UseManuscriptSelectionDataOptions = {
  initialStoryId?: string;
  toast: ToastFn;
};

export function useManuscriptSelectionData({
  initialStoryId,
  toast,
}: UseManuscriptSelectionDataOptions) {
  const [stories, setStories] = useState<Story[]>([]);
  const [selectedStoryId, setSelectedStoryId] = useState("");
  const [outlines, setOutlines] = useState<Outline[]>([]);
  const [selectedOutlineId, setSelectedOutlineId] = useState("");
  const [outlineDraft, setOutlineDraft] = useState<Outline | null>(null);
  const [manuscripts, setManuscripts] = useState<Manuscript[]>([]);
  const [selectedManuscriptId, setSelectedManuscriptId] = useState("");
  const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>([]);
  const [openSceneIds, setOpenSceneIds] = useState<string[]>([]);
  const [expandedChapterIds, setExpandedChapterIds] = useState<Record<string, boolean>>({});
  const [selectedSceneId, setSelectedSceneId] = useState("");
  const [characters, setCharacters] = useState<any[]>([]);
  const [batchMoveChapterId, setBatchMoveChapterId] = useState("");
  const lastSelectedSceneRef = useRef("");

  const selectedStory = useMemo(
    () => stories.find((story) => story.id === selectedStoryId) || null,
    [selectedStoryId, stories],
  );
  const selectedOutline = useMemo(
    () => outlines.find((outline) => outline.id === selectedOutlineId) || null,
    [outlines, selectedOutlineId],
  );
  const selectedManuscript = useMemo(
    () => manuscripts.find((manuscript) => manuscript.id === selectedManuscriptId) || null,
    [manuscripts, selectedManuscriptId],
  );
  const sceneRows = useMemo(() => {
    const rows: SceneRow[] = [];
    (outlineDraft?.chapters || []).forEach((chapter, chapterIndex) => {
      chapter.scenes.forEach((scene, sceneIndex) => {
        rows.push({
          chapterId: chapter.id,
          chapterTitle: chapter.title,
          sceneIndex,
          chapterIndex,
          scene,
          id: scene.id,
          displayName: `第${chapterIndex + 1}章 Sc.${sceneIndex + 1} ${scene.title}`,
        });
      });
    });
    return rows;
  }, [outlineDraft]);
  const sceneMap = useMemo(() => Object.fromEntries(sceneRows.map((row) => [row.id, row])), [sceneRows]);
  const chapters = outlineDraft?.chapters || [];

  const replaceManuscript = useCallback((manuscript: Manuscript) => {
    setManuscripts((prev) => prev.map((item) => (item.id === manuscript.id ? manuscript : item)));
  }, []);
  const replaceOutline = useCallback((outline: Outline) => {
    setOutlines((prev) => prev.map((item) => (item.id === outline.id ? outline : item)));
  }, []);

  const reorderOpenTabs = useCallback((fromId: string, toId: string) => {
    if (!fromId || !toId || fromId === toId) return;
    setOpenSceneIds((prev) => {
      const from = prev.indexOf(fromId);
      const to = prev.indexOf(toId);
      if (from < 0 || to < 0) return prev;
      const next = [...prev];
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      return next;
    });
  }, []);

  const handleSceneSelect = useCallback(
    (sceneId: string, event?: SceneSelectEvent) => {
      const orderedIds = sceneRows.map((row) => row.id);
      if (event?.shiftKey && lastSelectedSceneRef.current) {
        const from = orderedIds.indexOf(lastSelectedSceneRef.current);
        const to = orderedIds.indexOf(sceneId);
        if (from >= 0 && to >= 0) {
          const [start, end] = from < to ? [from, to] : [to, from];
          setSelectedSceneIds(orderedIds.slice(start, end + 1));
        }
      } else if (event && (event.metaKey || event.ctrlKey)) {
        setSelectedSceneIds((prev) =>
          prev.includes(sceneId) ? prev.filter((id) => id !== sceneId) : [...prev, sceneId],
        );
      } else {
        setSelectedSceneIds([sceneId]);
      }
      lastSelectedSceneRef.current = sceneId;
      setSelectedSceneId(sceneId);
    },
    [sceneRows],
  );

  const closeSceneTab = useCallback(
    (sceneId: string) => {
      setOpenSceneIds((prev) => {
        const index = prev.indexOf(sceneId);
        if (index < 0) return prev;
        const next = prev.filter((id) => id !== sceneId);
        if (selectedSceneId === sceneId) {
          setSelectedSceneId(next[index] || next[index - 1] || sceneRows[0]?.id || "");
        }
        return next;
      });
    },
    [sceneRows, selectedSceneId],
  );

  const toggleChapterExpanded = useCallback((chapterId: string) => {
    setExpandedChapterIds((prev) => ({ ...prev, [chapterId]: !prev[chapterId] }));
  }, []);

  const loadCharacters = useCallback(async () => {
    if (!selectedStoryId) {
      setCharacters([]);
      return;
    }
    try {
      setCharacters(await api.stories.listCharacters(selectedStoryId));
    } catch {
      setCharacters([]);
    }
  }, [selectedStoryId]);

  const loadStories = useCallback(async () => {
    try {
      const list = await api.stories.list();
      setStories(list);
      if (initialStoryId && list.some((story) => story.id === initialStoryId)) {
        setSelectedStoryId(initialStoryId);
      } else if (list.length > 0) {
        setSelectedStoryId(list[0].id);
      }
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载故事失败", description: e.message });
    }
  }, [initialStoryId, toast]);

  useEffect(() => {
    void loadStories();
  }, [loadStories]);

  useEffect(() => {
    void loadCharacters();
  }, [loadCharacters]);

  useEffect(() => {
    if (!selectedStoryId) return;
    api.outlines
      .listByStory(selectedStoryId)
      .then(async (list) => {
        let next = list;
        if (next.length === 0) {
          const created = await api.outlines.create(selectedStoryId, { title: "主线大纲" });
          next = [created];
        }
        setOutlines(next);
        setSelectedOutlineId((prev) => (prev && next.some((outline) => outline.id === prev) ? prev : next[0].id));
      })
      .catch((e: any) =>
        toast({ variant: "destructive", title: "加载大纲失败", description: e.message }),
      );
  }, [selectedStoryId, toast]);

  useEffect(() => {
    if (!selectedOutlineId) return;
    api.manuscripts
      .listByOutline(selectedOutlineId)
      .then(async (list) => {
        let next = list;
        if (next.length === 0) {
          const created = await api.manuscripts.create(selectedOutlineId, { title: "正文稿" });
          next = [created];
        }
        setManuscripts(next);
        setSelectedManuscriptId((prev) =>
          prev && next.some((manuscript) => manuscript.id === prev) ? prev : next[0].id,
        );
      })
      .catch((e: any) =>
        toast({ variant: "destructive", title: "加载稿件失败", description: e.message }),
      );
  }, [selectedOutlineId, toast]);

  useEffect(() => {
    if (!selectedOutline) {
      setOutlineDraft(null);
      return;
    }
    const copied = JSON.parse(JSON.stringify(selectedOutline)) as Outline;
    setOutlineDraft(copied);
    const expanded: Record<string, boolean> = {};
    copied.chapters.forEach((chapter) => {
      expanded[chapter.id] = true;
    });
    setExpandedChapterIds(expanded);
    setBatchMoveChapterId(copied.chapters[0]?.id || "");
  }, [selectedOutline]);

  useEffect(() => {
    const firstSceneId = sceneRows[0]?.id || "";
    setSelectedSceneId((prev) => {
      if (!prev) return firstSceneId;
      return sceneRows.some((row) => row.id === prev) ? prev : firstSceneId;
    });
  }, [sceneRows]);

  useEffect(() => {
    if (!selectedSceneId) return;
    setOpenSceneIds((prev) => (prev.includes(selectedSceneId) ? prev : [...prev, selectedSceneId]));
    setSelectedSceneIds((prev) => (prev.length ? prev : [selectedSceneId]));
  }, [selectedSceneId]);

  useEffect(() => {
    const validSceneIds = new Set(sceneRows.map((row) => row.id));
    setOpenSceneIds((prev) => prev.filter((sceneId) => validSceneIds.has(sceneId)));
    setSelectedSceneIds((prev) => prev.filter((sceneId) => validSceneIds.has(sceneId)));
  }, [sceneRows]);

  return {
    batchMoveChapterId,
    chapters,
    characters,
    closeSceneTab,
    expandedChapterIds,
    handleSceneSelect,
    manuscripts,
    openSceneIds,
    outlineDraft,
    outlines,
    reorderOpenTabs,
    replaceOutline,
    replaceManuscript,
    sceneMap,
    sceneRows,
    selectedManuscript,
    selectedManuscriptId,
    selectedOutlineId,
    selectedSceneId,
    selectedSceneIds,
    selectedStory,
    selectedStoryId,
    setBatchMoveChapterId,
    setOpenSceneIds,
    setOutlineDraft,
    setSelectedManuscriptId,
    setSelectedOutlineId,
    setSelectedSceneId,
    setSelectedSceneIds,
    setSelectedStoryId,
    stories,
    toggleChapterExpanded,
  };
}
