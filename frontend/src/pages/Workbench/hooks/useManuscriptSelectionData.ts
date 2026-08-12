import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { MouseEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Manuscript, Outline } from "@/types";
import { t } from "@/i18n";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: "default" | "destructive";
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
  initialOutlineId?: string;
  initialManuscriptId?: string;
  initialSceneId?: string;
  onSelectionChange?: (selection: { storyId: string; outlineId: string; manuscriptId: string; sceneId: string }) => void;
  toast: ToastFn;
};

const WORKBENCH_QUERY_STALE_TIME = 60_000;

const storiesQueryKey = ["workbench", "stories"] as const;
const charactersQueryKey = (storyId: string) => ["workbench", "story-characters", storyId] as const;
const outlinesQueryKey = (storyId: string) => ["workbench", "story-outlines", storyId] as const;
const manuscriptsQueryKey = (outlineId: string) => ["workbench", "outline-manuscripts", outlineId] as const;

async function fetchStories() {
  return await api.stories.list();
}

async function fetchCharacters(storyId: string) {
  try {
    return await api.stories.listCharacters(storyId);
  } catch {
    return [];
  }
}

async function fetchOutlines(storyId: string) {
  return await api.outlines.listByStory(storyId);
}

async function fetchManuscripts(outlineId: string) {
  return await api.manuscripts.listByOutline(outlineId);
}

export function useManuscriptSelectionData({
  initialStoryId,
  initialOutlineId,
  initialManuscriptId,
  initialSceneId,
  onSelectionChange,
  toast,
}: UseManuscriptSelectionDataOptions) {
  const queryClient = useQueryClient();
  const [selectedStoryId, setSelectedStoryId] = useState("");
  const [selectedOutlineId, setSelectedOutlineId] = useState("");
  const [outlineDraft, setOutlineDraft] = useState<Outline | null>(null);
  const [selectedManuscriptId, setSelectedManuscriptId] = useState("");
  const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>([]);
  const [openSceneIds, setOpenSceneIds] = useState<string[]>([]);
  const [expandedChapterIds, setExpandedChapterIds] = useState<Record<string, boolean>>({});
  const [selectedSceneId, setSelectedSceneId] = useState("");
  const [batchMoveChapterId, setBatchMoveChapterId] = useState("");
  const lastSelectedSceneRef = useRef("");

  const storiesQuery = useQuery({
    queryKey: storiesQueryKey,
    queryFn: fetchStories,
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const stories = storiesQuery.data ?? [];

  const charactersQuery = useQuery({
    queryKey: charactersQueryKey(selectedStoryId),
    queryFn: () => fetchCharacters(selectedStoryId),
    enabled: Boolean(selectedStoryId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const characters = charactersQuery.data ?? [];

  const outlinesQuery = useQuery({
    queryKey: outlinesQueryKey(selectedStoryId),
    queryFn: () => fetchOutlines(selectedStoryId),
    enabled: Boolean(selectedStoryId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const outlines = outlinesQuery.data ?? [];

  const manuscriptsQuery = useQuery({
    queryKey: manuscriptsQueryKey(selectedOutlineId),
    queryFn: () => fetchManuscripts(selectedOutlineId),
    enabled: Boolean(selectedOutlineId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const manuscripts = manuscriptsQuery.data ?? [];

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
          displayName: `${t("common.chapterShort", { count: chapterIndex + 1 })} Sc.${sceneIndex + 1} ${scene.title}`,
        });
      });
    });
    return rows;
  }, [outlineDraft]);
  const sceneMap = useMemo(() => Object.fromEntries(sceneRows.map((row) => [row.id, row])), [sceneRows]);
  const chapters = outlineDraft?.chapters || [];

  const replaceManuscript = useCallback(
    (manuscript: Manuscript) => {
      if (!selectedOutlineId) return;
      queryClient.setQueryData<Manuscript[] | undefined>(manuscriptsQueryKey(selectedOutlineId), (prev) =>
        Array.isArray(prev) ? prev.map((item) => (item.id === manuscript.id ? manuscript : item)) : prev,
      );
    },
    [queryClient, selectedOutlineId],
  );
  const replaceOutline = useCallback(
    (outline: Outline) => {
      if (!selectedStoryId) return;
      queryClient.setQueryData<Outline[] | undefined>(outlinesQueryKey(selectedStoryId), (prev) =>
        Array.isArray(prev) ? prev.map((item) => (item.id === outline.id ? outline : item)) : prev,
      );
    },
    [queryClient, selectedStoryId],
  );

  const selectStoryId = useCallback((storyId: string) => {
    setSelectedStoryId(storyId);
    setSelectedOutlineId("");
    setSelectedManuscriptId("");
    setSelectedSceneId("");
    setSelectedSceneIds([]);
    setOpenSceneIds([]);
    setOutlineDraft(null);
    lastSelectedSceneRef.current = "";
  }, []);

  const selectOutlineId = useCallback((outlineId: string) => {
    setSelectedOutlineId(outlineId);
    setSelectedManuscriptId("");
    setSelectedSceneId("");
    setSelectedSceneIds([]);
    setOpenSceneIds([]);
    setOutlineDraft(null);
    lastSelectedSceneRef.current = "";
  }, []);

  const createOutline = useCallback(async (title: string) => {
    if (!selectedStoryId) return null;
    const created = await api.outlines.create(selectedStoryId, { title: title.trim() || t("selectionData.defaultOutlineTitle") });
    queryClient.setQueryData<Outline[]>(outlinesQueryKey(selectedStoryId), (current = []) => [...current, created]);
    selectOutlineId(created.id);
    toast({ title: t("selectionData.outlineCreated") });
    return created;
  }, [queryClient, selectOutlineId, selectedStoryId, toast]);

  const createManuscript = useCallback(async (title: string) => {
    if (!selectedOutlineId) return null;
    const created = await api.manuscripts.create(selectedOutlineId, { title: title.trim() || t("selectionData.defaultManuscriptTitle") });
    queryClient.setQueryData<Manuscript[]>(manuscriptsQueryKey(selectedOutlineId), (current = []) => [...current, created]);
    setSelectedManuscriptId(created.id);
    toast({ title: t("selectionData.manuscriptCreated") });
    return created;
  }, [queryClient, selectedOutlineId, toast]);

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

  useEffect(() => {
    if (!stories.length) {
      setSelectedStoryId("");
      return;
    }
    setSelectedStoryId((prev) => {
      if (prev && stories.some((story) => story.id === prev)) return prev;
      if (initialStoryId && stories.some((story) => story.id === initialStoryId)) return initialStoryId;
      return stories[0]?.id || "";
    });
  }, [initialStoryId, stories]);

  useEffect(() => {
    if (!selectedStoryId) {
      setSelectedOutlineId("");
      return;
    }
    if (!outlines.length) {
      setSelectedOutlineId("");
      return;
    }
    setSelectedOutlineId((prev) => {
      if (prev && outlines.some((outline) => outline.id === prev)) return prev;
      if (initialOutlineId && outlines.some((outline) => outline.id === initialOutlineId)) return initialOutlineId;
      return outlines[0]?.id || "";
    });
  }, [initialOutlineId, outlines, selectedStoryId]);

  useEffect(() => {
    if (!selectedOutlineId) {
      setSelectedManuscriptId("");
      return;
    }
    if (!manuscripts.length) {
      setSelectedManuscriptId("");
      return;
    }
    setSelectedManuscriptId((prev) => {
      if (prev && manuscripts.some((manuscript) => manuscript.id === prev)) return prev;
      if (initialManuscriptId && manuscripts.some((manuscript) => manuscript.id === initialManuscriptId)) return initialManuscriptId;
      return manuscripts[0]?.id || "";
    });
  }, [initialManuscriptId, manuscripts, selectedOutlineId]);

  useEffect(() => {
    const error = storiesQuery.error as Error | null;
    if (!error) return;
    toast({ variant: "destructive", title: t("selectionData.storyLoadFailed"), description: error.message });
  }, [storiesQuery.error, toast]);

  useEffect(() => {
    const error = outlinesQuery.error as Error | null;
    if (!error) return;
    toast({ variant: "destructive", title: t("selectionData.outlineLoadFailed"), description: error.message });
  }, [outlinesQuery.error, toast]);

  useEffect(() => {
    const error = manuscriptsQuery.error as Error | null;
    if (!error) return;
    toast({ variant: "destructive", title: t("selectionData.manuscriptLoadFailed"), description: error.message });
  }, [manuscriptsQuery.error, toast]);

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
      if (!prev && initialSceneId && sceneRows.some((row) => row.id === initialSceneId)) return initialSceneId;
      if (!prev) return firstSceneId;
      return sceneRows.some((row) => row.id === prev) ? prev : firstSceneId;
    });
  }, [initialSceneId, sceneRows]);

  useEffect(() => {
    if (!selectedStoryId || !stories.some((story) => story.id === selectedStoryId)) return;
    if (outlinesQuery.data === undefined) return;
    if (outlines.length > 0 && !outlines.some((outline) => outline.id === selectedOutlineId)) return;
    if (outlines.length === 0 && selectedOutlineId) return;
    if (selectedOutlineId) {
      if (manuscriptsQuery.data === undefined) return;
      if (manuscripts.length > 0 && !manuscripts.some((manuscript) => manuscript.id === selectedManuscriptId)) return;
      if (manuscripts.length === 0 && selectedManuscriptId) return;
    } else if (selectedManuscriptId) {
      return;
    }
    if (selectedSceneId && !sceneRows.some((row) => row.id === selectedSceneId)) return;
    onSelectionChange?.({ storyId: selectedStoryId, outlineId: selectedOutlineId, manuscriptId: selectedManuscriptId, sceneId: selectedSceneId });
  }, [manuscripts, manuscriptsQuery.data, onSelectionChange, outlines, outlinesQuery.data, sceneRows, selectedManuscriptId, selectedOutlineId, selectedSceneId, selectedStoryId, stories]);

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
    createManuscript,
    createOutline,
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
    setSelectedOutlineId: selectOutlineId,
    setSelectedSceneId,
    setSelectedSceneIds,
    setSelectedStoryId: selectStoryId,
    stories,
    toggleChapterExpanded,
  };
}
