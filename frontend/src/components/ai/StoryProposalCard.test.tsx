import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { StoryAssistantProposal } from "@/lib/api/domains/story-assistant";
import { StoryProposalCard } from "./StoryProposalCard";
import { StoryDiscussionWorkspaceContext } from "./story-discussion-context";

function proposal(patch: Partial<StoryAssistantProposal> = {}): StoryAssistantProposal {
  return { id: "proposal-1", turnId: "turn-1", action: "UPDATE", resourceType: "CHARACTER", targetId: "character-1", outlineId: null, parentId: null, status: "READY", revision: 0, resourceRevision: 4,
    before: { name: "许安", synopsis: "旧简介", details: "旧设定", relationships: "朋友" }, candidate: { name: "许安", synopsis: "新简介", details: "新设定", relationships: "竞争者" }, modelCandidate: {}, current: { name: "许安", synopsis: "旧简介" }, rationale: "保留动机", sources: [], sourceTurnIds: ["turn-1"], applicable: true, staleReason: null, undoAllowed: false, createdAt: "2026-10-15", ...patch };
}
function mount(dirty = new Set<string>()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter><StoryDiscussionWorkspaceContext.Provider value={{ dirtyTargets: dirty, setDirty: vi.fn() }}><StoryProposalCard storyId="story-1" proposalId="proposal-1" working={false} onRefine={vi.fn()} /></StoryDiscussionWorkspaceContext.Provider></MemoryRouter></QueryClientProvider>);
}
describe("story material proposal review", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it("saves manual candidate edits before accepting the current resource revision", async () => {
    let current = proposal();
    vi.spyOn(api.storyAssistant, "proposal").mockImplementation(async () => current);
    const edit = vi.spyOn(api.storyAssistant, "editProposal").mockImplementation(async (_story, _proposal, _revision, candidate) => { current = { ...current, revision: 1, candidate }; return current; });
    const decide = vi.spyOn(api.storyAssistant, "decide").mockImplementation(async () => { current = { ...current, status: "ACCEPTED", applicable: false, undoAllowed: true, revision: 2 }; return { proposal: current, resourceRevision: 5 }; });
    mount();
    fireEvent.change(await screen.findByLabelText("详细设定"), { target: { value: "作者亲自修改的设定" } });
    expect(screen.getByRole("button", { name: "采纳并保存" }).hasAttribute("disabled")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "保存候选" }));
    await waitFor(() => expect(edit).toHaveBeenCalledWith("story-1", "proposal-1", 0, expect.objectContaining({ details: "作者亲自修改的设定" }), expect.any(String)));
    await waitFor(() => expect(screen.getByRole("button", { name: "采纳并保存" }).hasAttribute("disabled")).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: "采纳并保存" }));
    await waitFor(() => expect(decide).toHaveBeenCalledWith("story-1", "proposal-1", "accept", 1, 4, expect.any(String)));
    expect(await screen.findByRole("button", { name: "撤销本次修改" })).toBeTruthy();
    expect(screen.queryByText("资料或候选已发生变化，原提案保留供比较，不会覆盖现在的内容。")).toBeNull();
  });
  it("blocks acceptance while the character editor has unsaved changes", async () => {
    vi.spyOn(api.storyAssistant, "proposal").mockResolvedValue(proposal());
    const decide = vi.spyOn(api.storyAssistant, "decide");
    mount(new Set(["CHARACTER:character-1"]));
    const accept = await screen.findByRole("button", { name: "采纳并保存" });
    expect(accept.hasAttribute("disabled")).toBe(true);
    fireEvent.click(accept);
    expect(decide).not.toHaveBeenCalled();
    expect(screen.getByText(/目标资料有未保存编辑/)).toBeTruthy();
  });
  it("does not offer undo for an accepted creation or link to an uncreated target", async () => {
    vi.spyOn(api.storyAssistant, "proposal").mockResolvedValue(proposal({ action: "CREATE", before: null }));
    const view = mount();
    await screen.findByText("新增项不会删除或重排现有内容。");
    expect(screen.queryByRole("link", { name: "查看目标资料" })).toBeNull();
    view.unmount();
    vi.mocked(api.storyAssistant.proposal).mockResolvedValue(proposal({ action: "CREATE", before: null, status: "ACCEPTED", applicable: false }));
    mount();
    expect(await screen.findByRole("link", { name: "查看目标资料" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "撤销本次修改" })).toBeNull();
  });
  it("reconciles an uncertain acceptance with the exact original request", async () => {
    let current = proposal();
    vi.spyOn(api.storyAssistant, "proposal").mockImplementation(async () => current);
    const decide = vi.spyOn(api.storyAssistant, "decide").mockRejectedValueOnce(new Error("network lost")).mockImplementationOnce(async () => { current = { ...current, status: "ACCEPTED", applicable: false, undoAllowed: true, revision: 1 }; return { proposal: current, resourceRevision: 5 }; });
    mount();
    fireEvent.click(await screen.findByRole("button", { name: "采纳并保存" }));
    fireEvent.click(await screen.findByRole("button", { name: "核对本次保存" }));
    await waitFor(() => expect(decide).toHaveBeenCalledTimes(2));
    expect(decide.mock.calls[1]).toEqual(decide.mock.calls[0]);
  });
});
