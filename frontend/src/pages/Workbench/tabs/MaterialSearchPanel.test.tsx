import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import MaterialSearchPanel from "./MaterialSearchPanel";

const mocks = vi.hoisted(() => ({ settings: vi.fn(), list: vi.fn(), search: vi.fn(), open: vi.fn() }));
vi.mock("@/lib/api-client", async importOriginal => ({
  ...(await importOriginal<typeof import("@/lib/api-client")>()),
  api: { evidence: { settings: mocks.settings, search: mocks.search, open: mocks.open }, materials: { list: mocks.list } },
}));
const hit = { chunkId: "chunk", materialId: "source", revisionId: "revision", sourceVersion: 1,
  title: "雨夜候桥", text: "雨夜，蓝灯照见行人。", start: 0, end: 11, reasons: ["中文全文"], rank: 1 };
beforeEach(() => {
  vi.resetAllMocks(); mocks.list.mockResolvedValue([]);
  mocks.settings.mockResolvedValue({ version: 0, semanticProfile: "basic", rerank: false, hints: false, checks: false, bindings: [] });
});
afterEach(cleanup);

it("labels inspiration as related sources and clears old results when the scope changes", async () => {
  mocks.search.mockResolvedValue({ mode: "inspiration", scope: "bound", items: [hit], degradation: ["基础检索"] });
  render(<MaterialSearchPanel storyId="story" />);
  fireEvent.change(screen.getByLabelText("查找目的"), { target: { value: "inspiration" } });
  fireEvent.change(screen.getByLabelText("查找内容"), { target: { value: "雨夜" } });
  fireEvent.click(screen.getByRole("button", { name: "查找" }));
  await screen.findByText("雨夜候桥");
  expect(screen.getByText(/相近素材 ·/)).toBeDefined();
  expect(screen.getByText(/不作为重复合并或事实确认/)).toBeDefined();
  expect(screen.queryByRole("button", { name: "审阅合并" })).toBeNull();
  fireEvent.change(screen.getByLabelText("资料范围"), { target: { value: "personal" } });
  expect(screen.queryByText("雨夜候桥")).toBeNull();
  expect(screen.queryByText("基础检索")).toBeNull();
});

it("cancels the previous mode observation and ignores its late response", async () => {
  let complete!: (value: unknown) => void;
  mocks.search.mockImplementationOnce(() => new Promise(resolve => { complete = resolve; }));
  render(<MaterialSearchPanel storyId="story" />);
  fireEvent.change(screen.getByLabelText("查找内容"), { target: { value: "雨夜" } });
  fireEvent.click(screen.getByRole("button", { name: "查找" }));
  await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(1));
  const signal = mocks.search.mock.calls[0][1] as AbortSignal;
  fireEvent.change(screen.getByLabelText("查找目的"), { target: { value: "inspiration" } });
  expect(signal.aborted).toBe(true);
  await act(async () => complete({ mode: "fact", scope: "bound", items: [hit], degradation: [] }));
  expect(screen.queryByText("雨夜候桥")).toBeNull();
});

it("clears the previous source preview for a new query and ignores its late response", async () => {
  let complete!: (value: unknown) => void;
  mocks.search.mockResolvedValueOnce({ mode: "fact", scope: "bound", items: [hit], degradation: [] })
    .mockResolvedValueOnce({ mode: "fact", scope: "bound", items: [{ ...hit, chunkId: "new", title: "新查询资料" }], degradation: [] });
  mocks.open.mockResolvedValueOnce({ ...hit, title: "已打开的旧原文" })
    .mockImplementationOnce(() => new Promise(resolve => { complete = resolve; }));
  render(<MaterialSearchPanel storyId="story" />);
  fireEvent.change(screen.getByLabelText("查找内容"), { target: { value: "雨夜" } });
  fireEvent.click(screen.getByRole("button", { name: "查找" }));
  await screen.findByText("雨夜候桥");
  fireEvent.click(screen.getByRole("button", { name: "打开原文" }));
  await screen.findByText("已打开的旧原文");
  fireEvent.click(screen.getByRole("button", { name: "打开原文" }));
  await waitFor(() => expect(mocks.open).toHaveBeenCalledTimes(2));
  fireEvent.change(screen.getByLabelText("查找内容"), { target: { value: "蓝灯" } });
  fireEvent.click(screen.getByRole("button", { name: "查找" }));
  await screen.findByText("新查询资料");
  expect(screen.queryByText("已打开的旧原文")).toBeNull();
  await act(async () => complete({ ...hit, title: "晚返回的旧原文" }));
  expect(screen.queryByText("晚返回的旧原文")).toBeNull();
});
