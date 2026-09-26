import { isApiError } from "@/lib/api-client";
import { t } from "@/i18n";

/**
 * 稳定错误类别 → 本地化文案的映射。
 * 后端原始 message 从不直接展示给用户，只映射 HTTP 状态到稳定的前端错误类别；
 * 未命中类别时显示本地化通用操作失败。
 */
const H2_MESSAGES: Record<string,string> = {
    H2_KNOWLEDGE_KIND_MISMATCH: '人物可用表述的类型必须与关联陈述一致，请重新核对。',
    H2_PROMPT_BUDGET_EXCEEDED: '完整上下文与上一稿超出模型预算，已停止调用。已有稿件保留在生成稿中。',
    H2_LENGTH_LIMIT_REACHED: '一次篇幅修正后仍未达标，已保留初稿与修正稿，请查看后处理。',
    H2_CONFIGURATION_REQUIRED: '请先保存本分支的叙事约定。', H2_VIEWPOINT_REQUIRED: '请为当前场景指定视角人物，再预览或生成。',
    H2_SCENE_PLAN_REQUIRED: '请先补充并保存一条当前场景的作者计划。',
    H2_INVALID_VIEWPOINT: '视角人物或场景已不存在，请重新选择。', H2_KNOWLEDGE_BEFORE_EVIDENCE: '知情开始位置必须在事实及获知证据所在场景之后。',
    H2_HYPOTHESIS_EVIDENCE_REQUIRED: '读者假设需要关联已披露的证据记录。', H2_RECORD_UNAVAILABLE: '关联记录已失效或晚于适用位置，请核对证据。',
    H2_CONTEXT_CHANGED: '正文、账本或隔离配置已经改变。生成候选已保留，请查看后处理。',
    H2_STALE_REQUIRES_NEW_DECISION: '待复核条目不能直接恢复有效；请保留历史并重新补充有依据的条目。',
    H2_REQUIRED_CONTEXT_TOO_LARGE: '本场计划超出上下文预算，请精简计划后重试。',
  };

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
    const message = H2_MESSAGES[error.message] || (error.status === 503 ? "服务暂时不可用，输入已保留，请稍后重试。" : t(errorKeyForStatus(error.status)));
    return error.requestId && /^[a-zA-Z0-9-]{1,64}$/.test(error.requestId)
      ? `${message}（请求编号：${error.requestId}）` : message;
  }
  return t(fallbackKey);
}
