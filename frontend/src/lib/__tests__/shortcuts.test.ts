import { describe, expect, it } from "vitest";
import { detectShortcutConflicts, matchesShortcut, normalizeShortcut } from "@/lib/shortcuts";

describe("shortcuts utils", () => {
  it("normalizes shortcut tokens", () => {
    expect(normalizeShortcut("cmd + shift + k")).toBe("Ctrl+Shift+K");
    expect(normalizeShortcut("ctrl + tab")).toBe("Ctrl+Tab");
  });

  it("detects conflicts by normalized shortcut", () => {
    const conflicts = detectShortcutConflicts([
      { action: "save", shortcut: "Ctrl+S" },
      { action: "export", shortcut: "cmd+s" },
      { action: "focus_mode", shortcut: "Ctrl+Shift+F" },
    ]);
    expect(conflicts).toHaveLength(1);
    expect(conflicts[0].shortcut).toBe("Ctrl+S");
    expect(conflicts[0].actions.sort()).toEqual(["export", "save"]);
  });

  it("matches keyboard events", () => {
    const event = {
      key: "k",
      ctrlKey: true,
      metaKey: false,
      shiftKey: false,
      altKey: false,
    } as KeyboardEvent;
    expect(matchesShortcut(event, "Ctrl+K")).toBe(true);
    expect(matchesShortcut(event, "Ctrl+Shift+K")).toBe(false);
  });
});
