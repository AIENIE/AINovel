import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MaterialList from "./MaterialList";

const mocks = vi.hoisted(() => ({ list: vi.fn(), statuses: vi.fn(), updateVersioned: vi.fn() }));
vi.mock("@/lib/api-client", async importOriginal => ({ ...(await importOriginal<typeof import("@/lib/api-client")>()), api: { materials: mocks, evidence: mocks } }));
vi.mock("./MaterialSourceTools", () => ({ MaterialSourceTools: () => null }));
const material = { id: "source", title: "验收资料", content: "原文", tags: [], type: "text", status: "approved" };
const queued = { materialId: "source", version: 1, basic: "QUEUED", semantic: "NOT_ENABLED", review: "approved" };
beforeEach(() => { vi.resetAllMocks(); mocks.list.mockResolvedValue([material]); });
afterEach(() => { cleanup(); vi.useRealTimers(); });
describe("material processing feedback", () => {
  it("observes indexing completion without reloading or rewriting the material, then stops polling", async () => {
    mocks.statuses.mockResolvedValueOnce([queued]).mockResolvedValue([{ ...queued, basic: "SEARCHABLE" }]);
    vi.useFakeTimers(); const view = render(<MaterialList />);
    await act(async () => {}); expect(screen.getByText("已保存，等待基础索引")).toBeTruthy();
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(screen.getByText("已保存 · 基础可检索")).toBeTruthy();
    const count = mocks.statuses.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(60000); });
    expect(mocks.statuses).toHaveBeenCalledTimes(count);
    expect(mocks.list).toHaveBeenCalledTimes(1); expect(mocks.updateVersioned).not.toHaveBeenCalled(); view.unmount();
  });
  it("retains saved data when status lookup fails and offers a read-only refresh", async () => {
    mocks.statuses.mockResolvedValueOnce([queued]).mockRejectedValueOnce(new Error("状态读取失败"))
      .mockResolvedValue([{ ...queued, basic: "SEARCHABLE" }]);
    vi.useFakeTimers(); render(<MaterialList />); await act(async () => {}); expect(screen.getByText("已保存，等待基础索引")).toBeTruthy();
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(screen.getByRole("alert")).toBeTruthy(); expect(screen.getByText("验收资料")).toBeTruthy();
    vi.useRealTimers(); fireEvent.click(screen.getByRole("button", { name: "刷新处理状态" }));
    await waitFor(() => expect(screen.getByText("已保存 · 基础可检索")).toBeTruthy());
    expect(mocks.updateVersioned).not.toHaveBeenCalled();
  });
});
