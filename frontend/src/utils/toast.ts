import { toast } from "@/components/ui/use-toast";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";

/**
 * 成功提示：messageKey 为 i18n 键（可带插值参数）。
 */
export const showSuccess = (messageKey: string, options?: Record<string, unknown>) =>
  toast({ title: t("common.notice"), description: t(messageKey, options) });

/**
 * 失败提示：优先将 ApiError 映射为本地化错误类别；
 * 普通 Error / 未知值回退到 fallbackKey，绝不直接展示后端原始 message。
 */
export const showError = (input: string | Error | unknown, fallbackKey = "errors.operationFailed") => {
  const description = typeof input === "string" ? t(input) : localizedErrorMessage(input, fallbackKey);
  toast({ title: t("common.error"), description, variant: "destructive" });
};
