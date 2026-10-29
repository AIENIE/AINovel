import { beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AiPendingPayments } from "./AiPendingPayments";
import { api } from "@/lib/api-client";
import i18n from "@/i18n";

vi.mock("@/lib/api-client", () => ({ isApiError: () => false, api: { modelConfig: { pending: vi.fn(), settle: vi.fn() } } }));
describe("pending payment recovery", () => {
  beforeEach(async () => { cleanup(); vi.clearAllMocks(); await i18n.changeLanguage("en"); });
  it("loads a retained operation after refresh and displays its original result on settlement", async () => {
    const receipt = { id: "original", target: "/v1/ai/chat", body: { messages: [] }, requestKey: "same-request", quoteId: "original-quote", label: "Original plan" };
    vi.mocked(api.modelConfig.pending).mockResolvedValue([receipt]); vi.mocked(api.modelConfig.settle).mockResolvedValue({ content: "Retained result" });
    render(<AiPendingPayments />);
    fireEvent.click(await screen.findByRole("button", { name: "Resume the original operation after topping up" }));
    await waitFor(() => expect(api.modelConfig.settle).toHaveBeenCalledExactlyOnceWith(receipt));
    const output = await screen.findByRole("textbox", { name: "Recovered original result" });
    expect((output as HTMLTextAreaElement).value).toBe("Retained result"); expect((output as HTMLTextAreaElement).readOnly).toBe(true);
    expect(screen.queryByRole("button", { name: "Resume the original operation after topping up" })).toBeNull();
  });
});
