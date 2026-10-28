import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api-client";
import * as assistantEditor from "@/lib/assistant-editor";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import { agentLimits, type AgentRun } from "@/lib/api/domains/writing-agent";
import { localizedErrorMessage, localizedOperationFailure } from "@/lib/error-messages";
import type { Manuscript } from "@/types";
import i18n from "@/i18n";
import { WritingAgentPanel, type WritingAgentPanelProps } from "./WritingAgentPanel";

vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: vi.fn().mockResolvedValue({ status: "SUCCEEDED" }) }));
const id = "11111111-1111-4111-8111-111111111111";
const manuscript: Manuscript = { id, outlineId: id, title: "稿件", version: 1, currentBranchId: id, sections: { [id]: "<p>冻结的原文</p>" }, updatedAt: "2026-10-28" };
const candidate = { id, sequence: 1, parentId: null, html: "<p>完整候选正文</p>", hash: "candidate", actualHan: 6, replacements: [], sources: [{ type: "PERSON", title: "守塔人", text: "守塔人已提供的公开材料", start: 0, end: 13 }] };
const run = (status = "COMPLETED"): AgentRun => ({ id, manuscriptId: id, sceneId: id, branchId: id, status, revision: 2, operationId: id, progress: { message: "已完成本轮" }, candidates: [candidate], steps: [{ sequence: 1, kind: "TOOL", feature: "candidate_check_language", status: "SUCCEEDED", result: { kind: "LANGUAGE", candidateHash: "candidate", report: { complete: false, summary: "仅部分范围完成", issues: [{ quote: "正文", impact: "证据不足", direction: "请作者核对", kind: "OBSERVATION" }] } }, reservedCny: 0, settledCny: 0.01, projectCredits: 1 }], settledCny: 0.01, reservedCny: 0, projectCredits: 1, maxCostCny: 5, maxProjectCredits: 100, createdAt: "2026-10-28T00:00:00Z", sourceHtml: manuscript.sections[id], sourceVersion: 1, intent: "EDIT", h2: false, worldBinding: null, selectionRevision: 2 });
const clients: ReturnType<typeof createTestQueryClient>[] = [];
function mount(overrides: Partial<WritingAgentPanelProps> = {}) {
  const client = createTestQueryClient();clients.push(client);
  const props: WritingAgentPanelProps = { manuscript, sceneId: id, content: manuscript.sections[id], dirty: false, busy: false, locked: false, h2: false, runWrite: vi.fn(), reconcileWrite: vi.fn(), ...overrides };
  const rendered = render(<WritingAgentPanel {...props} />, { wrapper: createQueryClientWrapper(client) });
  fireEvent.click(screen.getByRole("button", { name: i18n.t("agent.title") }));return { ...rendered, props };
}
beforeEach(async () => { await i18n.changeLanguage("zh-CN");vi.spyOn(api.writingAgent, "list").mockResolvedValue([run()]);vi.spyOn(api.writingAgent, "control").mockResolvedValue(run("RUNNING"));vi.spyOn(api.writingAgent, "start").mockResolvedValue({ runId: id, operationId: id });vi.spyOn(api.writingAgent, "accept").mockResolvedValue(manuscript); });
afterEach(() => { cleanup();clients.splice(0).forEach(client => client.clear());vi.restoreAllMocks(); });
describe("creative agent review panel", () => {
  it("restores full comparison and actual sources without computing a character diff until requested", async () => {
    const diff = vi.spyOn(assistantEditor, "assistantTextDiff");mount();
    const review = await screen.findByRole("region", { name: "当前候选" });
    expect(within(review).getByText("冻结的原文")).toBeTruthy();expect(within(review).getByText("完整候选正文")).toBeTruthy();expect(diff).not.toHaveBeenCalled();
    fireEvent.click(within(review).getByRole("button", { name: "逐字差异" }));expect(diff).toHaveBeenCalledOnce();
    fireEvent.click(within(review).getByRole("button", { name: "完整前后对照" }));expect(within(review).getByText("完整候选正文")).toBeTruthy();
    expect(screen.getByText("世界人物")).toBeTruthy();expect(screen.getByText("检查未完整覆盖，不能视为通过。")).toBeTruthy();
    expect(api.writingAgent.start).not.toHaveBeenCalled();
  });
  it("disables author adoption for unsaved input and retains readable old candidates", async () => {
    mount({ dirty: true, content: "<p>继续输入的新正文</p>" });
    const button = await screen.findByRole("button", { name: "采纳此候选" });expect(button.hasAttribute("disabled")).toBe(true);
    expect(screen.getByText("完整候选正文")).toBeTruthy();expect(screen.getByText("有未保存输入，采纳暂不可用。")).toBeTruthy();expect(api.writingAgent.accept).not.toHaveBeenCalled();
  });
  it("blocks unsaved edit startup but lets an explicit author question preview the draft", async () => {
    vi.spyOn(api.writingAgent, "preview").mockResolvedValue({ contextHash: "qa-draft", selectionRevision: 0, protocol: "writing-agent-v1", h2: true, binding: null, tools: ["context_read"], issues: [], limits: agentLimits, maxCostCny: 5, maxProjectCredits: 100, firstInput: [] });
    mount({ dirty: true, h2: true, content: "<p>未保存的作者草稿</p>" });
    fireEvent.change(screen.getByRole("textbox", { name: "这次希望完成什么？" }), { target: { value: "解释当前草稿" } });
    expect(screen.getByRole("button", { name: "预览范围与工具" }).hasAttribute("disabled")).toBe(true);
    expect(screen.getByText("请先保存当前正文，再预览并启动候选创作。作者问答可以读取未保存稿。")).toBeTruthy();
    fireEvent.change(screen.getByRole("combobox", { name: "用途" }), { target: { value: "AUTHOR_QA" } });
    fireEvent.click(screen.getByRole("button", { name: "预览范围与工具" }));
    await waitFor(() => expect(api.writingAgent.preview).toHaveBeenCalledWith(id, id, expect.objectContaining({ intent: "AUTHOR_QA", sourceHtml: "<p>未保存的作者草稿</p>" })));
    expect(api.writingAgent.start).not.toHaveBeenCalled();
  });
  it("lets authors expand the exact first input without starting a model call", async () => {
    vi.spyOn(api.writingAgent, "preview").mockResolvedValue({ contextHash: "preview", selectionRevision: 0, protocol: "writing-agent-v1", h2: false, binding: null, tools: ["world_search"], issues: [], limits: agentLimits, maxCostCny: 5, maxProjectCredits: 100,
      firstInput: [{ role: "system", content: "首步冻结规则样例" }, { role: "user", content: JSON.stringify({ sourceHtml: manuscript.sections[id], instruction: "核对航行设定" }) }] });
    mount();fireEvent.change(screen.getByRole("textbox", { name: "这次希望完成什么？" }), { target: { value: "核对航行设定" } });
    fireEvent.click(screen.getByRole("button", { name: "预览范围与工具" }));
    const first = await screen.findByText("首步实际发送内容");fireEvent.click(first);
    expect(within(first.closest("details")!).getByText("首步冻结规则样例")).toBeTruthy();
    expect(screen.getByText("这是当前预览，确认报价后才发送。后续取材由工具决定，预览未读取的世界材料不代表已发送。")).toBeTruthy();
    expect(api.writingAgent.start).not.toHaveBeenCalled();
  });
  it("separates a language model step's candidate-only source from earlier world reads", async () => {
    const value = run();value.steps.push({ sequence: 2, kind: "MODEL", feature: "language.diagnose", status: "COMPLETED", result: { message: "本步完成语言检查", transport: "BUDGETED_UNARY", sources: [{ id: "candidate-current", type: "CANDIDATE", title: "本步候选输入", content: "仅本步真正发送的候选正文" }] }, reservedCny: 0, settledCny: 0.01, projectCredits: 1 });
    vi.mocked(api.writingAgent.list).mockResolvedValue([value]);mount();
    const label = await screen.findByText("本步真正发送的来源");const step = label.closest("details")!;
    expect(within(step).getByText("当前场景候选")).toBeTruthy();expect(within(step).getByText("仅本步真正发送的候选正文")).toBeTruthy();
    expect(within(step).queryByText("守塔人")).toBeNull();expect(screen.getByText("守塔人")).toBeTruthy();
  });
  it("shows a failed budget preparation as one recorded step with no saved model result", async () => {
    const value: AgentRun = { ...run("PAUSED_BUDGET"), progress: { message: "WRITING_AGENT_BUDGET_UNAVAILABLE", modelCalls: 1, revisions: 0 }, candidates: [],
      steps: [{ sequence: 1, kind: "MODEL", feature: "writing_agent.control", status: "PREPARED", result: {}, reservedCny: 0, settledCny: null, projectCredits: null }],
      settledCny: 0, reservedCny: 0, projectCredits: 0 };
    vi.mocked(api.writingAgent.list).mockResolvedValue([value]);mount();

    await screen.findByText("本步预算准备失败，尚未向模型发送请求。请检查预算服务和模型方案，再查看原任务状态。");
    expect(screen.getByText("已记录 1 个模型步骤 · 已保存 0 项模型结果 · 候选 0 份")).toBeTruthy();
    expect(screen.getByText("1 · 模型步骤 · 准备模型步骤")).toBeTruthy();
    expect(screen.queryByText(/已调用 1 次模型/)).toBeNull();
    expect(screen.queryByText("WRITING_AGENT_BUDGET_UNAVAILABLE")).toBeNull();
    expect(screen.getByText("执行已暂停，待处理")).toBeTruthy();expect(screen.queryByText(i18n.t("agent.budgetPaused"))).toBeNull();
    expect(screen.queryByRole("button", { name: "继续原任务" })).toBeNull();expect(value.status).toBe("PAUSED_BUDGET");
    expect(api.writingAgent.start).not.toHaveBeenCalled();expect(api.writingAgent.control).not.toHaveBeenCalled();
  });
  it.each(["WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE", "WRITING_AGENT_BUDGET_GRANT_INVALID", "WRITING_AGENT_BUDGET_PREPARE_GRPC_UNAVAILABLE", "AI_NATIVE_CONFIGURATION_REQUIRED", "AI_FEATURE_UNCONFIGURED", "WRITING_AGENT_CONFIRMED_NOT_SENT"])("shows a legacy budget pause from %s as preparation failure without changing actions", async code => {
    const value = { ...run("PAUSED_BUDGET"), progress: { message: code } };
    vi.mocked(api.writingAgent.list).mockResolvedValue([value]);mount();
    await screen.findByText("执行已暂停，待处理");
    expect(screen.queryByText(i18n.t("agent.budgetPaused"))).toBeNull();
    expect(within(screen.getByRole("combobox", { name: i18n.t("agent.history") })).getByRole("option").textContent).toContain("执行已暂停，待处理");
    expect(screen.queryByRole("button", { name: "继续原任务" })).toBeNull();
    expect(screen.getByRole("button", { name: "结束任务" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "采纳此候选" }).hasAttribute("disabled")).toBe(false);
    expect(value.status).toBe("PAUSED_BUDGET");expect(value.progress.message).toBe(code);expect(api.writingAgent.control).not.toHaveBeenCalled();
  });
  it.each(["WRITING_AGENT_BUDGET_EXHAUSTED", "WRITING_AGENT_CALL_LIMIT", "AI_VALIDATION_PHASE_BUDGET_EXHAUSTED", "WRITING_AGENT_BUDGET_PREPARE_GRPC_UNRECOGNIZED"])("retains budget exhaustion display for %s", async code => {
    vi.mocked(api.writingAgent.list).mockResolvedValue([{ ...run("PAUSED_BUDGET"), progress: { message: code } }]);mount();
    const notices = await screen.findAllByText(i18n.t("agent.budgetPaused"));
    expect(notices.some(notice => notice.classList.contains("text-amber-700"))).toBe(true);
    expect(screen.getByText("达到预算上限")).toBeTruthy();expect(screen.queryByText("执行已暂停，待处理")).toBeNull();
    expect(screen.queryByRole("button", { name: "继续原任务" })).toBeNull();expect(api.writingAgent.control).not.toHaveBeenCalled();
  });
  it("counts persisted model steps and results separately from progress attempts and tool records", async () => {
    const value = run();value.progress = { modelCalls: 99, revisions: 99 };
    value.steps.push(
      { sequence: 2, kind: "MODEL", feature: "writing_agent.control", status: "COMPLETED", result: { message: "已选择下一步" }, reservedCny: 0, settledCny: 0.01, projectCredits: 1 },
      { sequence: 3, kind: "MODEL", feature: "language.diagnose", status: "NOT_SENT", result: {}, reservedCny: 0, settledCny: null, projectCredits: null },
    );
    vi.mocked(api.writingAgent.list).mockResolvedValue([value]);mount();

    await screen.findByText("已记录 2 个模型步骤 · 已保存 1 项模型结果 · 候选 1 份");
    expect(screen.getByText("3 · 模型步骤 · 已确认未发送")).toBeTruthy();
    expect(screen.queryByText(/99/)).toBeNull();
  });
  it.each(["zh-CN", "zh-TW", "en"])("localizes new stop reasons in %s without exposing internal keys", async locale => {
    await i18n.changeLanguage(locale);
    for (const code of ["WRITING_AGENT_SAVE_REQUIRED", "WRITING_AGENT_PROTOCOL_UNAVAILABLE", "AI_VALIDATION_PHASE_BUDGET_EXHAUSTED", "AI_VALIDATION_PHASE_BASELINE_INVALID", "WRITING_AGENT_CHECK_LIMIT", "WORLD_AGENT_REQUIRED_READ_CAPACITY", "AUTHOR_QA_READ_ONLY", "H2_WORLD_TOOL_UNAVAILABLE",
      "WRITING_AGENT_BUDGET_UNAVAILABLE", "WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE", "WRITING_AGENT_BUDGET_GRANT_INVALID", "AI_VALIDATION_PREPARE_UNAVAILABLE", "WRITING_AGENT_BUDGET_INVALID", "WRITING_AGENT_PRICE_INVALID", "AI_VALIDATION_QUOTE_INVALID", "AI_VALIDATION_PRICE_UNAVAILABLE", "AI_NATIVE_CONFIGURATION_REQUIRED", "AI_NATIVE_BUDGET_UNAVAILABLE", "AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE", "AI_VALIDATION_CAPABILITY_UNAVAILABLE", "WRITING_AGENT_TOOL_UNAVAILABLE", "WRITING_AGENT_CONFIRMED_NOT_SENT", "AI_VALIDATION_CONFIRMED_NOT_SENT", "WRITING_AGENT_RECEIPT_INVALID", "WRITING_AGENT_RECEIPT_MISMATCH", "WRITING_AGENT_GRANT_INVALID", "WRITING_AGENT_RETAIL_MISMATCH", "WRITING_AGENT_LIMIT_INVALID", "AI_NATIVE_RESPONSE_INVALID", "AI_FEATURE_UNCONFIGURED", "AI_CONFIGURATION_CHANGED", "AI_OPTION_UNAVAILABLE"]) {
      const value = localizedOperationFailure(code);expect(value).not.toBe(code);expect(value).not.toContain("agent.");expect(value).not.toBe(i18n.t("errors.operationFailed"));
      expect(localizedErrorMessage(new ApiError(412, code))).toBe(value);
    }
    expect(localizedOperationFailure("WRITING_AGENT_UNKNOWN_UPSTREAM_SECRET")).toBe(i18n.t("errors.operationFailed"));
  });
  it.each(["zh-CN", "zh-TW", "en"])("distinguishes prepare transport, unavailable and invalid grant failures safely in %s", async locale => {
    await i18n.changeLanguage(locale);
    const legacy = localizedOperationFailure("WRITING_AGENT_BUDGET_UNAVAILABLE");
    const unavailable = localizedOperationFailure("WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE");
    const invalidGrant = localizedOperationFailure("WRITING_AGENT_BUDGET_GRANT_INVALID");
    expect(new Set([legacy, unavailable, invalidGrant]).size).toBe(3);
    expect(invalidGrant).not.toBe(localizedOperationFailure("WRITING_AGENT_GRANT_INVALID"));
    for (const status of ["OK", "CANCELLED", "UNKNOWN", "INVALID_ARGUMENT", "DEADLINE_EXCEEDED", "NOT_FOUND", "ALREADY_EXISTS", "PERMISSION_DENIED", "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION", "ABORTED", "OUT_OF_RANGE", "UNIMPLEMENTED", "INTERNAL", "UNAVAILABLE", "DATA_LOSS", "UNAUTHENTICATED"]) {
      const code = `WRITING_AGENT_BUDGET_PREPARE_GRPC_${status}`;
      const value = localizedOperationFailure(code);
      expect(value).toContain(status);expect(value).not.toContain("WRITING_AGENT_");expect(value).not.toBe(legacy);expect(value).not.toBe(unavailable);expect(value).not.toBe(invalidGrant);
      expect(localizedErrorMessage(new ApiError(412, code))).toBe(value);
    }
    for (const unsafe of ["WRITING_AGENT_BUDGET_PREPARE_GRPC_UNKNOWN_STATUS", "WRITING_AGENT_BUDGET_PREPARE_GRPC_UNAVAILABLE: private upstream description", "WRITING_AGENT_BUDGET_PREPARE_GRPC_INVALID_ARGUMENT\nprivate upstream description"]) {
      expect(localizedOperationFailure(unsafe)).toBe(i18n.t("errors.operationFailed"));
      expect(localizedErrorMessage(new ApiError(412, unsafe))).not.toContain("private upstream description");
    }
  });
  it("shows a saved prepare gRPC status without treating it as an inference or retrying", async () => {
    vi.mocked(api.writingAgent.list).mockResolvedValue([{ ...run("PAUSED_INPUT"), progress: { message: "WRITING_AGENT_BUDGET_PREPARE_GRPC_INVALID_ARGUMENT", modelCalls: 1 }, candidates: [],
      steps: [{ sequence: 1, kind: "MODEL", feature: "writing_agent.control", status: "PREPARED", result: {}, reservedCny: 0, settledCny: null, projectCredits: null }], settledCny: 0, reservedCny: 0, projectCredits: 0 }]);
    mount();
    await screen.findByText("预算准备服务返回 INVALID_ARGUMENT，尚未向模型发送请求。请核对预算服务与模型方案，再查看原任务状态。");
    expect(screen.getByText("已记录 1 个模型步骤 · 已保存 0 项模型结果 · 候选 0 份")).toBeTruthy();
    expect(screen.getByText("执行已暂停，待处理")).toBeTruthy();expect(screen.queryByText("输入容量不足")).toBeNull();
    expect(screen.queryByText("WRITING_AGENT_BUDGET_PREPARE_GRPC_INVALID_ARGUMENT")).toBeNull();
    expect(api.writingAgent.start).not.toHaveBeenCalled();expect(api.writingAgent.control).not.toHaveBeenCalled();
  });
  it.each([["zh-CN", "执行已暂停，待处理"], ["zh-TW", "執行已暫停，待處理"], ["en", "Paused; review required"]])("keeps the paused status general and the capacity reason specific in %s", async (locale, label) => {
    await i18n.changeLanguage(locale);
    expect(i18n.t("agent.status.PAUSED_INPUT")).toBe(label);
    expect(localizedOperationFailure("WRITING_AGENT_TOOL_INPUT_LIMIT")).toBe(i18n.t("agent.inputLimit"));
    expect(localizedOperationFailure("WRITING_AGENT_BUDGET_PREPARE_GRPC_INTERNAL")).not.toBe(i18n.t("agent.inputLimit"));
  });
  it("sends an explicit author answer and does not resume a budget-limited run", async () => {
    vi.mocked(api.writingAgent.list).mockResolvedValue([{ ...run("WAITING_AUTHOR"), progress: { message: "需要确定航行代价" } }]);
    const view = mount();const resume = await screen.findByRole("button", { name: "继续原任务" });expect(resume.hasAttribute("disabled")).toBe(true);
    fireEvent.change(screen.getByRole("textbox", { name: "补充给本次任务的答复" }), { target: { value: "保留两天航程" } });
    fireEvent.click(resume);await waitFor(() => expect(api.writingAgent.control).toHaveBeenCalledWith(id, id, id, "resume", 2, expect.any(String), "保留两天航程"));
    view.unmount();vi.mocked(api.writingAgent.list).mockResolvedValue([run("PAUSED_BUDGET")]);mount();
    await screen.findByText("原任务已达到预算上限。继续创作需要新建并确认预算，不会自动增加原额度。");expect(screen.queryByRole("button", { name: "继续原任务" })).toBeNull();
  });
  it.each([["zh-TW", "世界人物"], ["en", "World character"]])("renders source types in %s", async (locale, translated) => {
    await i18n.changeLanguage(locale);mount();await screen.findByText(translated);
    expect(screen.queryByText("PERSON")).toBeNull();
  });
});
