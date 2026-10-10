import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MaterialSourceTools } from "./MaterialSourceTools";

const mocks = vi.hoisted(() => ({ tasks: vi.fn(), annotations: vi.fn(), resumeSemantic: vi.fn() }));
vi.mock("@/lib/api-client", async importOriginal => ({ ...(await importOriginal<typeof import("@/lib/api-client")>()), api: { evidence: mocks } }));
vi.mock("@/components/materials/MaterialRevisionHistory", () => ({ MaterialRevisionHistory: () => null }));
const source = { materialId: "source", revisionId: "revision", version: 2, saved: "SAVED", basic: "SEARCHABLE", review: "approved", semantic: "qwen-standard-1024-cp-v1:RECONCILIATION_REQUIRED" };

beforeEach(() => { vi.resetAllMocks(); mocks.tasks.mockResolvedValue([]); mocks.annotations.mockResolvedValue([]); });
afterEach(cleanup);

it("prevents double resume and keeps the original intent after an unknown response", async () => {
  let reject!: (reason: Error) => void;
  mocks.resumeSemantic.mockImplementationOnce(() => new Promise((_, no) => { reject = no; })).mockResolvedValue({ status: "QUEUED" });
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(<MaterialSourceTools materials={[{ id: "source", title: "验收资料", content: "原文", type: "text", status: "approved", tags: [] }]} statuses={[source]} refresh={refresh} />);
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "source" } });
  const resume = await screen.findByRole("button", { name: "恢复 标准 索引任务" });
  fireEvent.click(resume); fireEvent.click(resume);
  expect((resume as HTMLButtonElement).disabled).toBe(true); expect(mocks.resumeSemantic).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error("恢复结果未知，请沿原任务重试")));
  expect(screen.getByRole("alert").textContent).toContain("恢复结果未知"); expect(refresh).not.toHaveBeenCalled();
  fireEvent.click(resume);
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  expect(mocks.resumeSemantic).toHaveBeenCalledTimes(2);
  expect(mocks.resumeSemantic.mock.calls[1]).toEqual(mocks.resumeSemantic.mock.calls[0]);
  expect(mocks.resumeSemantic.mock.calls[0][2]).toMatchObject({ expectedVersion: 2 });
});
