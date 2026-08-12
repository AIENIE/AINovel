import { isApiError } from "@/lib/api-client";
import { t } from "@/i18n";

/**
 * 稳定错误类别 → 本地化文案的映射。
 * 后端原始 message 从不直接展示给用户，只映射 HTTP 状态到稳定的前端错误类别；
 * 未命中类别时显示本地化通用操作失败。
 */
const ERROR_STATUS_KEYS: Record<number, string> = {
  400: "errors.badRequest",
  401: "errors.unauthorized",
  403: "errors.forbidden",
  404: "errors.notFound",
  409: "errors.conflict",
  428: "errors.operationFailed",
  429: "errors.rateLimited",
};

function isServerErrorStatus(status: number): boolean {
  return status >= 500 && status <= 599;
}

export function errorKeyForStatus(status: number): string {
  if (isServerErrorStatus(status)) return "errors.serverError";
  return ERROR_STATUS_KEYS[status] ?? "errors.operationFailed";
}

/**
 * 将任意错误转换为本地化文案：
 * - ApiError 按 HTTP 状态映射稳定错误类别；
 * - 普通 Error / 未知值一律返回 fallbackKey（本地化通用文案），不泄露原始 message。
 */
export function localizedErrorMessage(error: unknown, fallbackKey = "errors.operationFailed"): string {
  if (isApiError(error)) {
    return t(errorKeyForStatus(error.status));
  }
  return t(fallbackKey);
}
