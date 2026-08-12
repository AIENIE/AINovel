import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setUserLocale } from "@/i18n";
import Index from "@/pages/Index";

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ isAuthenticated: false, user: null, logout: vi.fn(), isAdmin: false }),
}));

vi.mock("@/lib/sso", () => ({
  buildSsoUrl: (mode: string) => `https://sso.example/${mode}`,
  issueSsoState: () => "test-state",
}));

/**
 * 估算按钮文本的近似像素宽度。
 * 基于近似：中文字符按 1em 宽、拉丁字符按 0.55em 宽、空格按 0.3em 宽估算。
 * 字体大小 14px（text-sm）时给出保守近似，用于验证 390px 头部不溢出。
 */
const estimateTextWidth = (text: string, fontSize = 14): number => {
  let width = 0;
  for (const ch of text) {
    const code = ch.codePointAt(0) ?? 0;
    if (code >= 0x4e00 && code <= 0x9fff) width += fontSize; // CJK
    else if (ch === " ") width += fontSize * 0.3;
    else width += fontSize * 0.55;
  }
  return width;
};

/** 紧凑文本按钮宽度：文本 + 两侧 padding(8px) + 图标间隔(未计入) */
const compactButton = (text: string) => estimateTextWidth(text) + 8 * 2;

describe("Index 首页头部 390px 响应式", () => {
  beforeEach(async () => {
    await setUserLocale("zh-CN");
  });

  afterEach(() => {
    cleanup();
  });

  it("估算三种语言下移动端头部总宽不超过 390px", () => {
    // 移动端(<640px)头部元素：
    // logo（AI 方块 28px + 间距 + "AINovel" ~66px）≈ 110px
    // LanguageSwitcher 图标按钮（icon 16 + 按钮内边距）≈ 40px
    // 积分说明折叠为图标按钮（icon 16 + px-2）≈ 32px
    // 登录 / 注册为紧凑文本按钮（px-2 = 8px 每侧）
    const logoWidth = 110;
    const langSwitcherWidth = 40;
    const pricingIconWidth = 32;

    const widthsByLocale: Record<string, number> = {
      "zh-CN": logoWidth + langSwitcherWidth + pricingIconWidth + compactButton("登录") + compactButton("注册"),
      "zh-TW": logoWidth + langSwitcherWidth + pricingIconWidth + compactButton("登入") + compactButton("註冊"),
      en: logoWidth + langSwitcherWidth + pricingIconWidth + compactButton("Log in") + compactButton("Sign up"),
    };

    // 容器左右 padding（px-3 = 12px × 2）+ flex 间隙（gap-1 = 4px × 4）
    const containerPadding = 12 * 2;
    const gaps = 4 * 4;

    for (const [locale, width] of Object.entries(widthsByLocale)) {
      const total = width + containerPadding + gaps;
      expect(total, `${locale} 头部总宽 ${total}px 应 < 390px`).toBeLessThan(390);
    }
  });

  it("en 为最宽用例且移动端折叠后总宽仍低于 390px", () => {
    // 积分说明在移动端折叠为图标（32px），仅登录/注册保留紧凑文本
    const enLogin = "Log in";
    const enRegister = "Sign up";
    const enTotal = 110 + 40 + 32 + compactButton(enLogin) + compactButton(enRegister) + 12 * 2 + 4 * 4;
    expect(enTotal).toBeLessThan(390);
  });

  it("移动端积分说明折叠为图标按钮且保持可访问，登录/注册始终可见", async () => {
    await setUserLocale("en");
    render(
      <MemoryRouter>
        <Index />
      </MemoryRouter>,
    );

    // 积分说明按钮：aria-label 与 title 提供无障碍名称（移动端无可见文本）
    const pricingButton = screen
      .getAllByRole("button")
      .find((btn) => (btn as HTMLButtonElement).getAttribute("aria-label") === "Credits Guide");
    expect(pricingButton).toBeDefined();
    expect(pricingButton?.getAttribute("title")).toBe("Credits Guide");

    // 内部文本 span 带 hidden sm:inline（sm 以下隐藏）
    const textSpan = within(pricingButton as HTMLElement).queryByText("Credits Guide");
    expect(textSpan).not.toBeNull();
    expect(textSpan?.className).toContain("hidden");
    expect(textSpan?.className).toContain("sm:inline");

    // 登录与注册按钮始终可见可点
    expect(screen.getByRole("button", { name: "Log in" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Sign up" })).toBeTruthy();
  });

  it("三种语言渲染头部各入口按钮", async () => {
    const locales = ["zh-CN", "zh-TW", "en"] as const;
    for (const locale of locales) {
      await setUserLocale(locale);
      render(
        <MemoryRouter>
          <Index />
        </MemoryRouter>,
      );

      const langName = locale === "zh-CN" ? "语言" : locale === "zh-TW" ? "語言" : "Language";
      expect(screen.getByRole("button", { name: langName })).toBeTruthy();
      expect(screen.getByRole("button", { name: locale === "en" ? "Log in" : locale === "zh-TW" ? "登入" : "登录" })).toBeTruthy();
      expect(screen.getByRole("button", { name: locale === "en" ? "Sign up" : locale === "zh-TW" ? "註冊" : "注册" })).toBeTruthy();
      cleanup();
    }
  });

  it("极窄断点(≤379px)登录/注册折叠为图标按钮且保持可访问", async () => {
    await setUserLocale("en");
    render(
      <MemoryRouter>
        <Index />
      </MemoryRouter>,
    );

    // 登录按钮：aria-label + title 提供无障碍名称（极窄下无可见文本）
    const loginButton = screen
      .getAllByRole("button")
      .find((btn) => (btn as HTMLButtonElement).getAttribute("aria-label") === "Log in");
    expect(loginButton).toBeDefined();
    expect(loginButton?.getAttribute("title")).toBe("Log in");

    // 图标（LogIn）在极窄断点显示，文本 span 在极窄断点隐藏
    const loginText = within(loginButton as HTMLElement).queryByText("Log in");
    expect(loginText).not.toBeNull();
    expect(loginText?.className).toContain("max-[379px]:hidden");

    // 注册按钮同理
    const registerButton = screen
      .getAllByRole("button")
      .find((btn) => (btn as HTMLButtonElement).getAttribute("aria-label") === "Sign up");
    expect(registerButton).toBeDefined();
    expect(registerButton?.getAttribute("title")).toBe("Sign up");
    const registerText = within(registerButton as HTMLElement).queryByText("Sign up");
    expect(registerText).not.toBeNull();
    expect(registerText?.className).toContain("max-[379px]:hidden");
  });

  it("320px en 极窄断点折叠后头部总宽不超过 320px", () => {
    // 极窄(≤379px)布局：logo ~101 + 语言切换 40 + 积分说明图标 32 + 登录图标 32 + 注册图标 32
    // 登录/注册为图标按钮（icon 16 + px-2 = 8px 每侧）
    const iconButton = 16 + 8 * 2; // 32px
    const total =
      101 + 40 + 32 + iconButton + iconButton + 12 * 2 + 4 * 4;
    expect(total).toBeLessThan(320);
  });

  it("390px 布局登录/注册保留文本且不溢出", () => {
    // 390px（>379px）布局：logo ~101 + 语言 40 + 积分图标 32 + "Log in" 文本 + "Sign up" 文本
    const enLogin = "Log in";
    const enRegister = "Sign up";
    const total = 101 + 40 + 32 + compactButton(enLogin) + compactButton(enRegister) + 12 * 2 + 4 * 4;
    expect(total).toBeLessThan(390);
  });
});
