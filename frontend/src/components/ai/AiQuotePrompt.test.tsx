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
    const next = { ...initial, id: "quote-two", roles: [{ ...initial.roles[0], selected: initial.roles[0].role.options[1], estimatedCredits: 2 }] };
    const refresh = vi.fn().mockResolvedValue(next), remember = vi.fn().mockResolvedValue(undefined);
    const confirmed = requestAiQuote(initial, refresh, remember);
    await screen.findByRole("dialog");
    fireEvent.change(screen.getByRole("combobox", { name: "General chat" }), { target: { value: "option-b" } });
    await waitFor(() => expect(refresh).toHaveBeenCalledWith({ "role.discussion": "option-b" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Confirm and run" }).hasAttribute("disabled")).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: "Confirm and run" }));
    await expect(confirmed).resolves.toBe("quote-two");
    expect(remember).toHaveBeenCalledWith({ "role.discussion": "option-b" });
    expect(pendingAiQuote()).toBeNull();
  });
  it("offers no model selector for free steps and cancels without confirming", async () => {
    render(<AiQuotePrompt />);const initial = quoteFixture();
    const free = structuredClone(initial.roles[0]);free.role.definition.key = "role.plot_check";free.role.definition.en = "Free check";
    free.role.billingEnabled = false;free.selected.billingEnabled = false;
    initial.roles.push(free);
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
    const initial = quoteFixture();initial.roles[0].estimates[0].calls = calls;
    const pending = requestAiQuote(initial, vi.fn(), vi.fn());const rejected = expect(pending).rejects.toThrow("AI_QUOTE_CANCELLED");
    await screen.findByText(new RegExp(label));expect(screen.queryByText(new RegExp(`aiModels.calls.${calls}`))).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: cancel }));await rejected;
  });

  it.each([
    ["zh-CN", "本职责估算 6 积分", "估算合计 6 项目积分", "单次估算 6 积分", "最多 6 次调用", "取消"],
    ["zh-TW", "本職責估算 6 積分", "估算合計 6 專案積分", "單次估算 6 積分", "最多 6 次呼叫", "取消"],
    ["en", "Estimated for this responsibility: 6 credits", "Estimated total: 6 project credits", "About 6 credits per call", "Up to 6 calls", "Cancel"],
  ])("labels the server's six-call estimate as a combined amount in %s", async (locale, stepLabel, totalLabel, oldLabel, callsLabel, cancel) => {
    await i18n.changeLanguage(locale);render(<AiQuotePrompt />);
    const initial = quoteFixture();initial.estimatedCredits = 6;
    initial.roles[0].estimates[0] = { ...initial.roles[0].estimates[0], calls: "UP_TO_6", estimatedInput: 24000, estimatedOutput: 4096 };
    initial.roles[0].selected = { ...initial.roles[0].selected, pricingMode: "TOKENS", fixedCredits: null, inputPer1k: 0.01, outputPer1k: 0.01 };
    initial.roles[0].estimatedCredits = 6;
    const pending = requestAiQuote(initial, vi.fn(), vi.fn());const rejected = expect(pending).rejects.toThrow("AI_QUOTE_CANCELLED");

    await screen.findByText(new RegExp(stepLabel));expect(screen.getByText(totalLabel)).toBeTruthy();
    expect(screen.getByText(new RegExp(callsLabel))).toBeTruthy();expect(screen.queryByText(new RegExp(oldLabel))).toBeNull();
    expect(initial.roles[0].estimatedCredits).toBe(6);expect(initial.estimatedCredits).toBe(6);
    fireEvent.click(screen.getByRole("button", { name: cancel }));await rejected;
  });
  it.each([
    ["zh-CN", "本职责估算 1 积分", "估算合计 1 项目积分", "取消"],
    ["zh-TW", "本職責估算 1 積分", "估算合計 1 專案積分", "取消"],
    ["en", "Estimated for this responsibility: 1 credits", "Estimated total: 1 project credits", "Cancel"],
  ])("keeps existing single-call and nonnumeric legacy estimates unchanged in %s", async (locale, stepLabel, totalLabel, cancel) => {
    await i18n.changeLanguage(locale);
    for (const calls of ["ONE", "UP_TO_TWO"]) {
      render(<AiQuotePrompt />);const initial = quoteFixture();initial.roles[0].estimates[0].calls = calls;
      const pending = requestAiQuote(initial, vi.fn(), vi.fn());const rejected = expect(pending).rejects.toThrow("AI_QUOTE_CANCELLED");
      await screen.findByText(new RegExp(stepLabel));expect(screen.getByText(totalLabel)).toBeTruthy();
      fireEvent.click(screen.getByRole("button", { name: cancel }));await rejected;cleanup();
    }
  });

});
