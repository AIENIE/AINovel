const VALID_TABS = new Set(["workspace", "world", "style", "models", "experience"]);

export function normalizeSettingsTabParam(raw: string | null): string {
  const value = String(raw || "").trim().toLowerCase();
  if (!value) return "workspace";
  if (value === "model") return "models";
  return VALID_TABS.has(value) ? value : "workspace";
}
