import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { WorldAssistantPanel } from "./WorldAssistantPanel";
import type { WorldEditorState } from "../useWorldDiscussion";
import type { WorldTurn } from "@/lib/api/domains/world-assistant";

afterEach(() => { cleanup(); sessionStorage.clear(); vi.restoreAllMocks(); });

it("uses each real topic identity for navigation and filters while retaining per-topic input and original rename titles", async () => {
  const topics = [
    { id: "a1111111-1111-1111-1111-111111111111", worldId: "world", title: "世界概览整合", createdAt: "2026-10-27T08:00:00Z", updatedAt: "2026-10-27T08:01:00Z" },
    { id: "b2222222-2222-2222-2222-222222222222", worldId: "world", title: "世界概览整合", createdAt: "2026-10-27T08:00:00Z", updatedAt: "2026-10-27T08:01:00Z" },
  ];
  vi.spyOn(api.worldAssistant, "sessions").mockResolvedValue(topics);
  const session = vi.spyOn(api.worldAssistant, "session").mockImplementation(async (_world, id) => ({
    id: id || topics[0].id, worldId: "world", title: "世界概览整合", turns: [], hasMore: false, nextBefore: null,
  }));
  const proposals = vi.spyOn(api.worldAssistant, "proposals").mockResolvedValue({ items: [], hasMore: false, nextBefore: null });
  const editor = { draft: { revision: 1 }, canAct: () => true } as WorldEditorState;
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><WorldAssistantPanel world="world" editor={editor} single={null} onSingleClose={() => {}} onDirty={() => {}} /></QueryClientProvider>);
  const nav = await screen.findByRole("navigation", { name: "讨论话题" });
  const first = await within(nav).findByRole("button", { name: /世界概览整合.*创建于.*编号 a1111111/ });
  const second = within(nav).getByRole("button", { name: /世界概览整合.*创建于.*编号 b2222222/ });
  await waitFor(() => expect(first.getAttribute("aria-current")).toBe("true"));
  fireEvent.change(screen.getByLabelText("你的要求"), { target: { value: "话题一还没有发送的要求" } });
  fireEvent.click(second);await waitFor(() => expect(second.getAttribute("aria-current")).toBe("true"));
  expect((screen.getByLabelText("你的要求") as HTMLTextAreaElement).value).toBe("");
  expect(session.mock.calls.some(call => call[1] === topics[1].id)).toBe(true);
  fireEvent.click(first);await waitFor(() => expect((screen.getByLabelText("你的要求") as HTMLTextAreaElement).value).toBe("话题一还没有发送的要求"));
  fireEvent.click(screen.getByRole("button", { name: "话题改名" }));
  expect((screen.getByRole("textbox", { name: "话题名称" }) as HTMLInputElement).value).toBe("世界概览整合");
  fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "取消" }));
  await act(async () => { fireEvent.mouseDown(screen.getByRole("tab", { name: "提案库" }), { button: 0, ctrlKey: false }); });
  const filter = await screen.findByRole("combobox", { name: "筛选来源话题" });
  expect(within(filter).getByRole("option", { name: /编号 b2222222/ }).getAttribute("value")).toBe(topics[1].id);
  fireEvent.change(filter, { target: { value: topics[1].id } });
  await waitFor(() => expect(proposals).toHaveBeenLastCalledWith("world", expect.objectContaining({ sessionId: topics[1].id }), expect.any(AbortSignal)));
});

it("reads persisted failure details after refresh without rerunning generation and keeps unknown fees pending", async () => {
  const topic = { id: "topic", worldId: "world", title: "世界概览整合", createdAt: "2026-10-27T08:00:00Z", updatedAt: "2026-10-27T08:00:00Z" };
  const base: WorldTurn = { id: "failed", operationId: "original-operation", status: "FAILED", resultKind: null, instruction: "概览整合要求", answer: null, intent: "OVERVIEW", suggestions: [], proposalIds: [], parentProposalId: null, sources: [], warnings: [], conflicts: [], usage: { modelCalls: 1, inputTokens: 13000, outputTokens: 8192, cost: 20 }, createdAt: topic.createdAt,
    failure: { code: "WORLD_ASSISTANT_REASONING_OUTPUT_LIMIT", phase: "plan", finishReason: "length", reasoningTokens: 8192, completionTokens: 8192, usageAuthoritative: true } };
  vi.spyOn(api.worldAssistant, "sessions").mockResolvedValue([topic]);
  const session = vi.spyOn(api.worldAssistant, "session").mockResolvedValue({ id: "topic", worldId: "world", title: topic.title, turns: [base], hasMore: false, nextBefore: null });
  const submit = vi.spyOn(api.worldAssistant, "submit"), single = vi.spyOn(api.worldAssistant, "single");
  const mountPanel = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WorldAssistantPanel world="world" editor={{ draft: { revision: 1 }, canAct: () => true } as WorldEditorState} single={null} onSingleClose={() => {}} onDirty={() => {}} /></QueryClientProvider>);
  const first = mountPanel();expect(await screen.findByText(/本次输出额度全部用于思考/)).toBeTruthy();
  fireEvent.click(await screen.findByText("查看本次返回信息"));expect(screen.getByText("length")).toBeTruthy();expect(screen.getAllByText("8,192")).toHaveLength(2);expect(screen.getByText(/网关已确认记录/)).toBeTruthy();
  expect(screen.getByText("1 次调用 · 20 项目积分")).toBeTruthy();first.unmount();
  mountPanel();expect(await screen.findByText(/本次输出额度全部用于思考/)).toBeTruthy();cleanup();
  session.mockResolvedValue({ id: "topic", worldId: "world", title: topic.title, turns: [{ ...base, id: "pending", status: "RECOVERY_REQUIRED", usage: { modelCalls: 0, inputTokens: 0, outputTokens: 0, cost: 0 }, failure: { code: "AI_RESULT_RECONCILIATION_REQUIRED", phase: "plan", finishReason: null, reasoningTokens: null, completionTokens: null, usageAuthoritative: null } }], hasMore: false, nextBefore: null });
  mountPanel();expect(await screen.findByText(/结果仍待对账|结果尚待对账/)).toBeTruthy();
  expect(screen.queryByText(/0 项目积分/)).toBeNull();expect(screen.queryByText(/本次输出额度全部用于思考/)).toBeNull();
  fireEvent.click(await screen.findByText("查看本次返回信息"));expect(screen.getAllByText("未记录")).toHaveLength(3);expect(screen.getByText(/缺失值不按零处理/)).toBeTruthy();
  expect(submit).not.toHaveBeenCalled();expect(single).not.toHaveBeenCalled();
});
