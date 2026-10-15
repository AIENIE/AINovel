import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { api } from "@/lib/api-client";
import type { StoryAssistantProposal, StoryAssistantSession, StoryAssistantTurn } from "@/lib/api/domains/story-assistant";
import { useStoryDiscussion } from "./useStoryDiscussion";
import { STORY_ASSISTANT_UPDATED } from "@/lib/story-assistant-events";

vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: vi.fn(async accepted => await accepted), retryTrackedAiOperation: vi.fn(async () => {}) }));
const turn = (id: string, instruction: string): StoryAssistantTurn => ({ id, operationId: null, status: "SUCCEEDED", resultKind: "ANSWER", instruction, answer: `答复 ${instruction}`, intent: "DISCUSS", suggestions: [], proposalIds: [], sources: [], warnings: [], usage: { inputTokens: 1, outputTokens: 2, cost: 1, modelCalls: 1 }, createdAt: `2026-10-15T00:00:0${id.slice(-1)}Z` });
const session = (storyId: string, id: string, turns: StoryAssistantTurn[] = []): StoryAssistantSession => ({ storyId, id, title: id, turns, hasMore: false, nextBefore: null });
function wrapper() { const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>; }

describe("story discussion sessions", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); sessionStorage.clear(); });
  it("restores the selected topic after remount without requiring a manuscript", async () => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    const read = vi.spyOn(api.storyAssistant, "session").mockResolvedValue(session("story-a", "topic-a"));
    const first = renderHook(() => useStoryDiscussion("story-a", ""), { wrapper: wrapper() });
    await waitFor(() => expect(first.result.current.session.data?.id).toBe("topic-a"));
    expect(read).toHaveBeenCalledWith("story-a", "topic-a", undefined, expect.any(AbortSignal));
    first.unmount();
    const second = renderHook(() => useStoryDiscussion("story-a", ""), { wrapper: wrapper() });
    await waitFor(() => expect(second.result.current.selectedSessionId).toBe("topic-a"));
  });

  it("does not clear the new story draft or mix in a late response from the old story", async () => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    sessionStorage.setItem("ainovel.story-discussion:story-b", "topic-b");
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    vi.spyOn(api.storyAssistant, "session").mockImplementation(async story => story === "story-a" ? session(story, "topic-a", [turn("turn-1", "A的故事")]) : session(story, "topic-b", [turn("turn-2", "B的故事")]));
    let resolve!: (value: { turnId: string; operationId: string }) => void;
    vi.spyOn(api.storyAssistant, "submit").mockImplementation(() => new Promise(done => { resolve = done; }));
    const { result, rerender } = renderHook(({ story }) => useStoryDiscussion(story, ""), { initialProps: { story: "story-a" }, wrapper: wrapper() });
    await waitFor(() => expect(result.current.session.data?.id).toBe("topic-a"));
    act(() => result.current.setInput("继续讨论A"));
    let sending!: Promise<unknown>;
    act(() => { sending = result.current.submit(); });
    rerender({ story: "story-b" });
    await waitFor(() => expect(result.current.session.data?.id).toBe("topic-b"));
    act(() => result.current.setInput("保留B的输入"));
    await act(async () => { resolve({ turnId: "turn-3", operationId: "operation-1" }); await sending; });
    expect(result.current.input).toBe("保留B的输入");
    expect(result.current.turns.map(item => item.instruction)).toEqual(["B的故事"]);
    rerender({ story: "story-a" });
    await waitFor(() => expect(result.current.session.data?.id).toBe("topic-a"));
    expect(result.current.input).toBe("");
  });

  it("only submits proposals on explicit action and freezes preceding discussion sources", async () => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    const turns = [turn("turn-1", "动机"), turn("turn-2", "关系"), turn("turn-3", "另一个方向")];
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    vi.spyOn(api.storyAssistant, "session").mockResolvedValue(session("story-a", "topic-a", turns));
    const submit = vi.spyOn(api.storyAssistant, "submit").mockResolvedValue({ turnId: "turn-4", operationId: "operation-1" });
    const { result } = renderHook(() => useStoryDiscussion("story-a", "outline-a"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.turns.length).toBe(3));
    expect(submit).not.toHaveBeenCalled();
    await act(async () => { await result.current.propose(turns[1]); });
    expect(submit).toHaveBeenCalledWith("story-a", expect.objectContaining({ intent: "PROPOSE", sessionId: "topic-a", outlineId: "outline-a", sourceTurnIds: ["turn-1", "turn-2"] }), expect.any(String));
  });

  it("reuses the original submission key after an uncertain response", async () => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    vi.spyOn(api.storyAssistant, "session").mockResolvedValue(session("story-a", "topic-a"));
    const submit = vi.spyOn(api.storyAssistant, "submit").mockRejectedValueOnce(new Error("network lost")).mockResolvedValueOnce({ turnId: "turn-1", operationId: "operation-1" });
    const { result } = renderHook(() => useStoryDiscussion("story-a", ""), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.session.data?.id).toBe("topic-a"));
    act(() => result.current.setInput("保留指令"));
    await act(async () => { await result.current.submit(); });
    expect(result.current.input).toBe("保留指令");
    await act(async () => { await result.current.submit(); });
    expect(submit.mock.calls[0][2]).toBe(submit.mock.calls[1][2]);
  });

  it("uses frozen discussion sources even when earlier turns are not loaded", async () => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    const sourceId = "11111111-1111-4111-8111-111111111111";
    const selected = { ...turn("turn-2", "按前面方向整理"), sources: [{ id: `turn:${sourceId}`, type: "DISCUSSION", title: "之前的讨论", content: "已冻结的设想", truncated: false }] };
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    vi.spyOn(api.storyAssistant, "session").mockResolvedValue(session("story-a", "topic-a", [selected]));
    const submit = vi.spyOn(api.storyAssistant, "submit").mockResolvedValue({ turnId: "turn-3", operationId: "operation-1" });
    const { result } = renderHook(() => useStoryDiscussion("story-a", ""), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.turns.length).toBe(1));
    await act(async () => { await result.current.propose(selected); });
    expect(submit.mock.calls[0][1].sourceTurnIds).toEqual([sourceId, "turn-2"]);
  });

  it("refreshes recovered story turns in place and preserves the current input", async () => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    const read = vi.spyOn(api.storyAssistant, "session").mockResolvedValue(session("story-a", "topic-a"));
    const { result } = renderHook(() => useStoryDiscussion("story-a", ""), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.session.data?.id).toBe("topic-a"));
    act(() => result.current.setInput("保留未发送的讨论"));
    read.mockResolvedValue(session("story-a", "topic-a", [turn("turn-1", "已恢复的讨论")]));
    act(() => window.dispatchEvent(new CustomEvent(STORY_ASSISTANT_UPDATED)));
    await waitFor(() => expect(result.current.turns[0]?.instruction).toBe("已恢复的讨论"));
    expect(result.current.input).toBe("保留未发送的讨论");
  });

  it.each(["outline-original", null])("keeps the original refinement target %s even when another outline is selected", async originalOutline => {
    sessionStorage.setItem("ainovel.story-discussion:story-a", "topic-a");
    vi.spyOn(api.storyAssistant, "sessions").mockResolvedValue([]);
    vi.spyOn(api.storyAssistant, "session").mockResolvedValue(session("story-a", "topic-a"));
    const submit = vi.spyOn(api.storyAssistant, "submit").mockResolvedValue({ turnId: "turn-1", operationId: "operation-1" });
    const { result } = renderHook(() => useStoryDiscussion("story-a", "outline-other"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.session.data?.id).toBe("topic-a"));
    const proposal: StoryAssistantProposal = { id: "proposal-1", turnId: "turn-0", action: "UPDATE", resourceType: originalOutline ? "CHAPTER" : "CHARACTER", targetId: "target-1", outlineId: originalOutline, parentId: null, status: "READY", revision: 0, resourceRevision: 1, before: {}, candidate: { title: "原候选" }, modelCandidate: {}, current: {}, rationale: "", sources: [], sourceTurnIds: [], applicable: true, staleReason: null, undoAllowed: false, createdAt: "2026-10-15" };
    act(() => result.current.refine(proposal));
    act(() => result.current.setInput("再完善动机"));
    await act(async () => { await result.current.submit(); });
    expect(submit.mock.calls[0][1]).toMatchObject({ intent: "REFINE", parentProposalId: "proposal-1", outlineId: originalOutline || undefined });
  });
});
