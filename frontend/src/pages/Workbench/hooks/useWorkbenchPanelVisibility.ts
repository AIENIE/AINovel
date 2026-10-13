import { useEffect, useRef } from "react";
import type { ImperativePanelHandle } from "react-resizable-panels";

export function useWorkbenchPanelVisibility(visible: boolean, pagedLayout: boolean) {
  const panelRef = useRef<ImperativePanelHandle | null>(null);

  useEffect(() => {
    // Paged layouts unmount these panels, so synchronize again when desktop returns.
    if (pagedLayout) return;
    if (visible) panelRef.current?.expand();
    else panelRef.current?.collapse();
  }, [visible, pagedLayout]);

  return panelRef;
}
