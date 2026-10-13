import { useState } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import type { AssistantProposal, AssistantTurn } from "@/lib/api/domains/writing-assistant";
import { useWritingAssistant } from "@/pages/Workbench/hooks/useWritingAssistant";
import { useManuscriptEditorState } from "@/pages/Workbench/hooks/useManuscriptEditorState";
import { WritingAssistantContext } from "./writing-assistant-context";
import CopilotSidebar from "./CopilotSidebar";

const manuscript: Manuscript = { id: "m", currentBranchId: "b", outlineId: "o", title: "稿件", version: 4, updatedAt: "2026-10-13", sections: { s: "<p>旧正文</p>" } };
const proposal: AssistantProposal = { id: "proposal", turnId: "turn", parentProposalId: null, status: "READY", applicable: true, staleReason: null, beforeHtml: "<p>旧正文</p>", afterHtml: "<p><strong>新</strong>正文</p>", sourceVersion: 4, branchId: "b", sourceHash: "h", minHan: 1, maxHan: 4, actualHanBefore: 3, actualHanAfter: 3 };
const turn: AssistantTurn = { id: "turn", operationId: "operation", status: "SUCCEEDED", resultKind: "PROPOSAL_READY", instruction: "改好这段", answer: "已保留结尾。", parentProposalId: null, proposalId: "proposal", createdAt: "2026-10-13T12:00:00Z", warnings: ["ASSISTANT_HISTORY_OMITTED:2"], usage: { inputTokens: 100, outputTokens: 30, cost: 1, modelCalls: 1 }, sources: [{ id: "source", type: "PREVIOUS_SCENE", title: "上一个场景", content: "前文依据", sceneId: "previous", truncated: true, hash: "h", reason: "EXCERPT:2:6" }] };
const clients: QueryClient[] = [];
beforeEach(() => {
  vi.spyOn(api.writingAssistant, "session").mockResolvedValue({ id: "session", branchId: "b", sceneId: "s", h2Enabled: false, turns: [turn], hasMore: false, nextBefore: null });
  vi.spyOn(api.writingAssistant, "proposal").mockResolvedValue(proposal);
  vi.spyOn(api.writingAssistant, "decide").mockResolvedValue({ proposalId: "proposal", status: "ACCEPTED", manuscript: { ...manuscript, version: 5, sections: { s: proposal.afterHtml } } });
});
afterEach(() => { cleanup(); clients.splice(0).forEach(client => client.clear()); vi.restoreAllMocks(); });
function mount(busy = false, initialHtml = manuscript.sections.s) {
  const write = vi.fn<ReturnType<typeof useManuscriptEditorState>["runAssistantWrite"]>();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); clients.push(client);
  function Workbench() {
    const [visible, setVisible] = useState(true);
    const [current, setCurrent] = useState<Manuscript>({ ...manuscript, sections: { ...manuscript.sections, s: initialHtml } });
    const state = useManuscriptEditorState({ selectedManuscript: current, selectedManuscriptId: "m", selectedSceneId: "s", selectedStoryId: "", replaceManuscript: setCurrent, toast: vi.fn() });
    write.mockImplementation(state.runAssistantWrite);
    const assistant = useWritingAssistant({ manuscript: current, sceneId: "s", sceneTitle: "当前场景", content: state.content, busy, locked: state.assistantWriteLocked, open: vi.fn(), runWrite: write, reconcileWrite: state.reconcileAssistantWrite, initializeBranch: vi.fn(), onSourceResolved: setCurrent });
    return <WritingAssistantContext.Provider value={assistant}><button onClick={() => setVisible(value => !value)}>切换移动侧栏</button><button onClick={() => state.updateSceneDraft("s", "<p>作者继续编辑</p>")}>继续编辑</button><output data-testid="editor-body">{state.content}</output><output data-testid="server-other">{current.sections.other}</output>{visible && <CopilotSidebar />}</WritingAssistantContext.Provider>;
  }
  return { ...render(<QueryClientProvider client={client}><MemoryRouter><Workbench /></MemoryRouter></QueryClientProvider>), write };
}
describe("writing assistant proposal workflow", () => {
  it("restores server history, keeps the mobile composer and adopts only after showing complete differences", async () => {
    const { container, write } = mount();
    await screen.findByText("改好这段");
    await waitFor(() => expect(container.querySelector("del")?.textContent).toBe("旧"));
    expect(container.querySelector("ins")?.textContent).toBe("新");
    expect(container.querySelector("strong")?.textContent).toBe("新");
    expect(screen.getByText(/省略 2 项/)).toBeTruthy();
    expect(screen.getByText(/第 3–6 个文本位置/)).toBeTruthy();
    expect(screen.queryByText(/ASSISTANT_HISTORY_OMITTED/)).toBeNull();
    fireEvent.change(screen.getByRole("textbox", { name: "写作要求" }), { target: { value: "待发要求" } });
    fireEvent.click(screen.getByText("切换移动侧栏"));
    fireEvent.click(screen.getByText("切换移动侧栏"));
    expect((screen.getByRole("textbox", { name: "写作要求" }) as HTMLTextAreaElement).value).toBe("待发要求");
    expect(write).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "继续打磨" }));
    expect(screen.getByText("沿用原提案范围")).toBeTruthy();
    expect(screen.queryByText("整个当前场景")).toBeNull();
    await act(async () => { fireEvent.click(screen.getByRole("button", { name: "采纳并保存" })); });
    expect(write).toHaveBeenCalledWith(proposal.beforeHtml, expect.any(Function), true);
    expect(api.writingAssistant.decide).toHaveBeenCalledWith("m", "s", "proposal", "accept", 4, "b", expect.any(String));
  });
  it("blocks adoption while another manuscript write is active but still allows rejection", async () => {
    mount(true);
    const adopt = await screen.findByRole("button", { name: "采纳并保存" });
    expect((adopt as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "拒绝" }) as HTMLButtonElement).disabled).toBe(false);
  });
  it.each(["FAILED", "RECOVERY_REQUIRED", "CANCELLED"])("labels zero usage as confirmed so %s does not imply a final zero bill", async status => {
    vi.mocked(api.writingAssistant.session).mockResolvedValue({ id: "session", branchId: "b", sceneId: "s", h2Enabled: false,
      turns: [{ ...turn, status, resultKind: null, proposalId: null, usage: { inputTokens: 0, outputTokens: 0, cost: 0, modelCalls: 0 } }], hasMore: false, nextBefore: null });
    mount();
    const usage = await screen.findByText(/已确认用量：0 次模型调用/);
    expect(usage.textContent).toContain("0 积分");
    expect(usage.textContent).toContain("未确认调用仍待对账");
    expect(usage.textContent).toContain("这不是最终账单");
  });
  it.each(["accept", "undo"] as const)("keeps a replay button after a committed %s loses its response and refreshes the proposal", async action => {
    let serverProposal: AssistantProposal = { ...proposal, status: action === "accept" ? "READY" : "ACCEPTED" };
    vi.mocked(api.writingAssistant.proposal).mockImplementation(async () => serverProposal);
    const expected = action === "accept" ? proposal.afterHtml : proposal.beforeHtml;
    const receipt = { ...manuscript, version: 5, sections: { s: expected, other: "旧的其他场景" } };
    const decide = vi.mocked(api.writingAssistant.decide).mockImplementationOnce(async () => {
      serverProposal = { ...serverProposal, status: action === "accept" ? "ACCEPTED" : "UNDONE" };
      throw new ApiError(503, "response lost");
    }).mockResolvedValue({ proposalId: proposal.id, status: action === "accept" ? "ACCEPTED" : "UNDONE", manuscript: receipt });
    vi.spyOn(api.manuscripts, "get").mockResolvedValue({ ...receipt, version: 6, sections: { ...receipt.sections, other: "其他场景的新版本" } });
    mount(false, action === "accept" ? proposal.beforeHtml : proposal.afterHtml);
    const decisionButton = await screen.findByRole("button", { name: action === "accept" ? "采纳并保存" : "撤销本次修改" });
    await act(async () => { fireEvent.click(decisionButton); });
    const check = await screen.findByRole("button", { name: "核对本次保存" });
    expect((check as HTMLButtonElement).disabled).toBe(false);
    expect(screen.getByText(action === "accept" ? "已采纳并保存" : "本次修改已撤销")).toBeTruthy();
    await act(async () => { fireEvent.click(check); });
    expect(decide.mock.calls[1]).toEqual(decide.mock.calls[0]);
    expect(screen.getByTestId("editor-body").textContent).toBe(expected);
    expect(screen.getByTestId("server-other").textContent).toBe("其他场景的新版本");
    expect(screen.queryByRole("button", { name: "核对本次保存" })).toBeNull();
  });
  it("confirms the original save without overwriting a new draft typed after the lost response", async () => {
    const receipt = { ...manuscript, version: 5, sections: { s: proposal.afterHtml } };
    vi.mocked(api.writingAssistant.decide).mockRejectedValueOnce(new ApiError(503, "response lost")).mockResolvedValue({ proposalId: proposal.id, status: "ACCEPTED", manuscript: receipt });
    vi.spyOn(api.manuscripts, "get").mockResolvedValue(receipt);
    const save = vi.spyOn(api.manuscripts, "saveSection");
    mount();
    const adopt = await screen.findByRole("button", { name: "采纳并保存" });
    await act(async () => { fireEvent.click(adopt); });
    fireEvent.click(screen.getByRole("button", { name: "继续编辑" }));
    const check = await screen.findByRole("button", { name: "核对本次保存" });
    await act(async () => { fireEvent.click(check); });
    expect(screen.getByTestId("editor-body").textContent).toBe("<p>作者继续编辑</p>");
    expect(screen.getByText(/服务端已完成本次保存/).textContent).toContain("本地草稿已保留");
    expect(save).not.toHaveBeenCalled();
  });
});
