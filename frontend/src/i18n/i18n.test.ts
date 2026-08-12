import { afterEach, beforeEach, describe, expect, it } from "vitest";
import i18n, {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  isSupportedLocale,
  resolveInitialLocale,
  setUserLocale,
} from "@/i18n";

describe("i18n locale 解析", () => {
  afterEach(() => {
    localStorage.clear();
    document.documentElement.lang = "zh-CN";
  });

  it("仅支持 zh-CN、zh-TW、en", () => {
    expect(SUPPORTED_LOCALES).toEqual(["zh-CN", "zh-TW", "en"]);
    expect(isSupportedLocale("zh-CN")).toBe(true);
    expect(isSupportedLocale("zh-TW")).toBe(true);
    expect(isSupportedLocale("en")).toBe(true);
    expect(isSupportedLocale("fr")).toBe(false);
    expect(isSupportedLocale("")).toBe(false);
    expect(isSupportedLocale(null)).toBe(false);
  });

  it("无 localStorage 值时回退默认 zh-CN", () => {
    expect(resolveInitialLocale()).toBe(DEFAULT_LOCALE);
  });

  it("坏值（不在支持列表）回退默认 zh-CN", () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "ja-JP");
    expect(resolveInitialLocale()).toBe(DEFAULT_LOCALE);
    localStorage.setItem(LOCALE_STORAGE_KEY, "not-a-locale");
    expect(resolveInitialLocale()).toBe(DEFAULT_LOCALE);
  });

  it("有效值被正确读取", () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "zh-TW");
    expect(resolveInitialLocale()).toBe("zh-TW");
    localStorage.setItem(LOCALE_STORAGE_KEY, "en");
    expect(resolveInitialLocale()).toBe("en");
  });
});

describe("setUserLocale 切换", () => {
  beforeEach(async () => {
    localStorage.clear();
    await setUserLocale("zh-CN");
    document.documentElement.lang = "zh-CN";
  });

  it("切换立即生效且持久化到 localStorage", async () => {
    await setUserLocale("en");
    expect(i18n.language).toBe("en");
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en");
    expect(i18n.t("nav.dashboard")).toBe("Dashboard");
  });

  it("切换后更新 documentElement.lang 与 document.title", async () => {
    await setUserLocale("zh-TW");
    expect(document.documentElement.lang).toBe("zh-TW");
    expect(document.title).toBe(i18n.t("app.title"));
    expect(document.title).not.toBe("AINovel - AI Novel Writing Platform");
  });

  it("不支持的语言被忽略，不覆盖已有偏好", async () => {
    await setUserLocale("fr" as never);
    expect(i18n.language).toBe("zh-CN");
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("zh-CN");
    expect(i18n.t("nav.dashboard")).toBe("创作首页");
  });
});

describe("资源完整性", () => {
  it("三种语言资源键集合一致（无缺失/多余键）", () => {
    const keys = (ns: Record<string, string>) => Object.keys(ns).sort();
    // 通过 i18next 现有 store 读取各语言资源
    const zhCNKeys = Object.keys(i18n.getResourceBundle("zh-CN", "translation")).sort();
    const zhTWKeys = Object.keys(i18n.getResourceBundle("zh-TW", "translation")).sort();
    const enKeys = Object.keys(i18n.getResourceBundle("en", "translation")).sort();
    expect(zhCNKeys).toEqual(zhTWKeys);
    expect(zhCNKeys).toEqual(enKeys);
    expect(zhCNKeys.length).toBeGreaterThan(150);
    void keys;
  });

  it("默认 zh-CN 文案与既有用户侧中文一致（抽查）", () => {
    i18n.changeLanguage("zh-CN");
    expect(i18n.t("nav.dashboard")).toBe("创作首页");
    expect(i18n.t("common.loading")).toBe("加载中...");
    expect(i18n.t("errors.loadFailed")).toBe("加载失败");
  });
});
