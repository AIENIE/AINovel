import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import Settings, { normalizeSettingsTabParam } from "@/pages/Settings/Settings";

vi.mock("@/pages/Settings/tabs/WorkspacePrompts", () => ({ default: () => null }));
vi.mock("@/pages/Settings/tabs/WorldPrompts", () => ({ default: () => null }));
vi.mock("@/pages/Settings/tabs/StyleProfiles", () => ({ default: () => null }));
vi.mock("@/pages/Settings/tabs/ModelPreferences", () => ({ default: () => null }));
vi.mock("@/pages/Settings/tabs/WorkspaceExperience", () => ({ default: () => null }));

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

  it("keeps the selected tab synchronized with browser navigation and clicks", async () => {
    const router = createMemoryRouter(
      [{ path: "/settings", element: React.createElement(Settings) }],
      { initialEntries: ["/settings?tab=models"] },
    );
    render(React.createElement(RouterProvider, { router }));

    expect(screen.getByRole("tab", { name: /模型偏好/ }).getAttribute("aria-selected")).toBe("true");

    await router.navigate("/settings?tab=world");
    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /世界观提示词/ }).getAttribute("aria-selected")).toBe("true");
    });

    const experienceTab = screen.getByRole("tab", { name: /工作台体验/ });
    experienceTab.focus();
    fireEvent.keyDown(experienceTab, { key: "Enter", code: "Enter" });
    await waitFor(() => expect(router.state.location.search).toBe("?tab=experience"));
  });
});
