import { fireEvent, render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import Workbench from "./Workbench";

vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock("./tabs/StoryConception", () => ({ default: () => null }));
vi.mock("./tabs/StoryManager", () => ({ default: () => null }));
vi.mock("./tabs/OutlineWorkbench", () => ({ default: () => null }));
vi.mock("./tabs/MaterialSearchPanel", () => ({ default: () => null }));
vi.mock("./tabs/V2Studio", () => ({ default: () => null }));
vi.mock("./tabs/LorebookPanel", () => ({ default: () => null }));
vi.mock("./tabs/KnowledgeGraphTab", () => ({ default: () => null }));
vi.mock("./tabs/AnalysisDashboard", () => ({ default: () => null }));
vi.mock("./tabs/ManuscriptWriter", () => ({ default: ({ onSelectionChange }: { onSelectionChange: (selection: { sceneId: string }) => void }) =>
  <button onClick={() => onSelectionChange({ sceneId: "scene-2" })}>同步场景</button> }));

function CurrentLocation() {
  const location = useLocation();
  return <output data-testid="location">{location.search}{location.hash}</output>;
}

describe("workbench return location", () => {
  it("keeps Chinese query parameters and fragment when synchronizing scene selection", () => {
    render(<MemoryRouter initialEntries={["/workbench?storyId=story-1&tab=writing&title=%E6%98%8E%E6%97%A5#scene-1"]}><Workbench /><CurrentLocation /></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "同步场景" }));
    expect(screen.getByTestId("location").textContent).toBe("?storyId=story-1&tab=writing&title=%E6%98%8E%E6%97%A5&sceneId=scene-2#scene-1");
  });
});
