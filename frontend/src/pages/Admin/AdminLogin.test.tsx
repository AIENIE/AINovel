import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminLogin from "./Login";

const auth = vi.hoisted(() => ({
  bootstrap: vi.fn(),
  me: vi.fn(),
  login: vi.fn(),
  startEnrollment: vi.fn(),
  confirmEnrollment: vi.fn(),
  loginTotp: vi.fn(),
  loginRecovery: vi.fn(),
  startRebind: vi.fn(),
  confirmRebind: vi.fn(),
}));

vi.mock("@/lib/api-client", () => ({ api: { adminAuth: auth } }));

const renderLogin = () => render(
  <MemoryRouter initialEntries={["/admin/login"]}>
    <Routes>
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route path="/admin/dashboard" element={<div>ADMIN_DASHBOARD</div>} />
    </Routes>
  </MemoryRouter>,
);

const enterPassword = async () => {
  fireEvent.change(await screen.findByLabelText("管理员账号"), { target: { value: "admin" } });
  fireEvent.change(screen.getByLabelText("管理员密码"), { target: { value: "correct horse" } });
  fireEvent.click(screen.getByRole("button", { name: "继续" }));
};

describe("AdminLogin", () => {
  beforeEach(() => {
    Object.values(auth).forEach((mockFn) => mockFn.mockReset());
    auth.me.mockRejectedValue(new Error("no session"));
  });

  it("uses the direct password flow only when bootstrap declares local password mode", async () => {
    auth.bootstrap.mockResolvedValue({ authMode: "password", state: "PASSWORD_REQUIRED" });
    auth.login.mockResolvedValue({ username: "admin", expiresAt: "2026-08-10T12:00:00Z" });
    renderLogin();

    await enterPassword();

    expect(await screen.findByText("ADMIN_DASHBOARD")).toBeTruthy();
    expect(auth.login).toHaveBeenCalledWith("admin", "correct horse");
    expect(auth.loginTotp).not.toHaveBeenCalled();
  });

  it("requires password before TOTP and forces recovery sessions into rebind", async () => {
    auth.bootstrap.mockResolvedValue({ authMode: "totp", state: "TOTP_REQUIRED" });
    auth.login.mockResolvedValue({
      status: "TOTP_REQUIRED", username: "admin", challengeId: "login-challenge",
      expiresAt: "2026-08-10T12:00:00Z",
    });
    auth.loginRecovery.mockResolvedValue({ username: "admin", sessionScope: "RECOVERY" });
    auth.startRebind.mockResolvedValue({
      challengeId: "rebind-challenge", manualKey: "BASE32KEY", otpauthUri: "otpauth://totp/rebind",
    });
    renderLogin();

    await enterPassword();
    expect(await screen.findByLabelText("动态验证码")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "使用恢复码" }));
    fireEvent.change(screen.getByLabelText("恢复码"), { target: { value: "RECOVERY-CODE" } });
    fireEvent.click(screen.getByRole("button", { name: "进入受限恢复" }));

    expect(await screen.findByText("重新绑定验证器")).toBeTruthy();
    expect(auth.loginRecovery).toHaveBeenCalledWith("login-challenge", "RECOVERY-CODE");
    expect(auth.login.mock.invocationCallOrder[0]).toBeLessThan(auth.loginRecovery.mock.invocationCallOrder[0]);
    await waitFor(() => expect(auth.startRebind).toHaveBeenCalledTimes(1));
  });

  it("enrolls only after the password challenge and shows one-time recovery codes", async () => {
    auth.bootstrap.mockResolvedValue({ authMode: "totp", state: "ENROLLMENT_REQUIRED" });
    auth.login.mockResolvedValue({
      status: "ENROLLMENT_REQUIRED", username: "admin", challengeId: "password-challenge",
      expiresAt: "2026-08-10T12:00:00Z",
    });
    auth.startEnrollment.mockResolvedValue({
      challengeId: "enrollment-challenge", manualKey: "BASE32KEY", otpauthUri: "otpauth://totp/enrollment",
    });
    auth.confirmEnrollment.mockResolvedValue({ recoveryCodes: ["RECOVERY-ONE", "RECOVERY-TWO"] });
    renderLogin();

    await enterPassword();
    expect(await screen.findByText("绑定验证器")).toBeTruthy();
    expect(auth.startEnrollment).toHaveBeenCalledWith("password-challenge");

    fireEvent.change(screen.getByLabelText("首次验证码"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "确认绑定" }));

    expect(await screen.findByText("保存恢复码")).toBeTruthy();
    expect(screen.getByText("RECOVERY-ONE")).toBeTruthy();
    expect(auth.confirmEnrollment).toHaveBeenCalledWith("enrollment-challenge", "123456");
  });
});
