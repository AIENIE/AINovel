import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import OutlineWorkbench from "./OutlineWorkbench";

describe("OutlineWorkbench", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("does not create data while loading and creates a named outline only after confirmation", async () => {
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as any);
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as any);
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([] as any);
    const createSpy = vi.spyOn(api.outlines, "create").mockResolvedValue({
      id: "outline-1",
      storyId: "story-1",
      title: "支线大纲",
      chapters: [],
    } as any);

    render(<OutlineWorkbench initialStoryId="story-1" />);

    await waitFor(() => expect(screen.getByText("暂无大纲")).toBeTruthy());
    expect(createSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: /新大纲/ }));
    fireEvent.change(screen.getByPlaceholderText("大纲名称"), { target: { value: "支线大纲" } });
    fireEvent.click(screen.getByRole("button", { name: "创建" }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith("story-1", { title: "支线大纲", planning: undefined }));
    expect(await screen.findByText("支线大纲")).toBeTruthy();
  });

  it("saves outline-level changes from the structure overview", async () => {
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as any);
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as any);
    const outline = {
      id: "outline-1",
      storyId: "story-1",
      title: "大纲",
      chapters: [],
      planning: { twistOptions: [], foreshadowPlans: [] },
    };
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([outline] as any);
    const saveSpy = vi.spyOn(api.outlines, "save").mockResolvedValue(outline as any);

    render(<OutlineWorkbench initialStoryId="story-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "保存大纲" }));

    await waitFor(() => expect(saveSpy).toHaveBeenCalledWith("outline-1", expect.objectContaining({ id: "outline-1" })));
    expect((await screen.findByRole("status")).textContent).toBe("已保存");
  });

  it("opens the no-foreshadow option for a newly added scene without crashing", async () => {
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as any);
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
