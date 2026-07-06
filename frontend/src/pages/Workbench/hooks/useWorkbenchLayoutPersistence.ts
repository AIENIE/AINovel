import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/lib/api-client";

export type WorkbenchSidebarTab = "copilot" | "context" | "version" | "export" | "stats" | "goals" | "plot";

const WORKBENCH_LAYOUT_KEY = "ainovel.workbench.layout.v2";

type UseWorkbenchLayoutPersistenceOptions = {
  selectedStoryId: string;
  selectedManuscriptId: string;
};

export function useWorkbenchLayoutPersistence({
  selectedStoryId,
  selectedManuscriptId,
}: UseWorkbenchLayoutPersistenceOptions) {
  const [leftPanelOpen, setLeftPanelOpen] = useState(true);
  const [leftPanelSize, setLeftPanelSize] = useState(24);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [rightPanelSize, setRightPanelSize] = useState(30);
  const [sidebarTab, setSidebarTab] = useState<WorkbenchSidebarTab>("copilot");
  const [activeLayoutId, setActiveLayoutId] = useState("");
  const layoutSyncTimerRef = useRef<number | null>(null);

  const layoutCacheKey = useMemo(
    () => `${WORKBENCH_LAYOUT_KEY}:${selectedStoryId || "na"}:${selectedManuscriptId || "na"}`,
    [selectedManuscriptId, selectedStoryId],
  );

  const applyLayoutPayload = useCallback((layout: any) => {
    if (!layout || typeof layout !== "object") return;
    const raw = layout.layout && typeof layout.layout === "object" ? layout.layout : layout;
    if (typeof raw.leftPanelOpen === "boolean") setLeftPanelOpen(raw.leftPanelOpen);
    if (typeof raw.leftPanelSize === "number") setLeftPanelSize(raw.leftPanelSize);
    if (typeof raw.rightPanelOpen === "boolean") setIsSidebarOpen(raw.rightPanelOpen);
    if (typeof raw.rightPanelSize === "number") setRightPanelSize(raw.rightPanelSize);
    if (typeof raw.sidebarTab === "string") setSidebarTab(raw.sidebarTab as WorkbenchSidebarTab);
  }, []);

  const buildLayoutPayload = useCallback(
    () => ({
      leftPanelOpen,
      leftPanelSize,
      rightPanelOpen: isSidebarOpen,
      rightPanelSize,
      sidebarTab,
    }),
    [isSidebarOpen, leftPanelOpen, leftPanelSize, rightPanelSize, sidebarTab],
  );

  useEffect(() => {
    if (!layoutCacheKey || !selectedManuscriptId || !selectedStoryId) return;
    let cancelled = false;

    const loadLayout = async () => {
      let localApplied = false;
      try {
        const cached = localStorage.getItem(layoutCacheKey);
        if (cached) {
          applyLayoutPayload(JSON.parse(cached));
          localApplied = true;
        }
      } catch {
        // ignore corrupted cache
      }

      try {
        const layouts = await api.v2.workspace.listLayouts();
        if (cancelled) return;
        const activeLayout = layouts.find((layout: any) => Boolean(layout.isActive));
        if (activeLayout?.id) setActiveLayoutId(String(activeLayout.id));
        if (!localApplied && activeLayout?.layout) {
          applyLayoutPayload(activeLayout.layout);
        }
      } catch {
        // ignore server-side layout fetch failures
      }
    };

    void loadLayout();
    return () => {
      cancelled = true;
    };
  }, [applyLayoutPayload, layoutCacheKey, selectedManuscriptId, selectedStoryId]);

  useEffect(() => {
    if (!selectedStoryId || !selectedManuscriptId) return;
    localStorage.setItem(layoutCacheKey, JSON.stringify(buildLayoutPayload()));
  }, [buildLayoutPayload, layoutCacheKey, selectedManuscriptId, selectedStoryId]);

  useEffect(() => {
    if (!selectedStoryId || !selectedManuscriptId) return;
    if (layoutSyncTimerRef.current) window.clearTimeout(layoutSyncTimerRef.current);
    layoutSyncTimerRef.current = window.setTimeout(() => {
      const payload = buildLayoutPayload();
      const request = activeLayoutId
        ? api.v2.workspace.updateLayout(activeLayoutId, { layout: payload, isActive: true })
        : api.v2.workspace.createLayout({ name: "写作模式", layout: payload, isActive: true });
      void request
        .then((layout: any) => {
          if (layout?.id) setActiveLayoutId(String(layout.id));
        })
        .catch(() => {});
    }, 600);

    return () => {
      if (layoutSyncTimerRef.current) window.clearTimeout(layoutSyncTimerRef.current);
    };
  }, [activeLayoutId, buildLayoutPayload, selectedManuscriptId, selectedStoryId]);

  return {
    activeLayoutId,
    isSidebarOpen,
    leftPanelOpen,
    leftPanelSize,
    rightPanelSize,
    setActiveLayoutId,
    setIsSidebarOpen,
    setLeftPanelOpen,
    setLeftPanelSize,
    setRightPanelSize,
    setSidebarTab,
    sidebarTab,
  };
}
