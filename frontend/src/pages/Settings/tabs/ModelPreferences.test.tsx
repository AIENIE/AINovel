import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import ModelPreferences from "./ModelPreferences";
import { api } from "@/lib/api-client";

vi.mock("./AiFeaturePreferences", () => ({ default: () => <div>选型</div> }));
vi.mock("@/lib/api-client", async importOriginal => ({ ...await importOriginal<typeof import("@/lib/api-client")>(), api: { v2: { models: { list: vi.fn(), usageSummary: vi.fn(), usageDetails: vi.fn() } } } }));

describe("model usage reads", () => {
  it("shows a read failure separately from a successfully loaded empty history", async () => {
    vi.mocked(api.v2.models.list).mockResolvedValue([]);
    vi.mocked(api.v2.models.usageSummary).mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValue({ totalCalls: 0, totalCost: 0, totalInputTokens: 0, totalOutputTokens: 0 });
    vi.mocked(api.v2.models.usageDetails).mockResolvedValue([]);
    render(<ModelPreferences />);
    await screen.findByRole("alert");
    expect(screen.getAllByText("—")).toHaveLength(4);
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
    expect(screen.getAllByText("0")).toHaveLength(4);
  });
});
