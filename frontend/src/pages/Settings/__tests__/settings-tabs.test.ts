import { describe, expect, it } from "vitest";
import { normalizeSettingsTabParam } from "@/pages/Settings/Settings";

describe("settings tab query normalization", () => {
  it("maps legacy model tab to models", () => {
    expect(normalizeSettingsTabParam("model")).toBe("models");
    expect(normalizeSettingsTabParam(" MODEL ")).toBe("models");
  });

  it("accepts known tabs", () => {
    expect(normalizeSettingsTabParam("workspace")).toBe("workspace");
    expect(normalizeSettingsTabParam("world")).toBe("world");
    expect(normalizeSettingsTabParam("style")).toBe("style");
    expect(normalizeSettingsTabParam("models")).toBe("models");
    expect(normalizeSettingsTabParam("experience")).toBe("experience");
  });

  it("falls back to workspace for blank or invalid values", () => {
    expect(normalizeSettingsTabParam(null)).toBe("workspace");
    expect(normalizeSettingsTabParam("")).toBe("workspace");
    expect(normalizeSettingsTabParam("unknown")).toBe("workspace");
  });
});
