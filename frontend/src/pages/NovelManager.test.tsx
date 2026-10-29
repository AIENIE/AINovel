import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import NovelManager from "./NovelManager";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });
function story() {
  vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story", title: "作品", synopsis: "简介", genre: "", tone: "", status: "draft", updatedAt: "2026-10-29T01:00:00Z" }]);
}
it("shows unavailable statistics when reading fails", async () => {
  story();
  vi.spyOn(api.outlines, "listByStory").mockRejectedValue(new Error("read failed"));
  render(<MemoryRouter><NovelManager /></MemoryRouter>);
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.queryByText("0 字")).toBeNull();
  expect(screen.queryByText("0%")).toBeNull();
});
it("keeps a successfully read empty story distinct from a failed read", async () => {
  story();
  vi.spyOn(api.outlines, "listByStory").mockResolvedValue([]);
  render(<MemoryRouter><NovelManager /></MemoryRouter>);
  expect(await screen.findByText("0 字")).toBeTruthy();
  expect(screen.getByText("0%")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});
