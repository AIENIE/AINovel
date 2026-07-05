import { useCallback, useEffect, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { ShortcutAction } from "@/lib/shortcuts";
import { useManuscriptShortcuts } from "./useManuscriptShortcuts";
import type { WorkbenchSidebarTab } from "./useWorkbenchLayoutPersistence";
import { useWorkbenchViewport } from "./useWorkbenchViewport";

type SceneRow = {
  id: string;
};

type UseManuscriptWorkspaceShellOptions = {
  closeSceneTab: (sceneId: string) => void;
  createSceneInCurrentChapter: () => Promise<void> | void;
  handleManualSave: () => Promise<void> | void;
  isSidebarOpen: boolean;
  leftPanelOpen: boolean;
  openSceneIds: string[];
  selectedSceneId: string;
  sceneRows: SceneRow[];
  setIsSidebarOpen: Dispatch<SetStateAction<boolean>>;
  setLeftPanelOpen: Dispatch<SetStateAction<boolean>>;
  setOpenSceneIds: Dispatch<SetStateAction<string[]>>;
  setSelectedSceneId: Dispatch<SetStateAction<string>>;
  setSelectedSceneIds: Dispatch<SetStateAction<string[]>>;
  setSidebarTab: (tab: WorkbenchSidebarTab) => void;
};

export function useManuscriptWorkspaceShell({
  closeSceneTab,
  createSceneInCurrentChapter,
  handleManualSave,
  isSidebarOpen,
  leftPanelOpen,
  openSceneIds,
  selectedSceneId,
  sceneRows,
  setIsSidebarOpen,
  setLeftPanelOpen,
  setOpenSceneIds,
  setSelectedSceneId,
  setSelectedSceneIds,
  setSidebarTab,
}: UseManuscriptWorkspaceShellOptions) {
  const [commandQuery, setCommandQuery] = useState("");
  const [focusMode, setFocusMode] = useState(false);
  const [isCommandOpen, setIsCommandOpen] = useState(false);
  const [mobilePane, setMobilePane] = useState<"outline" | "editor" | "sidebar">("editor");
  const [selectedWordCount, setSelectedWordCount] = useState(0);
  const focusRestoreRef = useRef<{ leftOpen: boolean; rightOpen: boolean } | null>(null);

  const jumpScene = useCallback((offset: number) => {
    if (!sceneRows.length) return;
    const currentIndex = sceneRows.findIndex((row) => row.id === selectedSceneId);
    const nextIndex = currentIndex < 0 ? 0 : Math.max(0, Math.min(sceneRows.length - 1, currentIndex + offset));
    const nextSceneId = sceneRows[nextIndex]?.id;
    if (!nextSceneId) return;
    setSelectedSceneId(nextSceneId);
    setSelectedSceneIds([nextSceneId]);
    setOpenSceneIds((prev) => (prev.includes(nextSceneId) ? prev : [...prev, nextSceneId]));
  }, [sceneRows, selectedSceneId, setOpenSceneIds, setSelectedSceneId, setSelectedSceneIds]);

  const closeCurrentTab = useCallback(() => {
    if (!selectedSceneId) return;
    closeSceneTab(selectedSceneId);
  }, [closeSceneTab, selectedSceneId]);

  const focusNextTab = useCallback(() => {
    if (!openSceneIds.length) return;
    const currentIndex = openSceneIds.indexOf(selectedSceneId);
    const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % openSceneIds.length;
    setSelectedSceneId(openSceneIds[nextIndex]);
  }, [openSceneIds, selectedSceneId, setSelectedSceneId]);

  const enterFocusMode = useCallback(() => {
    if (focusMode) return;
    focusRestoreRef.current = { leftOpen: leftPanelOpen, rightOpen: isSidebarOpen };
    setLeftPanelOpen(false);
    setIsSidebarOpen(false);
    setFocusMode(true);
  }, [focusMode, isSidebarOpen, leftPanelOpen, setIsSidebarOpen, setLeftPanelOpen]);

  const exitFocusMode = useCallback(() => {
    if (!focusMode) return;
    const restore = focusRestoreRef.current || { leftOpen: true, rightOpen: true };
    setFocusMode(false);
    window.setTimeout(() => {
      setLeftPanelOpen(Boolean(restore.leftOpen));
      window.setTimeout(() => {
        setIsSidebarOpen(Boolean(restore.rightOpen));
      }, 80);
    }, 0);
  }, [focusMode, setIsSidebarOpen, setLeftPanelOpen]);

  const toggleFocusMode = useCallback(() => {
    if (focusMode) {
      exitFocusMode();
      return;
    }
    enterFocusMode();
  }, [enterFocusMode, exitFocusMode, focusMode]);

  const handleShortcutAction = useCallback((action: ShortcutAction) => {
    if (action === "command_palette") setIsCommandOpen(true);
    if (action === "save") void handleManualSave();
    if (action === "focus_mode") toggleFocusMode();
    if (action === "toggle_left_panel") setLeftPanelOpen((prev) => !prev);
    if (action === "toggle_right_panel") setIsSidebarOpen((prev) => !prev);
    if (action === "next_chapter") jumpScene(1);
    if (action === "prev_chapter") jumpScene(-1);
    if (action === "ai_refine") {
      setIsSidebarOpen(true);
      setSidebarTab("copilot");
    }
    if (action === "new_scene") void createSceneInCurrentChapter();
    if (action === "search_manuscript" || action === "search_replace") setIsCommandOpen(true);
    if (action === "export") {
      setIsSidebarOpen(true);
      setSidebarTab("export");
    }
    if (action === "close_tab") closeCurrentTab();
    if (action === "next_tab") focusNextTab();
  }, [
    closeCurrentTab,
    createSceneInCurrentChapter,
    focusNextTab,
    handleManualSave,
    jumpScene,
    setIsSidebarOpen,
    setLeftPanelOpen,
    setSidebarTab,
    toggleFocusMode,
  ]);

  const { shortcuts } = useManuscriptShortcuts({
    focusMode,
    onAction: handleShortcutAction,
    onExitFocusMode: exitFocusMode,
  });

  const { isCompact, isMobile } = useWorkbenchViewport({
    focusMode,
    setLeftPanelOpen,
    setIsSidebarOpen,
  });

  useEffect(() => {
    const selectionHandler = () => setSelectedWordCount((window.getSelection()?.toString() || "").replace(/\s+/g, "").trim().length);
    document.addEventListener("selectionchange", selectionHandler);
    return () => document.removeEventListener("selectionchange", selectionHandler);
  }, []);

  return {
    commandQuery,
    focusMode,
    isCommandOpen,
    isCompact,
    isMobile,
    jumpScene,
    mobilePane,
    selectedWordCount,
    setCommandQuery,
    setIsCommandOpen,
    setMobilePane,
    shortcuts,
    toggleFocusMode,
  };
}
