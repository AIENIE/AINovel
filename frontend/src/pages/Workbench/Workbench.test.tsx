import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, it, expect, vi } from "vitest";
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

afterEach(cleanup);

describe("workbench return location", () => {
  it("keeps Chinese query parameters and fragment when synchronizing scene selection", () => {
    render(<MemoryRouter initialEntries={["/workbench?storyId=story-1&tab=writing&title=%E6%98%8E%E6%97%A5#scene-1"]}><Workbench /><CurrentLocation /></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "同步场景" }));
    expect(screen.getByTestId("location").textContent).toBe("?storyId=story-1&tab=writing&title=%E6%98%8E%E6%97%A5&sceneId=scene-2#scene-1");
  });
  it("keeps every grouped navigation item reachable and preserves the current resource when changing groups", () => {
    render(<MemoryRouter initialEntries={["/workbench?storyId=story-1&outlineId=outline-1&manuscriptId=manuscript-1&sceneId=scene-1&tab=writing#scene-1"]}><Workbench /><CurrentLocation /></MemoryRouter>);
    const main = screen.getByRole("tablist", { name: "workbench.mainFlow" });
    const tools = screen.getByRole("tablist", { name: "workbench.tools" });
    const advanced = screen.getByRole("tablist", { name: "workbench.advanced" });
    expect(within(main).getAllByRole("tab")).toHaveLength(4);
    expect(within(tools).getAllByRole("tab")).toHaveLength(4);
    expect(within(advanced).getAllByRole("tab")).toHaveLength(1);
    for (const [group, name, tab] of [[tools,"workbench.qualityAnalysis","analysis"],[advanced,"workbench.advancedWorkbench","v2"],[main,"workbench.writing","writing"]] as const) {
      fireEvent.mouseDown(within(group).getByRole("tab", { name }), { button: 0, ctrlKey: false });
      expect(screen.getByTestId("location").textContent).toBe(`?storyId=story-1&outlineId=outline-1&manuscriptId=manuscript-1&sceneId=scene-1&tab=${tab}#scene-1`);
    }
  });
});
