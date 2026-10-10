import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MaterialUpload from "./MaterialUpload";

const mocks = vi.hoisted(() => ({ upload: vi.fn(), getUploadStatus: vi.fn(), statuses: vi.fn() }));
vi.mock("@/lib/api-client", async importOriginal => ({ ...(await importOriginal<typeof import("@/lib/api-client")>()), api: { materials: mocks, evidence: mocks } }));
const completed = { id: "job", fileName: "原文.txt", status: "completed", materialId: "source" };
beforeEach(() => {
  vi.resetAllMocks(); sessionStorage.clear();
  mocks.getUploadStatus.mockResolvedValue(completed);
  mocks.statuses.mockResolvedValue([{ materialId: "source", review: "pending", basic: "WITHHELD", semantic: "NOT_ENABLED" }]);
});
afterEach(cleanup);
async function selectFile(container: HTMLElement) {
  const file = new File(["原文"], "原文.txt", { type: "" });
  Object.defineProperty(file, "arrayBuffer", { value: async () => new TextEncoder().encode("原文").buffer });
  fireEvent.change(container.querySelector("input[type=file]")!, { target: { files: [file] } });
  await waitFor(() => expect((screen.getByRole("button", { name: "开始上传" }) as HTMLButtonElement).disabled).toBe(false));
}
describe("TXT import intent and independent processing states", () => {
  it("blocks delayed double submission and reuses the original intent after an unknown response", async () => {
    let reject!: (error: Error) => void;
    mocks.upload.mockImplementationOnce(() => new Promise((_resolve, fail) => { reject = fail; })).mockResolvedValue(completed);
    const view = render(<MaterialUpload />); await selectFile(view.container);
    const submit = screen.getByRole("button", { name: "开始上传" });
    fireEvent.click(submit); fireEvent.click(submit); expect(mocks.upload).toHaveBeenCalledTimes(1);
    await act(async () => reject(new Error("响应丢失")));
    fireEvent.click(screen.getByRole("button", { name: "重试上传" }));
    await waitFor(() => expect(screen.getByRole("status").textContent).toContain("待审核"));
    expect(mocks.upload.mock.calls[0][1]).toBe(mocks.upload.mock.calls[1][1]);
    expect(screen.getByRole("status").textContent).toContain("暂不可检索");
    expect((screen.getByRole("button", { name: "开始上传" }) as HTMLButtonElement).disabled).toBe(true);
  });
  it("recovers a pending intent after remount but permits a new intent after confirmed completion", async () => {
    mocks.upload.mockRejectedValueOnce(new Error("响应丢失")).mockResolvedValue(completed);
    let view = render(<MaterialUpload />); await selectFile(view.container);
    fireEvent.click(screen.getByRole("button", { name: "开始上传" }));
    await screen.findByRole("alert"); const first = mocks.upload.mock.calls[0][1];
    view.unmount(); view = render(<MaterialUpload />); await selectFile(view.container);
    fireEvent.click(screen.getByRole("button", { name: "开始上传" }));
    await screen.findByRole("status"); expect(mocks.upload.mock.calls[1][1]).toBe(first);
    view.unmount(); view = render(<MaterialUpload />); await selectFile(view.container);
    fireEvent.click(screen.getByRole("button", { name: "开始上传" }));
    await waitFor(() => expect(mocks.upload).toHaveBeenCalledTimes(3));
    expect(mocks.upload.mock.calls[2][1]).not.toBe(first);
  });
  it("keeps a completed upload visible when processing-state lookup fails", async () => {
    mocks.upload.mockResolvedValue(completed); mocks.statuses.mockRejectedValue(new Error("状态暂不可用"));
    const view = render(<MaterialUpload />); await selectFile(view.container);
    fireEvent.click(screen.getByRole("button", { name: "开始上传" }));
    await screen.findByText("加载失败");
    expect(screen.getByText(/解析成功/)).toBeTruthy();
    expect((screen.getByRole("button", { name: "重试上传" }) as HTMLButtonElement).disabled).toBe(true);
  });
});
