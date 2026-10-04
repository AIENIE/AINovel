import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
  it("clears displayed codes and current OTP when the page closes",async()=>{
    auth.status.mockResolvedValue({enrolled:true,recoveryCodesRemaining:10,lowRecoveryThreshold:3,authMode:"totp"});
    auth.getRecoveryCodes.mockResolvedValue({recoveryCodes:["PAGE-CODE"],remaining:10,generatedCount:0});render(<AdminSecurity/>);
    fireEvent.change(await screen.findByLabelText("当前动态验证码"),{target:{value:"123456"}});fireEvent.click(screen.getByRole("button",{name:"获取紧急码"}));await screen.findByText("PAGE-CODE");
    fireEvent.change(screen.getByLabelText("当前动态验证码"),{target:{value:"654321"}});fireEvent(window,new Event("pagehide"));
    expect(screen.queryByText("PAGE-CODE")).toBeNull();expect((screen.getByLabelText("当前动态验证码") as HTMLInputElement).value).toBe("");
  });
  it("rejects a late code response after pagehide even when the page resumes",async()=>{
    auth.status.mockResolvedValue({enrolled:true,recoveryCodesRemaining:10,lowRecoveryThreshold:3,authMode:"password"});
    let resolve!: (value:{recoveryCodes:string[];remaining:number;generatedCount:number})=>void;
    auth.getRecoveryCodes.mockReturnValue(new Promise(r=>{resolve=r;}));render(<AdminSecurity/>);fireEvent.click(await screen.findByRole("button",{name:"获取紧急码"}));
    fireEvent(window,new Event("pagehide"));fireEvent(window,new Event("pageshow"));await act(async()=>{resolve({recoveryCodes:["LATE-CODE"],remaining:10,generatedCount:0});});expect(screen.queryByText("LATE-CODE")).toBeNull();
    auth.getRecoveryCodes.mockResolvedValue({recoveryCodes:["FRESH-CODE"],remaining:10,generatedCount:0});fireEvent.click(screen.getByRole("button",{name:"获取紧急码"}));expect(await screen.findByText("FRESH-CODE")).toBeTruthy();
  });

});
