import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import OutlineWorkbench from "./OutlineWorkbench";

describe("OutlineWorkbench", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("does not create data while loading and creates a named outline only after confirmation", async () => {
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as never);
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as never);
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([] as never);
    const createSpy = vi.spyOn(api.outlines, "create").mockResolvedValue({
      id: "outline-1",
      storyId: "story-1",
      title: "支线大纲",
      chapters: [],
    } as never);

    render(<RouterProvider router={createMemoryRouter([{ path: "*", element: <OutlineWorkbench initialStoryId="story-1" /> }])} />);

    await waitFor(() => expect(screen.getByText("暂无大纲")).toBeTruthy());
    expect(createSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: /新大纲/ }));
    fireEvent.change(screen.getByPlaceholderText("大纲名称"), { target: { value: "支线大纲" } });
    fireEvent.click(screen.getByRole("button", { name: "创建" }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith("story-1", { title: "支线大纲", planning: undefined }));
    expect(await screen.findByText("支线大纲")).toBeTruthy();
  });

  it("saves outline-level changes from the structure overview", async () => {
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as never);
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as never);
    const outline = {
      id: "outline-1",
      storyId: "story-1",
      title: "大纲",
      chapters: [],
      planning: { twistOptions: [], foreshadowPlans: [] },
    };
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([outline] as never);
    const saveSpy = vi.spyOn(api.outlines, "save").mockResolvedValue(outline as never);

    render(<RouterProvider router={createMemoryRouter([{ path: "*", element: <OutlineWorkbench initialStoryId="story-1" /> }])} />);

    fireEvent.click(await screen.findByRole("button", { name: "保存大纲" }));

    await waitFor(() => expect(saveSpy).toHaveBeenCalledWith("outline-1", expect.objectContaining({ id: "outline-1" })));
    expect((await screen.findByRole("status")).textContent).toBe("已保存");
  });

  it("opens the no-foreshadow option for a newly added scene without crashing", async () => {
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as never);
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as never);
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([{
      id: "outline-1",
      storyId: "story-1",
      title: "大纲",
      chapters: [{ id: "chapter-1", title: "第一章", summary: "", scenes: [] }],
      planning: { twistOptions: [], foreshadowPlans: [] },
    }] as never);

    render(<RouterProvider router={createMemoryRouter([{ path: "*", element: <OutlineWorkbench initialStoryId="story-1" /> }])} />);

    await waitFor(() => expect(screen.getByText("第一章")).toBeTruthy());
    fireEvent.click(screen.getByText("第一章"));
    fireEvent.change(screen.getByDisplayValue("第一章"), { target: { value: "尚未保存的章节" } });
    const fields = screen.getAllByRole("textbox");
    fireEvent.change(fields[1], { target: { value: "尚未保存的摘要" } });
    fireEvent.click(screen.getByRole("button", { name: /添加场景/ }));
    fireEvent.click(screen.getByText("尚未保存的章节"));
    expect(screen.getByDisplayValue("尚未保存的摘要")).toBeTruthy();
    fireEvent.click(screen.getByText("新场景"));
    fireEvent.click(screen.getAllByRole("combobox")[1]);

    expect((await screen.findAllByText("无")).length).toBeGreaterThan(0);
  });
  it("blocks browser back, retains failed saves and only leaves after a successful save", async () => {
    vi.spyOn(api.worlds, "list").mockResolvedValue([] as never);
    vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as never);
    const outline = { id: "outline-1", storyId: "story-1", title: "大纲", chapters: [
      { id: "chapter-1", title: "第一章", summary: "原摘要", scenes: [] },
    ] };
    vi.spyOn(api.outlines, "listByStory").mockResolvedValue([outline] as never);
    const save = vi.spyOn(api.outlines, "save").mockRejectedValueOnce(new Error("offline"))
      .mockImplementationOnce(async (_id, value) => structuredClone(value) as never);
    const router = createMemoryRouter([
      { path: "/before", element: <p>离开后的页面</p> },
      { path: "/outline", element: <OutlineWorkbench initialStoryId="story-1" /> },
    ], { initialEntries: ["/before", "/outline"], initialIndex: 1 });
    render(<RouterProvider router={router} />);
    fireEvent.click(await screen.findByText("第一章"));
    fireEvent.change(screen.getByDisplayValue("第一章"), { target: { value: "保留修改" } });
    await act(async () => { await router.navigate(-1); });
    fireEvent.click(await screen.findByRole("button", { name: "取消" }));
    expect(router.state.location.pathname).toBe("/outline");
    expect(screen.getByDisplayValue("保留修改")).toBeTruthy();
    await act(async () => { await router.navigate(-1); });
    fireEvent.click(await screen.findByRole("button", { name: "保存并继续" }));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
    expect(router.state.location.pathname).toBe("/outline");
    expect(screen.getByDisplayValue("保留修改")).toBeTruthy();
    await waitFor(() => expect(screen.getByRole("button", { name: "保存并继续" }).hasAttribute("disabled")).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: "保存并继续" }));
    expect(await screen.findByText("离开后的页面")).toBeTruthy();
  });

});
