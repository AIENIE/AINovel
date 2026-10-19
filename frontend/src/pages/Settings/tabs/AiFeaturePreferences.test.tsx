import { beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import AiFeaturePreferences from "./AiFeaturePreferences";
import { api } from "@/lib/api-client";
import { featureFixture } from "@/test/ai-model-fixtures";
import i18n from "@/i18n";

vi.mock("@/lib/api-client", () => ({ isApiError: () => false, api: { modelConfig: { features: vi.fn(), pending: vi.fn(), preference: vi.fn() } } }));
describe("feature model preferences", () => {
  beforeEach(() => { cleanup(); vi.clearAllMocks(); vi.mocked(api.modelConfig.pending).mockResolvedValue([]); });
  it.each([["en", "General chat"], ["zh-CN", "通用对话"], ["zh-TW", "通用對話"]])("shows paid choices and a read-only free option in %s", async (language, label) => {
    await i18n.changeLanguage(language);
    const paid = featureFixture(); paid.preferredOptionId = "removed"; paid.preferenceInvalid = true;
    const free = featureFixture(); free.definition = { ...free.definition, key: "free.check", zh: "免费检查", tw: "免費檢查", en: "Free check" }; free.billingEnabled = false;
    free.options = [{ ...free.options[0], billingEnabled: false }];
    vi.mocked(api.modelConfig.features).mockResolvedValue([paid, free]);
    vi.mocked(api.modelConfig.preference).mockResolvedValue({ ...paid, preferenceInvalid: false, preferredOptionId: "option-b" });
    render(<AiFeaturePreferences />);
    const chooser = await screen.findByRole("combobox", { name: label });
    expect((chooser as HTMLSelectElement).value).toBe(""); expect(screen.getAllByRole("combobox")).toHaveLength(1);
    fireEvent.change(chooser, { target: { value: "option-b" } });
    await waitFor(() => expect(api.modelConfig.preference).toHaveBeenCalledWith("generic.chat", "option-b"));
    await waitFor(() => expect((screen.getByRole("combobox", { name: label }) as HTMLSelectElement).value).toBe("option-b"));
  });
});
