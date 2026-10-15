import { useState } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { CharacterCard } from "@/types";
import StoryManager from "./StoryManager";

function sources() {
  vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-a", title: "甲故事" }, { id: "story-b", title: "乙故事" }] as never);
  vi.spyOn(api.worlds, "list").mockResolvedValue([]);
}
describe("character cards in story discussion", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it("keeps late character responses in their originating story", async () => {
    sources();
    let resolveA!: (value: CharacterCard[]) => void;
    vi.spyOn(api.stories, "listCharacters").mockImplementation(story => story === "story-a" ? new Promise(resolve => { resolveA = resolve; }) : Promise.resolve([{ id: "character-b", name: "乙人物" }]));
    function Harness() { const [story, setStory] = useState("story-a"); return <><button onClick={() => setStory("story-b")}>切换乙故事</button><StoryManager initialStoryId={story} /></>; }
    render(<RouterProvider router={createMemoryRouter([{ path: "*", element: <Harness /> }])} />);
    await waitFor(() => expect(resolveA).toBeDefined());
    fireEvent.click(screen.getByRole("button", { name: "切换乙故事" }));
    expect(await screen.findByText("乙人物")).toBeTruthy();
    await act(async () => resolveA([{ id: "character-a", name: "甲人物" }]));
    expect(screen.queryByText("甲人物")).toBeNull();
    expect(screen.getByText("乙人物")).toBeTruthy();
  });
  it("allows authors to maintain details and relationships using the loaded revision", async () => {
    sources();
    vi.spyOn(api.stories, "listCharacters").mockResolvedValue([{ id: "character-a", name: "许安", synopsis: "简介", details: "原详情", relationships: "旧关系", revision: 7 }]);
    const update = vi.spyOn(api.stories, "updateCharacter").mockResolvedValue({ id: "character-a", name: "许安", revision: 8 });
    render(<RouterProvider router={createMemoryRouter([{ path: "*", element: <StoryManager initialStoryId="story-a" /> }])} />);
    fireEvent.click(await screen.findByRole("button", { name: "编辑角色" }));
    fireEvent.change(screen.getByRole("textbox", { name: "详细设定" }), { target: { value: "作者更新的详情" } });
    fireEvent.change(screen.getByRole("textbox", { name: "人物关系" }), { target: { value: "合作伙伴" } });
    fireEvent.click(screen.getByRole("button", { name: "保存角色" }));
    await waitFor(() => expect(update).toHaveBeenCalledWith("character-a", expect.objectContaining({ details: "作者更新的详情", relationships: "合作伙伴", expectedRevision: 7 })));
  });
});
