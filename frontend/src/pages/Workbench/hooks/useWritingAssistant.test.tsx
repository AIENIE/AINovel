import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { PropsWithChildren } from "react";
import { api, ApiError } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import type { AssistantProposal } from "@/lib/api/domains/writing-assistant";
import { useWritingAssistant } from "./useWritingAssistant";

vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: vi.fn().mockResolvedValue({ status: "SUCCEEDED" }), retryTrackedAiOperation: vi.fn().mockResolvedValue(undefined) }));
const manuscript: Manuscript = { id: "m", currentBranchId: "b", version: 4, outlineId: "o", title: "稿件", sections: { s: "<p>原稿</p>" }, updatedAt: "2026-10-13" };
const session = { id: null, branchId: "b", sceneId: "s", h2Enabled: false, turns: [], hasMore: false, nextBefore: null };
const proposal: AssistantProposal = { id: "p", turnId: "t", parentProposalId: null, status: "READY", applicable: true, staleReason: null, beforeHtml: "<p>原稿</p>", afterHtml: "<p>改稿</p>", sourceVersion: 4, branchId: "b", actualHanBefore: 2, actualHanAfter: 2, minHan: 2800, maxHan: 3200, sourceHash: "hash" };
const clients: QueryClient[] = [];
function mount(h2 = false, write = vi.fn(async (_html: string, action: (current: Manuscript) => Promise<Manuscript>) => action({ ...manuscript, version: 5 }))) {
  vi.spyOn(api.writingAssistant, "session").mockResolvedValue({ ...session, h2Enabled: h2 });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); clients.push(client);
  const wrapper = ({ children }: PropsWithChildren) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  const reconcile = vi.fn(async (_expected: string, _server: string, action: (current: Manuscript) => Promise<Manuscript>) => ({ manuscript: await action(manuscript), applied: true }));
  const hook = renderHook(({ content, sceneId, version }) => useWritingAssistant({ manuscript: { ...manuscript, version }, sceneId, sceneTitle: "场景", content, busy: false, locked: false, open: vi.fn(), runWrite: write, reconcileWrite: reconcile, initializeBranch: vi.fn(), onSourceResolved: vi.fn() }), { wrapper, initialProps: { content: "<p>原稿</p>", sceneId: "s", version: 4 } });
  return { ...hook, write, reconcile };
}
beforeEach(() => { vi.spyOn(api.writingAssistant, "submit").mockResolvedValue({ turnId: "t", operationId: "op" }); });
afterEach(() => { cleanup(); clients.splice(0).forEach(client => client.clear()); vi.restoreAllMocks(); });
describe("persistent writing assistant", () => {
  it("freezes an unsaved ordinary draft without an implicit save", async () => {
    const { result, rerender, write } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    rerender({ content: "<p>尚未保存</p>", sceneId: "s", version: 4 });
    act(() => result.current.setInput("让表达自然"));
    await act(() => result.current.submit());
    expect(write).not.toHaveBeenCalled();
    expect(api.writingAssistant.submit).toHaveBeenCalledWith("m", "s", expect.objectContaining({ sourceHtml: "<p>尚未保存</p>", expectedVersion: 4, instruction: "让表达自然" }), expect.any(String));
  });
  it("does not submit H2 when its real baseline save fails", async () => {
    const write = vi.fn().mockRejectedValue(new ApiError(409, "MANUSCRIPT_VERSION_CONFLICT"));
    const { result } = mount(true, write);
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => result.current.setInput("修改"));
    await act(() => result.current.submit());
    expect(api.writingAssistant.submit).not.toHaveBeenCalled();
    expect(result.current.input).toBe("修改");
  });
  it("uses the confirmed H2 version and opening a shortcut never submits", async () => {
    const { result } = mount(true);
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => result.current.openEditorAction({ html: "<p>原稿</p>", text: "原稿", supported: true, selection: { startBlockIndex: 0, endBlockIndex: 0, startOffset: 0, endOffset: 2 } }, "polish"));
    expect(api.writingAssistant.submit).not.toHaveBeenCalled();
    await act(() => result.current.submit());
    expect(api.writingAssistant.submit).toHaveBeenCalledWith("m", "s", expect.objectContaining({ expectedVersion: 5, intent: "EDIT" }), expect.any(String));
  });
  it("does not widen a stale selection to the entire updated scene", async () => {
    const { result, rerender } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => { result.current.openEditorAction({ html: "<p>原稿</p>", text: "原稿", selection: { startBlockIndex: 0, endBlockIndex: 0, startOffset: 0, endOffset: 2 }, supported: true }, "polish"); });
    rerender({ content: "<p>更新后的正文</p>", sceneId: "s", version: 4 });
    await act(() => result.current.submit());
    expect(api.writingAssistant.submit).not.toHaveBeenCalled();
    expect(result.current.error).toContain("选区");
  });
  it("can submit an insertion into TipTap's empty paragraph over an empty scene", async () => {
    const { result, rerender } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    rerender({ content: "", sceneId: "s", version: 4 });
    act(() => result.current.openEditorAction({ html: "<p></p>", text: "", supported: true, selection: { startBlockIndex: 0, endBlockIndex: 0, startOffset: 0, endOffset: 0 } }, "continue"));
    await act(() => result.current.submit());
    expect(api.writingAssistant.submit).toHaveBeenCalledWith("m", "s", expect.objectContaining({ sourceHtml: "", selection: { startBlockIndex: 0, endBlockIndex: 0, startOffset: 0, endOffset: 0 } }), expect.any(String));
  });
  it("keeps the exact key and body after an uncertain submit", async () => {
    const submit = vi.mocked(api.writingAssistant.submit).mockRejectedValueOnce(new ApiError(503, "unavailable"));
    const { result, rerender } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => result.current.setInput("修改"));
    await act(() => result.current.submit());
    rerender({ content: "<p>原稿</p>", sceneId: "s", version: 5 });
    await act(() => result.current.submit());
    expect(submit.mock.calls[1]).toEqual(submit.mock.calls[0]);
  });
  it("does not reuse an uncertain author-QA request after explicitly choosing edit", async () => {
    const submit = vi.mocked(api.writingAssistant.submit).mockRejectedValueOnce(new ApiError(503, "unavailable"));
    const { result } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => { result.current.setInput("修改这句"); result.current.setIntent("AUTHOR_QA"); });
    await act(() => result.current.submit());
    act(() => result.current.setIntent("EDIT"));
    await act(() => result.current.submit());
    expect(submit.mock.calls[1][2].intent).toBe("EDIT");
    expect(submit.mock.calls[1][3]).not.toBe(submit.mock.calls[0][3]);
  });
  it("uses whole-scene read-only context for an author question about a fixed-structure selection", async () => {
    const { result } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => { result.current.capture({ html: "<p>原稿</p>", supported: false, text: "标题", selection: { startBlockIndex: 0, endBlockIndex: 0, startOffset: 0, endOffset: 2 } }); result.current.setIntent("AUTHOR_QA"); result.current.setInput("解释这里"); });
    await act(() => result.current.submit());
    expect(api.writingAssistant.submit).toHaveBeenCalledWith("m", "s", expect.objectContaining({ intent: "AUTHOR_QA", selection: null }), expect.any(String));
  });
  it("keeps uncertain adoption keys but refreshes them after a definite version rejection", async () => {
    const decide = vi.spyOn(api.writingAssistant, "decide").mockRejectedValueOnce(new ApiError(503, "unavailable")).mockRejectedValueOnce(new ApiError(409, "MANUSCRIPT_VERSION_CONFLICT")).mockResolvedValue({ proposalId: "p", status: "ACCEPTED", manuscript });
    const { result } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    await act(() => result.current.decide(proposal, "accept"));
    await act(() => result.current.decide(proposal, "accept"));
    await act(() => result.current.decide(proposal, "accept"));
    expect(decide.mock.calls[1]).toEqual(decide.mock.calls[0]);
    expect(decide.mock.calls[2][6]).not.toEqual(decide.mock.calls[1][6]);
  });
  it("queries persistent history under a new scene instead of reusing old turns", async () => {
    const { result, rerender } = mount();
    await waitFor(() => expect(result.current.session.data).toBeTruthy());
    act(() => result.current.setInput("旧场景要求"));
    rerender({ content: "<p>新场景</p>", sceneId: "other", version: 4 });
    await waitFor(() => expect(api.writingAssistant.session).toHaveBeenCalledWith("m", "other", "b", undefined, expect.any(AbortSignal)));
    expect(result.current.input).toBe("");
  });
});
