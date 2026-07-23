import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import OutlineWorkbench from "./OutlineWorkbench";

describe("OutlineWorkbench", () => {
  afterEach(() => vi.restoreAllMocks());

  it("opens the no-foreshadow option for a newly added scene without crashing", async () => {
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as any);
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([{
      id: "outline-1",
      storyId: "story-1",
      title: "大纲",
      chapters: [{ id: "chapter-1", title: "第一章", summary: "", scenes: [] }],
      planning: { twistOptions: [], foreshadowPlans: [] },
    }] as any);

    render(<OutlineWorkbench initialStoryId="story-1" />);

    await waitFor(() => expect(screen.getByText("第一章")).toBeTruthy());
    fireEvent.click(screen.getByText("第一章"));
    fireEvent.click(screen.getByRole("button", { name: /添加场景/ }));
    fireEvent.click(screen.getByText("新场景"));
    fireEvent.click(screen.getAllByRole("combobox")[1]);

    expect((await screen.findAllByText("无")).length).toBeGreaterThan(0);
  });
});
