import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import i18n from "@/i18n";
import { api, ApiError } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import ProfilePage from "./ProfilePage";

vi.mock("@/contexts/auth-state", () => {
  const user = { id: "author", username: "author", email: "author@example.test", role: "user" };
  return { useAuth: () => ({ user }) };
});

beforeEach(async () => {
  await i18n.changeLanguage("zh-CN");
  vi.spyOn(api.user, "listLedger").mockResolvedValue([]);
  vi.spyOn(api.user, "listConversionHistory").mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it("keeps the project balance visible during a public balance outage and retries independently", async () => {
  const read = vi.spyOn(api.user, "getCreditBalance")
    .mockResolvedValueOnce({ projectCredits: 24, publicCredits: null, totalCredits: null })
    .mockResolvedValue({ projectCredits: 24, publicCredits: 0, totalCredits: 24 });
  render(<ProfilePage />, { wrapper: createQueryClientWrapper(createTestQueryClient()) });

  expect(await screen.findAllByText("暂不可用")).toHaveLength(2);
  expect(screen.getByText("24")).toBeTruthy();
  const retry = screen.getByRole("button", { name: "重试余额查询" });
  fireEvent.click(retry);

  expect(await screen.findByText("0")).toBeTruthy();
  expect(screen.queryByText("暂不可用")).toBeNull();
  expect(screen.queryByRole("button", { name: "重试余额查询" })).toBeNull();
  expect(read).toHaveBeenCalledTimes(2);
  expect(api.user.listLedger).toHaveBeenCalledTimes(1);
});

it("keeps the profile usable when the balance endpoint fails", async () => {
  vi.spyOn(api.user, "getCreditBalance").mockRejectedValue(new ApiError(503, "UNAVAILABLE"));
  render(<ProfilePage />, { wrapper: createQueryClientWrapper(createTestQueryClient()) });

  expect(await screen.findByRole("button", { name: "重试余额查询" })).toBeTruthy();
  expect(screen.getByText("author")).toBeTruthy();
  expect(screen.getAllByText("暂不可用")).toHaveLength(3);
});
