import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { zhCN } from "./locales/zh-CN";
import { zhTW } from "./locales/zh-TW";
import { en } from "./locales/en";

/** 仅支持的用户语言（静态打包资源，不使用 http backend / language detector / Accept-Language）。 */
export const SUPPORTED_LOCALES = ["zh-CN", "zh-TW", "en"] as const;
export type UserLocale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: UserLocale = "zh-CN";
export const LOCALE_STORAGE_KEY = "aienie.user.locale.v1";

export function isSupportedLocale(value: unknown): value is UserLocale {
  return typeof value === "string" && (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

/** 从 localStorage 读取用户语言，坏值/缺失一律回退默认 zh-CN。 */
export function resolveInitialLocale(): UserLocale {
  try {
    const stored = globalThis.localStorage?.getItem(LOCALE_STORAGE_KEY);
    if (isSupportedLocale(stored)) return stored;
  } catch {
    // localStorage 不可用（隐私模式等）时使用默认值
  }
  return DEFAULT_LOCALE;
}

function syncHtmlDocument(lng: string): void {
  if (typeof document === "undefined") return;
  document.documentElement.lang = lng;
  document.title = i18n.t("app.title");
}

i18n.use(initReactI18next).init({
  resources: {
    "zh-CN": { translation: zhCN },
    "zh-TW": { translation: zhTW },
    en: { translation: en },
  },
  lng: resolveInitialLocale(),
  fallbackLng: DEFAULT_LOCALE,
  supportedLngs: [...SUPPORTED_LOCALES],
  load: "currentOnly",
  keySeparator: false,
  nsSeparator: false,
  interpolation: {
    escapeValue: true,
  },
  initImmediate: false,
  react: {
    useSuspense: false,
  },
});

syncHtmlDocument(i18n.language);

/** 切换语言：立即生效、不 reload，并持久化到 localStorage。 */
export async function setUserLocale(locale: UserLocale): Promise<void> {
  if (!isSupportedLocale(locale)) return;
  await i18n.changeLanguage(locale);
  try {
    globalThis.localStorage?.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // 忽略存储不可用
  }
  syncHtmlDocument(locale);
}

export const t = i18n.t.bind(i18n);
export default i18n;
