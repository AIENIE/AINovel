import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { StoryAssistantProposal, StoryAssistantSession } from "@/lib/api/domains/story-assistant";
import { StoryDiscussionPanel } from "./StoryDiscussionPanel";
import { StoryDiscussionWorkspaceContext } from "./story-discussion-context";

vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: vi.fn(async accepted => await accepted), retryTrackedAiOperation: vi.fn(async () => {}) }));
function setup(dirty = new Set<string>(), hasSession = true, withProposal = false) {
  vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story-1", title: "故事" }] as never);
  vi.spyOn(api.outlines, "listByStory").mockResolvedValue([]);
  vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue(hasSession ? [{ id: "topic-1", storyId: "story-1", title: "人物讨论", createdAt: "2026-10-15", updatedAt: "2026-10-15" }, { id: "topic-2", storyId: "story-1", title: "另一个方向", createdAt: "2026-10-15", updatedAt: "2026-10-15" }] : []);
  const first: StoryAssistantSession = { id: hasSession ? "topic-1" : null, storyId: "story-1", title: hasSession ? "人物讨论" : null, turns: hasSession ? [{ id: "turn-1", operationId: null, status: "SUCCEEDED", resultKind: "ANSWER", instruction: "聊聊许安", answer: "可以安排为竞争者", intent: "DISCUSS", suggestions: [{ id: "suggestion-1", title: "整理角色", reason: "动机已明确", sourceTurnIds: ["turn-1"] }], proposalIds: withProposal ? ["proposal-1"] : [], sources: [], warnings: [], usage: { inputTokens: 1, outputTokens: 2, cost: 1, modelCalls: 1 }, createdAt: "2026-10-15" }] : [], hasMore: false, nextBefore: null };
  vi.spyOn(api.storyAssistant, "session").mockImplementation(async (_story, topic) => topic === "topic-2" ? { ...first, id: "topic-2", title: "另一个方向", turns: [] } : first);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(<QueryClientProvider client={client}><MemoryRouter><StoryDiscussionWorkspaceContext.Provider value={{ dirtyTargets: dirty, setDirty: vi.fn(), proposalDrafts: new Map() }}><StoryDiscussionPanel storyId="story-1" outlineId="" visible onClose={vi.fn()} onSelectionChange={vi.fn()} /></StoryDiscussionWorkspaceContext.Provider></MemoryRouter></QueryClientProvider>);
  return view;
}
describe("story discussion entry", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); sessionStorage.clear(); });
  it("shows the explicit new-topic action without any manuscript", async () => {
    const create = vi.spyOn(api.storyAssistant, "createSession").mockResolvedValue({ id: "new-topic", storyId: "story-1", title: "新讨论", createdAt: "2026-10-15", updatedAt: "2026-10-15" });
    setup(new Set(), false);
    await screen.findByText("为这部故事创建第一个讨论话题。");
    fireEvent.click(screen.getAllByRole("button", { name: "新话题" })[1]);
    await waitFor(() => expect(create).toHaveBeenCalledWith("story-1", expect.stringContaining("新讨论"), expect.any(String)));
  });
  it("does not prepare suggestions automatically and blocks preparation while editors are dirty", async () => {
    const submit = vi.spyOn(api.storyAssistant, "submit").mockResolvedValue({ turnId: "turn-2", operationId: "operation-1" });
    setup(new Set(["OUTLINE:outline-1"]));
    const prepare = await screen.findByRole("button", { name: "整理为资料提案" });
    expect(submit).not.toHaveBeenCalled();
    expect(prepare.hasAttribute("disabled")).toBe(true);
    fireEvent.click(prepare);
    expect(submit).not.toHaveBeenCalled();
    expect(screen.getByText(/当前有未保存编辑/)).toBeTruthy();
  });

  it("retains unsaved candidate edits after collapse and topic changes while preserving version conflicts", async () => {
    const initial: StoryAssistantProposal = { id: "proposal-1", turnId: "turn-1", action: "UPDATE", resourceType: "CHARACTER", targetId: "character-1", outlineId: null, parentId: null, status: "READY", revision: 0, resourceRevision: 4, before: { name: "许安", details: "旧设定" }, candidate: { name: "许安", details: "AI候选" }, modelCandidate: {}, current: { name: "许安", details: "旧设定" }, rationale: "", sources: [], sourceTurnIds: ["turn-1"], applicable: true, staleReason: null, undoAllowed: false, createdAt: "2026-10-15" };
    const read = vi.spyOn(api.storyAssistant, "proposal").mockResolvedValue(initial);
    const edit = vi.spyOn(api.storyAssistant, "editProposal");
    setup(new Set(), true, true);
    fireEvent.change(await screen.findByLabelText("详细设定"), { target: { value: "作者尚未保存的设定" } });
    fireEvent.click(screen.getByRole("button", { name: "查看修改提案" }));
    expect(screen.queryByLabelText("详细设定")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "查看修改提案" }));
    expect(await screen.findByDisplayValue("作者尚未保存的设定")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("讨论话题"), { target: { value: "topic-2" } });
    await waitFor(() => expect(screen.queryByLabelText("详细设定")).toBeNull());
    read.mockResolvedValue({ ...initial, revision: 1, candidate: { name: "许安", details: "另处更新的候选" } });
    fireEvent.change(screen.getByLabelText("讨论话题"), { target: { value: "topic-1" } });
    expect(await screen.findByDisplayValue("作者尚未保存的设定")).toBeTruthy();
    await screen.findByText("资料或候选已发生变化，原提案保留供比较，不会覆盖现在的内容。");
    expect(screen.getByRole("button", { name: "采纳并保存" }).hasAttribute("disabled")).toBe(true);
    expect(edit).not.toHaveBeenCalled();
  });
});
