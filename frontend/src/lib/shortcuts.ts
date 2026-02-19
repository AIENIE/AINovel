export const WORKSPACE_SHORTCUT_ACTIONS = [
  "command_palette",
  "save",
  "focus_mode",
  "toggle_left_panel",
  "toggle_right_panel",
  "next_chapter",
  "prev_chapter",
  "ai_refine",
  "new_scene",
  "search_manuscript",
  "search_replace",
  "export",
  "close_tab",
  "next_tab",
  "undo",
  "redo",
] as const;

export type ShortcutAction = (typeof WORKSPACE_SHORTCUT_ACTIONS)[number];

export type ShortcutMap = Record<ShortcutAction, string>;

const tokenOrder: Record<string, number> = {
  ctrl: 1,
  shift: 2,
  alt: 3,
  key: 4,
};

const keyAliases: Record<string, string> = {
  control: "ctrl",
  cmd: "ctrl",
  meta: "ctrl",
  option: "alt",
  esc: "escape",
};

const eventKeyAliases: Record<string, string> = {
  " ": "space",
  control: "ctrl",
  command: "ctrl",
  cmd: "ctrl",
  meta: "ctrl",
  option: "alt",
  escape: "escape",
};

export const DEFAULT_SHORTCUTS: ShortcutMap = {
  command_palette: "Ctrl+K",
  save: "Ctrl+S",
  focus_mode: "Ctrl+Shift+F",
  toggle_left_panel: "Ctrl+B",
  toggle_right_panel: "Ctrl+Shift+B",
  next_chapter: "Ctrl+]",
  prev_chapter: "Ctrl+[",
  ai_refine: "Ctrl+Shift+R",
  new_scene: "Ctrl+Shift+N",
  search_manuscript: "Ctrl+F",
  search_replace: "Ctrl+H",
  export: "Ctrl+Shift+E",
  close_tab: "Ctrl+W",
  next_tab: "Ctrl+Tab",
  undo: "Ctrl+Z",
  redo: "Ctrl+Shift+Z",
};

function normalizeToken(token: string): string {
  const clean = token.trim().toLowerCase();
  if (!clean) return "";
  if (keyAliases[clean]) return keyAliases[clean];
  return clean;
}

function isModifier(token: string): boolean {
  return token === "ctrl" || token === "shift" || token === "alt";
}

export function normalizeShortcut(shortcut: string): string {
  if (!shortcut) return "";
  const rawTokens = shortcut
    .split("+")
    .map((token) => normalizeToken(token))
    .filter(Boolean);
  if (!rawTokens.length) return "";

  const modifiers = new Set<string>();
  const keys: string[] = [];
  rawTokens.forEach((token) => {
    if (isModifier(token)) modifiers.add(token);
    else keys.push(token);
  });

  const ordered = [...modifiers]
    .sort((a, b) => tokenOrder[a] - tokenOrder[b])
    .concat(keys.length ? keys[keys.length - 1] : []);
  return ordered
    .filter(Boolean)
    .map((token) => (token.length === 1 ? token.toUpperCase() : token[0].toUpperCase() + token.slice(1)))
    .join("+");
}

export function detectShortcutConflicts(entries: Array<{ action: string; shortcut: string }>) {
  const byShortcut = new Map<string, string[]>();
  entries.forEach((entry) => {
    const normalized = normalizeShortcut(entry.shortcut);
    if (!normalized) return;
    const actions = byShortcut.get(normalized) || [];
    actions.push(entry.action);
    byShortcut.set(normalized, actions);
  });
  return [...byShortcut.entries()]
    .filter(([, actions]) => actions.length > 1)
    .map(([shortcut, actions]) => ({ shortcut, actions }));
}

export function matchesShortcut(event: KeyboardEvent, shortcut: string): boolean {
  const normalized = normalizeShortcut(shortcut);
  if (!normalized) return false;
  const tokens = normalized.toLowerCase().split("+");
  const keyToken = tokens[tokens.length - 1];
  const needCtrl = tokens.includes("ctrl");
  const needShift = tokens.includes("shift");
  const needAlt = tokens.includes("alt");
  const hasCtrl = event.ctrlKey || event.metaKey;

  if (needCtrl !== hasCtrl) return false;
  if (needShift !== event.shiftKey) return false;
  if (needAlt !== event.altKey) return false;

  const eventKey = (eventKeyAliases[event.key.toLowerCase()] || event.key.toLowerCase()).trim();
  if (!eventKey) return false;
  return eventKey === keyToken;
}

export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
}
