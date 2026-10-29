import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { waitFor } from "@testing-library/react";
import { api } from "@/lib/api-client";
import { finishAiQuote, pendingAiQuote } from "@/lib/ai-quote-store";
import { quoteFixture } from "@/test/ai-model-fixtures";

const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
describe("AI quote client", () => {
  beforeEach(() => localStorage.setItem("token", "fixture-user-token"));
  afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });
  it("waits for confirmation and retries the identical action with its original idempotency key", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(response({ code: "AI_QUOTE_REQUIRED" }, 428))
      .mockResolvedValueOnce(response(quoteFixture())).mockResolvedValueOnce(response({ content: "generated" }));
    vi.stubGlobal("fetch", fetcher);
    const result = api.ai.chat([{ role: "user", content: "hello" }], "ignored-legacy-id", {});
    await waitFor(() => expect(pendingAiQuote()).not.toBeNull());
    expect(fetcher).toHaveBeenCalledTimes(2);
    finishAiQuote(pendingAiQuote()!, "quote-one");
    await expect(result).resolves.toMatchObject({ content: "generated" });
    const original = fetcher.mock.calls[0][1] as RequestInit;
    const confirmed = fetcher.mock.calls[2][1] as RequestInit;
    expect(confirmed.body).toEqual(original.body);
    expect(new Headers(confirmed.headers).get("Idempotency-Key")).toBe(new Headers(original.headers).get("Idempotency-Key"));
    expect(new Headers(confirmed.headers).get("X-AI-Quote-Id")).toBe("quote-one");
  });
  it("cancelling never submits the chargeable action", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(response({ code: "AI_QUOTE_REQUIRED" }, 428)).mockResolvedValueOnce(response(quoteFixture()));
    vi.stubGlobal("fetch", fetcher);
    const result = api.ai.chat([{ role: "user", content: "hello" }], "model", {});
    await waitFor(() => expect(pendingAiQuote()).not.toBeNull());
    const rejected = expect(result).rejects.toThrow("AI_QUOTE_CANCELLED");
    finishAiQuote(pendingAiQuote()!); await rejected;
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("a free action never opens the quote prompt", async () => {
    const fetcher = vi.fn().mockResolvedValue(response({ content: "free" })); vi.stubGlobal("fetch", fetcher);
    await expect(api.ai.chat([{ role: "user", content: "hello" }], "model", {})).resolves.toMatchObject({ content: "free" });
    expect(pendingAiQuote()).toBeNull(); expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("settlement reuses the receipt body, quote and request key without quoting again", async () => {
    const fetcher = vi.fn().mockResolvedValue(response({ content: "retained" })); vi.stubGlobal("fetch", fetcher);
    const receipt = { id: "original", target: "/v1/ai/chat", body: { messages: [{ role: "user", content: "original" }] }, requestKey: "original-key", quoteId: "original-quote", label: "Original" };
    await expect(api.modelConfig.settle(receipt)).resolves.toMatchObject({ content: "retained" });
    expect(fetcher).toHaveBeenCalledTimes(1); const init = fetcher.mock.calls[0][1] as RequestInit;
    expect(init.body).toBe(JSON.stringify(receipt.body)); expect(new Headers(init.headers).get("Idempotency-Key")).toBe("original-key");
    expect(new Headers(init.headers).get("X-AI-Quote-Id")).toBe("original-quote"); expect(pendingAiQuote()).toBeNull();
  });

  it("recovers legacy refinement results returned as plain text", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response("Original refined text", { headers: { "Content-Type": "text/plain" } })); vi.stubGlobal("fetch", fetcher);
    await expect(api.modelConfig.settle({ id: "original", target: "/v1/story-cards/story/refine", body: { text: "old" }, requestKey: "original-key", quoteId: "original-quote", label: "Story refinement" })).resolves.toBe("Original refined text");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

});
