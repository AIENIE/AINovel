import { beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AiQuotePrompt } from "./AiQuotePrompt";
import { pendingAiQuote, requestAiQuote } from "@/lib/ai-quote-store";
import { quoteFixture } from "@/test/ai-model-fixtures";
import i18n from "@/i18n";

describe("AI pricing confirmation", () => {
  beforeEach(async () => { cleanup(); await i18n.changeLanguage("en"); });
  it("refreshes changed plans before confirmation and remembers the selected preset", async () => {
    render(<AiQuotePrompt />);
    const initial = quoteFixture();
    const next = { ...initial, id: "quote-two", steps: [{ ...initial.steps[0], selected: initial.steps[0].feature.options[1], estimatedCredits: 2 }] };
    const refresh = vi.fn().mockResolvedValue(next), remember = vi.fn().mockResolvedValue(undefined);
    const confirmed = requestAiQuote(initial, refresh, remember);
    await screen.findByRole("dialog");
    fireEvent.change(screen.getByRole("combobox", { name: "General chat" }), { target: { value: "option-b" } });
    await waitFor(() => expect(refresh).toHaveBeenCalledWith({ "generic.chat": "option-b" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Confirm and run" }).hasAttribute("disabled")).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: "Confirm and run" }));
    await expect(confirmed).resolves.toBe("quote-two");
    expect(remember).toHaveBeenCalledWith({ "generic.chat": "option-b" });
    expect(pendingAiQuote()).toBeNull();
  });
  it("offers no model selector for free steps and cancels without confirming", async () => {
    render(<AiQuotePrompt />);const initial = quoteFixture();
    const free = structuredClone(initial.steps[0]);free.feature.definition.key = "plot.diagnose";free.feature.definition.en = "Free check";
    free.feature.billingEnabled = false;free.selected.billingEnabled = false;free.step.featureKey = "plot.diagnose";
    initial.steps.push(free);
    const pending = requestAiQuote(initial, vi.fn(), vi.fn()); const rejected = expect(pending).rejects.toThrow("AI_QUOTE_CANCELLED");
    await screen.findByRole("dialog");expect(screen.getAllByRole("combobox")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));await rejected;
  });
  it("renews an expired quote and requires a second confirmation before executing", async () => {
    render(<AiQuotePrompt />); const initial = quoteFixture(); initial.expiresAt = new Date(Date.now() - 1).toISOString();
    const fresh = quoteFixture(); fresh.id = "fresh-quote"; const refresh = vi.fn().mockResolvedValue(fresh), remember = vi.fn();
    const confirmed = requestAiQuote(initial, refresh, remember); await screen.findByRole("dialog");
    fireEvent.click(screen.getByRole("button", { name: "Confirm and run" }));
    await screen.findByText("The quote expired and has been refreshed. Review it and confirm again.");
    expect(pendingAiQuote()).not.toBeNull(); expect(remember).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Confirm and run" })); await expect(confirmed).resolves.toBe("fresh-quote");
  });

  it.each([
    ["zh-CN", "UP_TO_12", "最多 12 次调用", "取消"],
    ["zh-TW", "UP_TO_3", "最多 3 次呼叫", "取消"],
    ["en", "UP_TO_4", "Up to 4 calls", "Cancel"],
  ])("shows a bounded agent call count in %s", async (locale, calls, label, cancel) => {
    await i18n.changeLanguage(locale);render(<AiQuotePrompt />);
    const initial = quoteFixture();initial.steps[0].step.calls = calls;
    const pending = requestAiQuote(initial, vi.fn(), vi.fn());const rejected = expect(pending).rejects.toThrow("AI_QUOTE_CANCELLED");
    await screen.findByText(new RegExp(label));expect(screen.queryByText(new RegExp(`aiModels.calls.${calls}`))).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: cancel }));await rejected;
  });

});
