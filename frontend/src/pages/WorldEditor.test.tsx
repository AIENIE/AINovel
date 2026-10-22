import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { api, ApiError } from "@/lib/api-client";
import type { AiOperationProgress, WorldDetail, WorldModuleDefinition } from "@/types";
import { WORLD_UPDATED } from "@/lib/world-operation-events";
import { refreshTrackedAiOperation, retryTrackedAiOperation } from "@/lib/ai-operation-store";
import WorldEditor from "./WorldEditor";

const state = vi.hoisted(() => ({ task: null as AiOperationProgress | null, listeners: new Set<() => void>() }));
vi.mock("@/contexts/auth-state", () => ({ useAuth: () => ({ refreshProfile: vi.fn() }) }));
vi.mock("@/lib/ai-operation-store", async () => {
  const { useSyncExternalStore } = await import("react");
  return { useTrackedAiOperation: () => useSyncExternalStore(listener => { state.listeners.add(listener); return () => { state.listeners.delete(listener); }; }, () => state.task), runTrackedAiOperation: vi.fn(async accepted => { await accepted; }), retryTrackedAiOperation: vi.fn(async () => {}), refreshTrackedAiOperation: vi.fn(async () => {}) };
});
const definitions: WorldModuleDefinition[] = [{ key: "geography", label: "地理环境", description: "", fields: [
  { key: "terrain", label: "地形地貌", type: "textarea" }, { key: "climate", label: "气候", type: "textarea" },
] }, { key: "society", label: "社会体系", description: "", fields: [{ key: "politics", label: "政体", type: "textarea" }] }];
const world = (revision = 1): WorldDetail => ({ id: "world-a", revision, name: "海上城", tagline: "", themes: [], creativeIntent: "", notes: "", status: "draft", version: "0.1", updatedAt: "", modules: [{ key: "geography", fields: { terrain: "群岛", climate: "" } }] });
const operation = (status: AiOperationProgress["status"], errorCode?: string): AiOperationProgress => ({ id: "op-a", operationType: "AINOVEL_LONG_TASK", scopeType: "WORLD", scopeId: "world-a", status, errorCode, completedSteps: 1, totalSteps: 2, remainingSteps: 1, currentStepOutputTokens: 0, outputTokensEstimated: false, attemptCount: 1 });
const renderEditor = () => {
  const router = createMemoryRouter([{ path: "/world-editor", element: <WorldEditor /> }, { path: "/worlds", element: <p>世界列表页</p> }], { initialEntries: ["/world-editor?id=world-a"] });
  render(<RouterProvider router={router} />); return router;
};
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(yes => { resolve = yes; }); return { promise, resolve }; }

beforeEach(() => {
  state.task = null;
  vi.spyOn(api.worlds, "getDetail").mockResolvedValue(world());
  vi.spyOn(api.worlds, "getDefinitions").mockResolvedValue(definitions);
  vi.spyOn(api.worlds, "generationStatus").mockResolvedValue({ queue: [] });
  vi.spyOn(api.aiOperations, "active").mockRejectedValue(new ApiError(404, "NOT_FOUND"));
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.clearAllMocks(); });

describe("WorldEditor", () => {
  it.each(["detail", "definitions"])("selects the first tab after asynchronous %s arrives first", async first => {
    const detail = deferred<WorldDetail>(), defs = deferred<WorldModuleDefinition[]>();
    vi.mocked(api.worlds.getDetail).mockReturnValue(detail.promise);
    vi.mocked(api.worlds.getDefinitions).mockReturnValue(defs.promise);
    renderEditor();
    await act(async () => { if (first === "detail") detail.resolve(world()); else defs.resolve(definitions); });
    await act(async () => { if (first === "detail") defs.resolve(definitions); else detail.resolve(world()); });
    expect((await screen.findByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("群岛");
    expect(screen.getByRole("tab", { name: "地理环境" }).getAttribute("aria-selected")).toBe("true");
  });

  it("requires explicit save before generation or publishing without silently saving", async () => {
    const update = vi.spyOn(api.worlds, "update");
    const generate = vi.spyOn(api.worlds, "startGenerateModule");
    const preview = vi.spyOn(api.worlds, "publishPreview");
    renderEditor();
    fireEvent.change(await screen.findByLabelText("地形地貌"), { target: { value: "未保存的群岛" } });
    fireEvent.click(screen.getByRole("button", { name: "AI 补全空字段" }));
    expect(screen.getByRole("alert").textContent).toContain("请先保存");
    fireEvent.click(screen.getByRole("button", { name: "预检/发布" }));
    expect(update).not.toHaveBeenCalled(); expect(generate).not.toHaveBeenCalled(); expect(preview).not.toHaveBeenCalled();
  });

  it("protects reload and route changes and keeps the editor open when save fails", async () => {
    const update = vi.spyOn(api.worlds, "update").mockRejectedValueOnce(new Error("offline")).mockImplementationOnce(async (_id, draft) => ({ ...draft, revision: 2 }));
    const router = renderEditor();
    fireEvent.change(await screen.findByLabelText("地形地貌"), { target: { value: "保留文字" } });
    const reload = new Event("beforeunload", { cancelable: true }); window.dispatchEvent(reload);
    expect(reload.defaultPrevented).toBe(true);
    fireEvent.click(screen.getByRole("link", { name: "返回世界列表" }));
    fireEvent.click(await screen.findByRole("button", { name: "取消" }));
    expect(router.state.location.pathname).toBe("/world-editor");
    fireEvent.click(screen.getByRole("link", { name: "返回世界列表" }));
    fireEvent.click(await screen.findByRole("button", { name: "保存并离开" }));
    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole("button", { name: "保存并离开" }).hasAttribute("disabled")).toBe(false));
    expect((screen.getByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("保留文字");
    expect(router.state.location.pathname).toBe("/world-editor");
    fireEvent.click(screen.getByRole("button", { name: "保存并离开" }));
    expect(await screen.findByText("世界列表页")).toBeTruthy();
  });

  it("preserves typing on world task completion and lets the author keep changed fields after comparison", async () => {
    renderEditor();
    fireEvent.change(await screen.findByLabelText("地形地貌"), { target: { value: "我的地形" } });
    vi.mocked(api.worlds.getDetail).mockResolvedValue({ ...world(2), modules: [{ key: "geography", fields: { terrain: "生成的地形", climate: "季风" } }] });
    act(() => window.dispatchEvent(new CustomEvent(WORLD_UPDATED, { detail: { worldId: "world-a", operationId: "op-a" } })));
    fireEvent.click(await screen.findByRole("button", { name: "比较已保存内容" }));
    expect(screen.getByText("生成的地形")).toBeTruthy();
    expect(screen.getByText("地理环境 / 地形地貌")).toBeTruthy();
    expect(screen.queryByText(/"revision"/)).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "保留我的修改" }));
    expect((screen.getByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("我的地形");
    expect((screen.getByLabelText("气候") as HTMLTextAreaElement).value).toBe("季风");
    expect(screen.getByRole("status").textContent).toContain("未保存");
  });

  it("reviews and edits a refinement before adopting it locally, without saving automatically", async () => {
    const update = vi.spyOn(api.worlds, "update");
    const refine = vi.spyOn(api.worlds, "refineField").mockResolvedValue({ result: "山脊环绕的群岛" });
    renderEditor();
    await screen.findByLabelText("地形地貌");
    fireEvent.click(screen.getAllByRole("button", { name: "润色" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "生成候选" }));
    const candidate = await screen.findByLabelText("候选内容（可编辑）");
    expect((screen.getByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("群岛");
    fireEvent.change(candidate, { target: { value: "作者修订后的群岛" } });
    fireEvent.click(screen.getByRole("button", { name: "采纳到当前输入" }));
    expect((screen.getByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("作者修订后的群岛");
    expect(update).not.toHaveBeenCalled();
    expect(refine).toHaveBeenCalledWith("world-a", "geography", "terrain", "群岛", "");
  });

  it("rejects adopting a candidate after task completion changed the original", async () => {
    vi.spyOn(api.worlds, "refineField").mockResolvedValue({ result: "候选地形" });
    renderEditor(); await screen.findByLabelText("地形地貌");
    fireEvent.click(screen.getAllByRole("button", { name: "润色" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "生成候选" }));
    await screen.findByLabelText("候选内容（可编辑）");
    vi.mocked(api.worlds.getDetail).mockResolvedValue(world(2));
    act(() => window.dispatchEvent(new CustomEvent(WORLD_UPDATED, { detail: { worldId: "world-a", operationId: "other-op" } })));
    await waitFor(() => expect(screen.getByRole("button", { name: "采纳到当前输入" }).hasAttribute("disabled")).toBe(true));
    expect(screen.getByText(/原文或世界内容已变化/)).toBeTruthy();
  });

  it.each(["", " \n "])("preserves the source and explains an empty refinement result without retrying (%j)", async result => {
    const refine = vi.spyOn(api.worlds, "refineField").mockResolvedValue({ result });
    const update = vi.spyOn(api.worlds, "update");
    renderEditor(); await screen.findByLabelText("地形地貌");
    fireEvent.click(screen.getAllByRole("button", { name: "润色" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "生成候选" }));
    expect((await screen.findByRole("alert")).textContent).toBe("未获得可用的精修内容，原文已保留。");
    expect((screen.getByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("群岛");
    expect(screen.queryByLabelText("候选内容（可编辑）")).toBeNull();
    expect(screen.queryByRole("button", { name: "采纳到当前输入" })).toBeNull();
    expect(update).not.toHaveBeenCalled();
    expect(refine).toHaveBeenCalledTimes(1);
  });

  it("ends an explicitly rejected failed task without retrying it or immediately generating again", async () => {
    state.task = operation("FAILED", "WORLD_VERSION_CONFLICT");
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const generate = vi.spyOn(api.worlds, "startGenerateModule").mockResolvedValue({ operationId: "new-op" });
    renderEditor(); await screen.findByLabelText("地形地貌");
    expect(screen.queryByRole("button", { name: "恢复原任务" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "结束本次恢复" }));
    expect(retryTrackedAiOperation).not.toHaveBeenCalled(); expect(generate).not.toHaveBeenCalled();
    expect(screen.getByText(/本次恢复已结束/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "AI 补全空字段" }));
    await waitFor(() => expect(generate).toHaveBeenCalledTimes(1));
  });

  it("keeps an uncertain result on its original task and blocks a replacement generation", async () => {
    state.task = operation("RECOVERY_REQUIRED", "AI_RESULT_RECONCILIATION_REQUIRED");
    const generate = vi.spyOn(api.worlds, "startGenerateModule");
    renderEditor(); await screen.findByLabelText("地形地貌");
    expect(screen.queryByRole("button", { name: "结束本次恢复" })).toBeNull();
    expect(screen.getByRole("button", { name: "恢复原任务" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "AI 补全空字段" }));
    expect(generate).not.toHaveBeenCalled();
  });

  it("does not restore an old recovery snapshot after the tracked task has completed", async () => {
    const active = deferred<AiOperationProgress>();
    vi.mocked(api.aiOperations.active).mockReturnValue(active.promise);
    state.task = operation("RECOVERY_REQUIRED");
    renderEditor(); await screen.findByLabelText("地形地貌");
    act(() => { state.task = operation("SUCCEEDED"); state.listeners.forEach(listener => listener()); });
    await act(async () => { active.resolve(operation("RECOVERY_REQUIRED")); });
    act(() => { state.task = null; state.listeners.forEach(listener => listener()); });
    expect(screen.getByRole("region", { name: "世界生成任务" }).textContent).toContain("已完成");
    expect(screen.queryByRole("button", { name: "恢复原任务" })).toBeNull();
  });

  it("keeps the source unchanged and does not show an error when the refinement quote is dismissed", async () => {
    vi.spyOn(api.worlds, "refineField").mockRejectedValue(new Error("AI_QUOTE_CANCELLED"));
    const update = vi.spyOn(api.worlds, "update");
    renderEditor(); await screen.findByLabelText("地形地貌");
    fireEvent.click(screen.getAllByRole("button", { name: "润色" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "生成候选" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "生成候选" }).hasAttribute("disabled")).toBe(false));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByLabelText("候选内容（可编辑）")).toBeNull();
    expect((screen.getByLabelText("地形地貌") as HTMLTextAreaElement).value).toBe("群岛");
    expect(update).not.toHaveBeenCalled();
  });

  it("blocks new generation until a failed active-task lookup is read successfully", async () => {
    vi.mocked(api.aiOperations.active).mockRejectedValueOnce(new ApiError(503, "unavailable")).mockRejectedValue(new ApiError(404, "NOT_FOUND"));
    const generate = vi.spyOn(api.worlds, "startGenerateModule").mockResolvedValue({ operationId: "new-op" });
    renderEditor(); await screen.findByLabelText("地形地貌");
    fireEvent.click(screen.getByRole("button", { name: "AI 补全空字段" }));
    expect(generate).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole("button", { name: "刷新任务状态" }));
    await waitFor(() => expect(screen.queryByText("正在确认已有任务状态…")).toBeNull());
    fireEvent.click(screen.getByRole("button", { name: "AI 补全空字段" }));
    await waitFor(() => expect(generate).toHaveBeenCalledTimes(1));
  });

  it("offers a read-only refresh of the original running task without retrying or replacing it", async () => {
    state.task = operation("RUNNING");
    const generate = vi.spyOn(api.worlds, "startGenerateModule");
    renderEditor(); await screen.findByLabelText("地形地貌");
    fireEvent.click(screen.getByRole("button", { name: "刷新任务状态" }));
    await waitFor(() => expect(refreshTrackedAiOperation).toHaveBeenCalledWith("op-a"));
    expect(retryTrackedAiOperation).not.toHaveBeenCalled();
    expect(generate).not.toHaveBeenCalled();
  });
});
