import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import type { Manuscript } from "@/types";
import { agentLimits, type AgentPreview, type AgentRun } from "@/lib/api/domains/writing-agent";
import { useWritingAgent, type WritingAgentOptions } from "./useWritingAgent";

vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: vi.fn().mockResolvedValue({ status: "SUCCEEDED" }) }));
const id = "11111111-1111-4111-8111-111111111111", scene = "22222222-2222-4222-8222-222222222222", runId = "33333333-3333-4333-8333-333333333333", candidateId = "44444444-4444-4444-8444-444444444444";
const manuscript: Manuscript = { id, outlineId: id, title: "稿件", currentBranchId: id, version: 4, sections: { [scene]: "<p>原稿</p>" }, updatedAt: "2026-10-28" };
const preview: AgentPreview = { contextHash: "frozen", selectionRevision: 2, protocol: "writing-agent-v1", h2: false, binding: null, tools: ["candidate_draft", "world_search"], issues: [], limits: agentLimits, maxCostCny: 5, maxProjectCredits: 100, firstInput: [] };
const run: AgentRun = { id: runId, manuscriptId: id, sceneId: scene, branchId: id, status: "COMPLETED", revision: 3, operationId: id, progress: { message: "已完成" }, candidates: [{ id: candidateId, sequence: 1, parentId: null, html: "<p>候选</p>", hash: "candidate", actualHan: 2, replacements: [], sources: [] }], steps: [], settledCny: 0.01, reservedCny: 0, projectCredits: 1, maxCostCny: 5, maxProjectCredits: 100, createdAt: "2026-10-28T00:00:00Z", sourceHtml: manuscript.sections[scene], sourceVersion: 4, intent: "EDIT", h2: false, worldBinding: null, selectionRevision: 2 };
const clients: ReturnType<typeof createTestQueryClient>[] = [];
const deferred = <T,>() => { let resolve!: (value: T) => void;const promise = new Promise<T>(r => { resolve = r; });return { promise, resolve }; };
function mount(overrides: Partial<WritingAgentOptions> = {}) {
  const client = createTestQueryClient();clients.push(client);
  const write = vi.fn(async (_expected: string, action: (current: Manuscript) => Promise<Manuscript>) => action(manuscript));
  const reconcile = vi.fn(async (_expected: string, _server: string, action: (current: Manuscript) => Promise<Manuscript>) => ({ manuscript: await action(manuscript), applied: true }));
  const props: WritingAgentOptions = { manuscript, sceneId: scene, content: manuscript.sections[scene], dirty: false, busy: false, locked: false, h2: false, open: true, runWrite: write, reconcileWrite: reconcile, ...overrides };
  const hook = renderHook((value: WritingAgentOptions) => useWritingAgent(value), { initialProps: props, wrapper: createQueryClientWrapper(client) });
  return { ...hook, props, write, reconcile, client };
}
async function prepare(hook: ReturnType<typeof mount>) {
  await waitFor(() => expect(hook.result.current.history.data).toBeTruthy());
  act(() => hook.result.current.updateInput("完善表达"));
  await act(() => hook.result.current.prepare());
  await waitFor(() => expect(hook.result.current.preview).toBeTruthy());
}
beforeEach(() => {
  vi.spyOn(api.writingAgent, "list").mockResolvedValue([]);
  vi.spyOn(api.writingAgent, "preview").mockResolvedValue(preview);
  vi.spyOn(api.writingAgent, "start").mockResolvedValue({ runId, operationId: id });
  vi.spyOn(api.writingAgent, "accept").mockResolvedValue({ ...manuscript, version: 5, sections: { [scene]: "<p>候选</p>" } });
  vi.spyOn(api.writingAgent, "control").mockResolvedValue(run);
});
afterEach(() => { cleanup();clients.splice(0).forEach(client => client.clear());vi.restoreAllMocks(); });
describe("creative agent interaction boundaries", () => {
  it("allows read-only author questions over an unsaved draft without saving it", async () => {
    const hook = mount({ content: "<p>尚未保存的原稿</p>", dirty: true });act(() => hook.result.current.setIntent("AUTHOR_QA"));await prepare(hook);
    expect(hook.write).not.toHaveBeenCalled();expect(api.writingAgent.start).not.toHaveBeenCalled();
    await act(() => hook.result.current.start());
    expect(api.writingAgent.start).toHaveBeenCalledWith(id, scene, expect.objectContaining({ sourceHtml: "<p>尚未保存的原稿</p>", expectedVersion: 4, intent: "AUTHOR_QA", contextHash: "frozen", expectedSelectionRevision: 2 }), expect.any(String));
    expect(hook.write).not.toHaveBeenCalled();
  });
  it("requires a saved baseline before any ordinary editing preview or start", async () => {
    const hook = mount({ content: "<p>尚未保存的原稿</p>", dirty: true });await waitFor(() => expect(hook.result.current.history.data).toBeTruthy());act(() => hook.result.current.updateInput("修改当前正文"));
    await act(() => hook.result.current.prepare());await act(() => hook.result.current.start());
    expect(api.writingAgent.preview).not.toHaveBeenCalled();expect(api.writingAgent.start).not.toHaveBeenCalled();expect(hook.write).not.toHaveBeenCalled();expect(hook.result.current.input).toBe("修改当前正文");
  });
  it("preserves instructions typed during slow acceptance", async () => {
    const accepted = deferred<{ runId: string; operationId: string }>();vi.mocked(api.writingAgent.start).mockReturnValue(accepted.promise);
    const hook = mount();await prepare(hook);let started!: Promise<void>;
    act(() => { started = hook.result.current.start(); });
    await waitFor(() => expect(api.writingAgent.start).toHaveBeenCalled());
    act(() => hook.result.current.updateInput("后续新写的要求"));
    await act(async () => { accepted.resolve({ runId, operationId: id });await started; });
    expect(hook.result.current.input).toBe("后续新写的要求");
  });
  it("discards a late preview when its local source changed", async () => {
    const pending = deferred<AgentPreview>();vi.mocked(api.writingAgent.preview).mockReturnValue(pending.promise);
    const hook = mount();await waitFor(() => expect(hook.result.current.history.data).toBeTruthy());act(() => hook.result.current.updateInput("修改"));let preparing!: Promise<void>;
    act(() => { preparing = hook.result.current.prepare(); });
    hook.rerender({ ...hook.props, content: "<p>继续输入的新原文</p>" });
    await act(async () => { pending.resolve(preview);await preparing; });
    expect(hook.result.current.preview).toBeNull();expect(api.writingAgent.start).not.toHaveBeenCalled();
  });
  it("does not insert late acceptance state into a different scene", async () => {
    const pending = deferred<{ runId: string; operationId: string }>();vi.mocked(api.writingAgent.start).mockReturnValue(pending.promise);
    const hook = mount();await prepare(hook);let starting!: Promise<void>;act(() => { starting = hook.result.current.start(); });
    await waitFor(() => expect(api.writingAgent.start).toHaveBeenCalled());hook.rerender({ ...hook.props, sceneId: runId, content: "<p>另一个场景</p>" });
    act(() => hook.result.current.updateInput("新场景的目标"));
    await act(async () => { pending.resolve({ runId, operationId: id });await starting; });
    expect(hook.result.current.input).toBe("新场景的目标");expect(hook.result.current.selectedRunId).toBe("");expect(hook.result.current.notice).toBe("");
  });
  it("stops H2 preview while dirty and refuses adoption of dirty or author-QA content", async () => {
    const hook = mount({ h2: true, dirty: true });await waitFor(() => expect(hook.result.current.history.data).toBeTruthy());
    act(() => hook.result.current.updateInput("修改"));await act(() => hook.result.current.prepare());expect(api.writingAgent.preview).not.toHaveBeenCalled();
    await act(() => hook.result.current.accept(run, run.candidates[0]));expect(api.writingAgent.accept).not.toHaveBeenCalled();
    hook.rerender({ ...hook.props, dirty: false });await act(() => hook.result.current.accept({ ...run, intent: "AUTHOR_QA" }, run.candidates[0]));
    expect(api.writingAgent.accept).not.toHaveBeenCalled();expect(hook.write).not.toHaveBeenCalled();
  });
  it("reconciles an uncertain submission with its exact original key and frozen request", async () => {
    vi.mocked(api.writingAgent.start).mockRejectedValueOnce(new ApiError(503, "temporary"));
    const hook = mount();await prepare(hook);await act(() => hook.result.current.start());
    expect(hook.result.current.pending).toBeTruthy();act(() => { hook.result.current.updateInput("下一轮要求");hook.result.current.setMaxCost("2"); });
    await act(() => hook.result.current.reconcileSubmit());
    expect(vi.mocked(api.writingAgent.start).mock.calls[1]).toEqual(vi.mocked(api.writingAgent.start).mock.calls[0]);
    expect(hook.result.current.input).toBe("下一轮要求");
  });
  it("retains unknown adoption identity and uses the existing reconciliation queue", async () => {
    vi.mocked(api.writingAgent.accept).mockRejectedValueOnce(new ApiError(503, "temporary"));
    vi.spyOn(api.manuscripts, "get").mockResolvedValue({ ...manuscript, version: 5, sections: { [scene]: "<p>候选</p>" } });
    const hook = mount();await waitFor(() => expect(hook.result.current.history.data).toBeTruthy());
    await act(() => hook.result.current.accept(run, run.candidates[0]));expect(hook.result.current.pendingAdoption).toBeTruthy();
    hook.rerender({ ...hook.props, dirty: true, content: "<p>新输入必须保留</p>" });
    await act(() => hook.result.current.reconcileAccept());
    expect(hook.reconcile).toHaveBeenCalledWith(run.sourceHtml, run.candidates[0].html, expect.any(Function));
    expect(vi.mocked(api.writingAgent.accept).mock.calls[1]).toEqual(vi.mocked(api.writingAgent.accept).mock.calls[0]);
  });
  it("restores persisted candidates after refresh without model calls", async () => {
    vi.mocked(api.writingAgent.list).mockResolvedValue([run]);const hook = mount();
    await waitFor(() => expect(hook.result.current.selectedRun?.candidates[0].html).toBe("<p>候选</p>"));
    await act(() => hook.result.current.refresh());expect(api.writingAgent.start).not.toHaveBeenCalled();expect(api.writingAgent.accept).not.toHaveBeenCalled();
  });
  it("refreshes only agent history after recovery while preserving unsaved prose and instructions", async () => {
    vi.mocked(api.writingAgent.list).mockResolvedValueOnce([]).mockResolvedValue([run]);
    const manuscriptRead = vi.spyOn(api.manuscripts, "get").mockResolvedValue(manuscript);
    const hook = mount({ dirty: true, content: "<p>未保存的新正文</p>" });
    await waitFor(() => expect(hook.result.current.history.data).toEqual([]));
    act(() => hook.result.current.updateInput("尚未提交的新指令"));
    const invalidate = vi.spyOn(hook.client, "invalidateQueries");

    await act(async () => { window.dispatchEvent(new CustomEvent("ainovel:writing-agent-updated")); });

    await waitFor(() => expect(hook.result.current.selectedRun?.candidates[0].html).toBe("<p>候选</p>"));
    expect(invalidate).toHaveBeenCalledExactlyOnceWith({ queryKey: ["writing-agent", id, id, scene], exact: true });
    expect(hook.result.current.input).toBe("尚未提交的新指令");
    expect(hook.props.content).toBe("<p>未保存的新正文</p>");
    expect(manuscriptRead).not.toHaveBeenCalled();expect(hook.write).not.toHaveBeenCalled();expect(hook.reconcile).not.toHaveBeenCalled();
    expect(api.writingAgent.start).not.toHaveBeenCalled();expect(api.writingAgent.accept).not.toHaveBeenCalled();
  });
  it("moves the recovery listener to the current manuscript branch and scene", async () => {
    const hook = mount();await waitFor(() => expect(hook.result.current.history.data).toEqual([]));
    const nextManuscript = { ...manuscript, id: runId, currentBranchId: candidateId };
    hook.rerender({ ...hook.props, manuscript: nextManuscript, sceneId: runId, dirty: true, content: "<p>新场景未保存正文</p>" });
    await waitFor(() => expect(api.writingAgent.list).toHaveBeenCalledWith(runId, runId, candidateId, expect.any(AbortSignal)));
    act(() => hook.result.current.updateInput("新场景未提交的指令"));
    const invalidate = vi.spyOn(hook.client, "invalidateQueries");

    await act(async () => { window.dispatchEvent(new CustomEvent("ainovel:writing-agent-updated")); });

    expect(invalidate).toHaveBeenCalledExactlyOnceWith({ queryKey: ["writing-agent", runId, candidateId, runId], exact: true });
    expect(hook.result.current.input).toBe("新场景未提交的指令");
    expect(hook.write).not.toHaveBeenCalled();expect(hook.reconcile).not.toHaveBeenCalled();
  });
  it("sends author answers under the existing run revision and preserves command identity on uncertainty", async () => {
    vi.mocked(api.writingAgent.control).mockRejectedValueOnce(new ApiError(503, "temporary"));const hook = mount();await waitFor(() => expect(hook.result.current.history.data).toBeTruthy());
    await act(() => hook.result.current.control({ ...run, status: "WAITING_AUTHOR" }, "resume", "保留原有航行限制"));
    await act(() => hook.result.current.control({ ...run, status: "WAITING_AUTHOR" }, "resume", "保留原有航行限制"));
    expect(vi.mocked(api.writingAgent.control).mock.calls[0]).toEqual(vi.mocked(api.writingAgent.control).mock.calls[1]);
    expect(api.writingAgent.control).toHaveBeenCalledWith(id, scene, runId, "resume", 3, expect.any(String), "保留原有航行限制");
  });
});
