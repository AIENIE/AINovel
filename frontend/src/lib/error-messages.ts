import { isApiError } from "@/lib/api-client";
import { t } from "@/i18n";

/**
 * 稳定错误类别 → 本地化文案的映射。
 * 后端原始 message 从不直接展示给用户，只映射 HTTP 状态到稳定的前端错误类别；
 * 未命中类别时显示本地化通用操作失败。
 */
const OPERATION_MESSAGES: Record<string,string> = {
    LANGUAGE_FEATURE_DISABLED: '本环境暂未启用语言检查，历史报告和候选仍保留。',
    LANGUAGE_EMPTY_SCENE: '请先保存需要检查的正文。',
    LANGUAGE_CHECK_IN_PROGRESS: '语言检查尚未结束，请等待完整结果后生成修改建议。',
    LANGUAGE_REPORT_STALE: '正文、分支或语言规范已变化，这份报告已过期。请主动重新检查。',
    LANGUAGE_LOCATION_UNTRUSTED: '无法唯一确认原文位置，不能生成可直接应用的修改。请查看引文并手动编辑。',
    LANGUAGE_PATCH_BLOCKED: '候选未通过语言效果或原意保留检查，不能一键采纳。请核对完整差异。',
    LANGUAGE_PATCH_STALE: '正文或上下文已经变化，未覆盖现有内容。请核对差异。',
    LANGUAGE_PATCH_LOCATION_CHANGED: '目标原文已经变化，不能采纳或撤销这项修改。',
    LANGUAGE_BRANCH_CHANGED: '当前分支已经变化，不能在这个分支应用原候选。',
    LANGUAGE_PATCH_DECIDED: '此候选的处置状态已经变化，请刷新结果。',
    LANGUAGE_DECISION_KEY_CONFLICT: '请求键已经用于另一项处置，未再次修改正文。请刷新结果。',
    LANGUAGE_RECORD_NOT_FOUND: '这份语言报告或候选不存在，或不属于当前稿件。',
    AI_RESULT_RECONCILIATION_REQUIRED: '这个任务的结果仍待对账，已保留原任务和积分冻结。请刷新原任务状态，避免重复生成。',
    AI_INITIALIZATION_INVALID_RESULT: 'AI 返回的初始化结果无法使用，请保留输入后重试。',
    CREATION_REQUEST_CONFLICT: '此创建请求已用于其他内容，请关闭创建框后重新创建。',
    CREATED_MANUSCRIPT_DELETED: '这次创建的稿件已经删除。请关闭创建框后发起新的创建。',
    AI_VALIDATION_BUDGET_EXHAUSTED: '本轮真实调用额度已用完，未发起新的 AI 请求。',
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

export function localizedOperationFailure(code?: string | null): string {
  return (code && OPERATION_MESSAGES[code]) || t("errors.operationFailed");
}

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
    const message = OPERATION_MESSAGES[error.message] || (error.status === 503 ? "服务暂时不可用，输入已保留，请稍后重试。" : t(errorKeyForStatus(error.status)));
    return error.requestId && /^[a-zA-Z0-9-]{1,64}$/.test(error.requestId)
      ? `${message}（请求编号：${error.requestId}）` : message;
  }
  return t(fallbackKey);
}
