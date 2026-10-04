import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminSecurity from "./Security";
const auth = vi.hoisted(() => ({status:vi.fn(),getRecoveryCodes:vi.fn()}));
vi.mock("@/lib/api-client",()=>({api:{adminAuth:auth}}));
describe("AdminSecurity emergency codes",()=>{
  beforeEach(()=>{cleanup();Object.values(auth).forEach(fn=>fn.mockReset());});
  it("requires current OTP and clears displayed codes on close",async()=>{
    auth.status.mockResolvedValue({enrolled:true,recoveryCodesRemaining:9,lowRecoveryThreshold:3,authMode:"totp"});
    auth.getRecoveryCodes.mockResolvedValue({recoveryCodes:["A1B2C3D4-11223344-55667788-99AABBCC"],remaining:10,generatedCount:1});
    render(<AdminSecurity/>);const button=await screen.findByRole("button",{name:"获取紧急码"});expect(button.hasAttribute("disabled")).toBe(true);
    fireEvent.change(screen.getByLabelText("当前动态验证码"),{target:{value:"123456"}});fireEvent.click(button);
    expect(await screen.findByText("A1B2C3D4-11223344-55667788-99AABBCC")).toBeTruthy();expect(auth.getRecoveryCodes).toHaveBeenCalledWith("123456");
    fireEvent.click(screen.getByRole("button",{name:"关闭"}));await waitFor(()=>expect(screen.queryByText("A1B2C3D4-11223344-55667788-99AABBCC")).toBeNull());
  });
  it("allows already bound password-mode administrators to obtain codes without OTP",async()=>{
    auth.status.mockResolvedValue({enrolled:true,recoveryCodesRemaining:0,lowRecoveryThreshold:3,authMode:"password"});auth.getRecoveryCodes.mockResolvedValue({recoveryCodes:["test-display"],remaining:10,generatedCount:10});
    render(<AdminSecurity/>);fireEvent.click(await screen.findByRole("button",{name:"获取紧急码"}));expect(await screen.findByText("test-display")).toBeTruthy();expect(auth.getRecoveryCodes).toHaveBeenCalledWith("");expect(screen.queryByLabelText("当前动态验证码")).toBeNull();
  });
  it("explains binding requirement instead of allowing an unbound get",async()=>{
    auth.status.mockResolvedValue({enrolled:false,recoveryCodesRemaining:0,lowRecoveryThreshold:3,authMode:"password"});render(<AdminSecurity/>);expect(await screen.findByText("请先完成动态码绑定。")).toBeTruthy();expect(screen.queryByRole("button",{name:"获取紧急码"})).toBeNull();expect(auth.getRecoveryCodes).not.toHaveBeenCalled();
  });
});
