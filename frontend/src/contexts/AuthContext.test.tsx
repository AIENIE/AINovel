import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthContext";
import { useAuth } from "./auth-state";
import { api, ApiError } from "@/lib/api-client";
import type { UserIdentity } from "@/types";

beforeEach(() => localStorage.clear());
afterEach(() => vi.restoreAllMocks());
it("does not let delayed bootstrap failure erase an accepted SSO session", async () => {
  localStorage.setItem("token", "old");
  let fail!: (error: Error) => void;
  vi.spyOn(api.user, "getIdentity").mockImplementationOnce(() => new Promise((_resolve, reject) => { fail = reject; }))
    .mockResolvedValue({ id: "u", username: "new", role: "user" } as UserIdentity);
  const balances = vi.spyOn(api.user, "getCreditBalance");
  const { result } = renderHook(useAuth, { wrapper: AuthProvider });
  await act(async () => { await result.current.acceptToken("new"); });
  await act(async () => { fail(new ApiError(401, "SESSION_INVALID")); });
  expect(localStorage.getItem("token")).toBe("new");
  expect(result.current.user?.username).toBe("new");
  expect(result.current.isAuthenticated).toBe(true);
  expect(balances).not.toHaveBeenCalled();
});
it("retains token on temporary bootstrap failure and recovers through retry", async () => {
  localStorage.setItem("token", "test");
  vi.spyOn(api.user, "getIdentity").mockRejectedValueOnce(new ApiError(503, "unavailable"))
    .mockResolvedValue({ id: "u", username: "test", role: "user" } as UserIdentity);
  const { result } = renderHook(useAuth, { wrapper: AuthProvider });
  await waitFor(() => expect(result.current.authUnavailable).toBe(true));
  expect(localStorage.getItem("token")).toBe("test");
  expect(result.current.isAuthenticated).toBe(false);
  await act(async () => { await result.current.retryAuth?.(); });
  expect(result.current.isAuthenticated).toBe(true);
  expect(result.current.authUnavailable).toBe(false);
});
it("keeps an exchanged token when identity validation is temporarily unavailable", async () => {
  vi.spyOn(api.user, "getIdentity").mockRejectedValue(new ApiError(503, "unavailable"));
  const { result } = renderHook(useAuth, { wrapper: AuthProvider });
  await act(async () => { await result.current.acceptToken("exchanged"); });
  expect(localStorage.getItem("token")).toBe("exchanged");
  expect(result.current.authUnavailable).toBe(true);
});

it("does not show a stale identity failure after logout", async () => {
  localStorage.setItem("token", "old");
  let fail!: (error: Error) => void;
  vi.spyOn(api.user, "getIdentity").mockImplementationOnce(() => new Promise((_resolve, reject) => { fail = reject; }));
  const { result } = renderHook(useAuth, { wrapper: AuthProvider });
  act(() => result.current.logout());
  await act(async () => fail(new ApiError(503, "SESSION_VALIDATION_UNAVAILABLE")));
  expect(result.current.isLoading).toBe(false);
  expect(result.current.authUnavailable).toBe(false);
  expect(result.current.user).toBeNull();
});
