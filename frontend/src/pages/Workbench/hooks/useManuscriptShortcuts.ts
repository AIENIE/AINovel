import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { DEFAULT_SHORTCUTS, ShortcutAction, ShortcutMap, isEditableTarget, matchesShortcut } from "@/lib/shortcuts";

const EDITABLE_SAFE_SHORTCUTS: ShortcutAction[] = ["save", "focus_mode", "command_palette"];

type UseManuscriptShortcutsOptions = {
  focusMode: boolean;
  onAction: (action: ShortcutAction) => void;
  onExitFocusMode: () => void;
};

export function useManuscriptShortcuts({
  focusMode,
  onAction,
  onExitFocusMode,
}: UseManuscriptShortcutsOptions) {
  const [shortcuts, setShortcuts] = useState<ShortcutMap>(DEFAULT_SHORTCUTS);

  const loadShortcuts = useCallback(async () => {
    try {
      const list = await api.v2.workspace.listShortcuts();
      const merged: ShortcutMap = { ...DEFAULT_SHORTCUTS };
      list.forEach((item: any) => {
        const action = String(item.action || "") as ShortcutAction;
        const shortcut = String(item.shortcut || "");
        if (action && shortcut && action in merged) {
          merged[action] = shortcut;
        }
      });
      setShortcuts(merged);
    } catch {
      setShortcuts(DEFAULT_SHORTCUTS);
    }
  }, []);

  useEffect(() => {
    void loadShortcuts();
  }, [loadShortcuts]);

  useEffect(() => {
    const handle = () => {
      void loadShortcuts();
    };
    window.addEventListener("ainovel-shortcuts-updated", handle as EventListener);
    return () => window.removeEventListener("ainovel-shortcuts-updated", handle as EventListener);
  }, [loadShortcuts]);

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape" && focusMode) {
        event.preventDefault();
        onExitFocusMode();
        return;
      }

      const matched = (Object.keys(shortcuts) as ShortcutAction[]).find((action) => matchesShortcut(event, shortcuts[action]));
      if (!matched || matched === "undo" || matched === "redo") return;

      const inProseMirror = event.target instanceof HTMLElement && Boolean(event.target.closest(".ProseMirror"));
      if (isEditableTarget(event.target) && !inProseMirror && !EDITABLE_SAFE_SHORTCUTS.includes(matched)) return;

      event.preventDefault();
      onAction(matched);
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [focusMode, onAction, onExitFocusMode, shortcuts]);

  return { shortcuts, reloadShortcuts: loadShortcuts };
}
