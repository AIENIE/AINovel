import { Dispatch, SetStateAction, useEffect, useState } from "react";

const MOBILE_BREAKPOINT = 768;
const COMPACT_BREAKPOINT = 1280;

type UseWorkbenchViewportOptions = {
  focusMode: boolean;
  setLeftPanelOpen: Dispatch<SetStateAction<boolean>>;
  setIsSidebarOpen: Dispatch<SetStateAction<boolean>>;
};

export function useWorkbenchViewport({
  focusMode,
  setLeftPanelOpen,
  setIsSidebarOpen,
}: UseWorkbenchViewportOptions) {
  const [isCompact, setIsCompact] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const syncViewport = () => {
      const width = window.innerWidth;
      setIsMobile(width < MOBILE_BREAKPOINT);
      setIsCompact(width < COMPACT_BREAKPOINT);
    };

    syncViewport();
    window.addEventListener("resize", syncViewport);
    return () => window.removeEventListener("resize", syncViewport);
  }, []);

  useEffect(() => {
    if (focusMode) return;
    if (isMobile) {
      setLeftPanelOpen(false);
      setIsSidebarOpen(false);
      return;
    }
    if (isCompact) {
      setIsSidebarOpen(false);
    }
  }, [focusMode, isCompact, isMobile, setIsSidebarOpen, setLeftPanelOpen]);

  return { isCompact, isMobile };
}
