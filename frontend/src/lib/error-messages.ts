import { isApiError } from "@/lib/api-client";
import { t } from "@/i18n";

/**
 * 稳定错误类别 → 本地化文案的映射。
 * 后端原始 message 从不直接展示给用户，只映射 HTTP 状态到稳定的前端错误类别；
 * 未命中类别时显示本地化通用操作失败。
 */
const AI_MODEL_ERROR_KEYS: Record<string, string> = {
  WRITING_AGENT_SAVE_REQUIRED: 'agent.saveRequired',
  WRITING_AGENT_PROTOCOL_UNAVAILABLE: 'agent.protocolUnavailable',
  WRITING_AGENT_PROTOCOL_UNSUPPORTED: 'agent.protocolUnavailable',
  WRITING_AGENT_BUDGET_UNAVAILABLE: 'agent.budgetUnavailable',
  WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE: 'agent.budgetPrepareUnavailable',
  WRITING_AGENT_BUDGET_GRANT_INVALID: 'agent.budgetGrantInvalid',
  AI_VALIDATION_PREPARE_UNAVAILABLE: 'agent.budgetUnavailable',
  WRITING_AGENT_BUDGET_INVALID: 'agent.budgetInvalid',
  WRITING_AGENT_PRICE_INVALID: 'agent.budgetInvalid',
  AI_VALIDATION_QUOTE_INVALID: 'agent.budgetInvalid',
  AI_VALIDATION_PRICE_UNAVAILABLE: 'agent.budgetInvalid',
  AI_NATIVE_CONFIGURATION_REQUIRED: 'agent.configurationRequired',
  AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE: 'agent.toolsUnavailable',
  AI_VALIDATION_CAPABILITY_UNAVAILABLE: 'agent.toolsUnavailable',
  WRITING_AGENT_TOOL_UNAVAILABLE: 'agent.toolUnavailable',
  WRITING_AGENT_CONFIRMED_NOT_SENT: 'agent.confirmedNotSent',
  AI_VALIDATION_CONFIRMED_NOT_SENT: 'agent.confirmedNotSent',
  WRITING_AGENT_RECEIPT_INVALID: 'agent.receiptInvalid',
  WRITING_AGENT_RECEIPT_MISMATCH: 'agent.receiptInvalid',
  WRITING_AGENT_GRANT_INVALID: 'agent.receiptInvalid',
  WRITING_AGENT_RETAIL_MISMATCH: 'agent.receiptInvalid',
  WRITING_AGENT_LIMIT_INVALID: 'agent.invalidBudget',
  WRITING_AGENT_STATE_INVALID: 'agent.reload',
  AI_NATIVE_RESPONSE_INVALID: 'agent.responseInvalid',
  AI_VALIDATION_PHASE_BUDGET_EXHAUSTED: 'agent.phaseBudget',
  AI_VALIDATION_PHASE_BASELINE_INVALID: 'agent.phaseBaselineInvalid',
  WRITING_AGENT_CHECK_LIMIT: 'agent.checkLimit',
  WORLD_AGENT_REQUIRED_READ_CAPACITY: 'agent.worldLimit',
  AUTHOR_QA_READ_ONLY: 'agent.readOnly',
  H2_WORLD_TOOL_UNAVAILABLE: 'agent.h2',
  WRITING_AGENT_TOOL_INPUT_LIMIT: 'agent.inputLimit',
  WRITING_AGENT_CONTEXT_LIMIT: 'agent.inputLimit',
  WORLD_AGENT_READ_BUDGET_EXCEEDED: 'agent.worldLimit',
  WRITING_AGENT_REVISION_CONFLICT: 'agent.reload',
  WRITING_AGENT_CANDIDATE_STALE: 'agent.stale',
  WRITING_AGENT_CANDIDATE_BASELINE_CHANGED: 'agent.stale',
  WRITING_AGENT_CONTEXT_CHANGED: 'agent.stale',
  WRITING_AGENT_INCOMPLETE_RESPONSE: 'agent.incomplete',
  WRITING_AGENT_NO_CHANGE: 'agent.noChange',
  WRITING_AGENT_CALL_LIMIT: 'agent.callLimit',
  WRITING_AGENT_REVISION_LIMIT: 'agent.callLimit',
  WRITING_AGENT_BUDGET_EXHAUSTED: 'agent.budgetPaused',
  WRITING_AGENT_REQUIRED_READS: 'agent.requiredReads',
  AI_NATIVE_BUDGET_UNAVAILABLE: 'agent.toolsUnavailable',
  WORLD_ASSISTANT_EMPTY_RESULT: 'world3.emptyResult',
  WORLD_ASSISTANT_OUTPUT_LIMIT: 'world3.outputLimit',
  WORLD_ASSISTANT_REASONING_OUTPUT_LIMIT: 'world3.reasoningOutputLimit',
  WORLD_ASSISTANT_TRUNCATED_RESULT: 'world3.truncatedResult',
  WORLD_TIMELINE_DATE_INVALID: 'world5.dateInvalid',
  WORLD_TIMELINE_DATE_RANGE_INVALID: 'world5.reversedDate',
  WORLD_TIMELINE_VERSION_INVALID: 'world5.timelineInvalid',
  WORLD_TIMELINE_CALENDAR_INVALID: 'world5.timelineInvalid',
  WORLD_TIMELINE_ERA_INVALID: 'world5.timelineInvalid',
  WORLD_TIMELINE_EVENT_INVALID: 'world5.timelineInvalid',
  WORLD_TIMELINE_DATA_INVALID: 'world5.timelineInvalid',
  WORLD_TIMELINE_ERA_REFERENCE_INVALID: 'world5.referenceInvalid',
  WORLD_TIMELINE_ENTRY_REFERENCE_INVALID: 'world5.referenceInvalid',
  WORLD_TIMELINE_REFERENCE_INVALID: 'world5.referenceInvalid',
  WORLD_PROPOSAL_DEPENDENCY_MISSING: 'world5.dependencyMissing',
  WORLD_ASSISTANT_LEGACY_INPUT_UNAVAILABLE: 'world5.legacyTaskStopped',
  WORLD_ERAS_CONFLICT: 'world5.erasConflict',
  WORLD_ERAS_INVALID: 'world5.erasConflict',
  WORLD_ERA_UNAVAILABLE: 'world5.erasMissing',
  WORLD_COPY_KEY_REQUIRED: 'world5.copyConflict',
  WORLD_COPY_REQUEST_CONFLICT: 'world5.copyConflict',
  WORLD_COPY_RESULT_REMOVED: 'world5.copyRemoved',
  WORLD_EXPORT_FORMAT_INVALID: 'world5.invalidSource',
  WORLD_SOURCE_REQUIRED: 'world5.invalidSource',
  WORLD_SOURCE_REFERENCE_INVALID: 'world5.invalidSource',
  WORLD_PUBLISHED_VERSION_REQUIRED: 'world2.publishRequired',
  WORLD_CONTEXT_CHANGED: 'world4.stale',
  WORLD_CONTEXT_SELECTION_CHANGED: 'world4.stale',
  WORLD_CONTEXT_BINDING_CHANGED: 'world4.stale',
  WORLD_CONTEXT_MISSING_REQUIRED: 'world4.MISSING_REQUIRED',
  WORLD_CONTEXT_REVIEW_REQUIRED: 'world4.REVIEW_REQUIRED',
  WORLD_CONTEXT_RELATION_ENDPOINT_CONFLICT: 'world4.RELATION_ENDPOINT_CONFLICT',
  WORLD_CONTEXT_REQUIRED_CONTEXT_TOO_LARGE: 'world4.REQUIRED_CONTEXT_TOO_LARGE',
  OUTLINE_AI_RESULT_NEEDS_REVIEW: 'world4.stale',
  OUTLINE_AI_RESULT_INVALID: 'world4.INVALID',
  AI_FEATURE_UNCONFIGURED: 'world2.unconfigured',
  AI_REQUEST_IN_PROGRESS: "aiModels.requestInProgress",
  AI_RESULT_RECONCILIATION_REQUIRED: "aiModels.reconciliationNeeded",
  AI_PAYMENT_REQUIRED: "aiModels.paymentRequired",
  AI_QUOTE_REQUIRED: "aiModels.quoteRequired",
  AI_QUOTE_CANCELLED: "aiModels.quoteCancelled",
  AI_QUOTE_EXPIRED: "aiModels.quoteChanged",
  AI_QUOTE_INPUT_CHANGED: "aiModels.quoteChanged",
  AI_CONFIGURATION_CHANGED: "aiModels.quoteChanged",
  AI_QUOTE_ALREADY_USED: "aiModels.quoteChanged",
  AI_STEP_NOT_QUOTED: "aiModels.quoteChanged",
  AI_PREFERENCE_UNAVAILABLE: "aiModels.preferenceUnavailable",
  AI_OPTION_UNAVAILABLE: "aiModels.preferenceUnavailable",
  AI_MODEL_UNAVAILABLE: "aiModels.preferenceUnavailable",
  AI_THINKING_UNSUPPORTED: "aiModels.preferenceUnavailable",
};
const GRPC_STATUS_CODES = new Set(["OK", "CANCELLED", "UNKNOWN", "INVALID_ARGUMENT", "DEADLINE_EXCEEDED", "NOT_FOUND", "ALREADY_EXISTS", "PERMISSION_DENIED", "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION", "ABORTED", "OUT_OF_RANGE", "UNIMPLEMENTED", "INTERNAL", "UNAVAILABLE", "DATA_LOSS", "UNAUTHENTICATED"]);
const preparationGrpcStatus = (code?: string | null) => {
  const status = code && /^WRITING_AGENT_BUDGET_PREPARE_GRPC_([A-Z_]+)$/.exec(code)?.[1];
  return status && GRPC_STATUS_CODES.has(status) ? status : null;
};
const WRITING_AGENT_PREPARATION_FAILURES = new Set([
  "WRITING_AGENT_BUDGET_UNAVAILABLE", "WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE", "WRITING_AGENT_BUDGET_GRANT_INVALID",
  "WRITING_AGENT_BUDGET_INVALID", "WRITING_AGENT_PRICE_INVALID", "AI_VALIDATION_PREPARE_UNAVAILABLE", "AI_VALIDATION_QUOTE_INVALID", "AI_VALIDATION_PRICE_UNAVAILABLE",
  "AI_NATIVE_CONFIGURATION_REQUIRED", "AI_NATIVE_BUDGET_UNAVAILABLE", "AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE", "AI_VALIDATION_CAPABILITY_UNAVAILABLE",
  "WRITING_AGENT_CONFIRMED_NOT_SENT", "AI_VALIDATION_CONFIRMED_NOT_SENT", "AI_FEATURE_UNCONFIGURED", "AI_MODEL_UNAVAILABLE", "AI_OPTION_UNAVAILABLE", "AI_THINKING_UNSUPPORTED", "AI_CONFIGURATION_CHANGED",
  "WRITING_AGENT_PROTOCOL_UNAVAILABLE", "WRITING_AGENT_PROTOCOL_UNSUPPORTED",
]);
/** Display compatibility for old runs whose preparation failure was classified as a budget pause. */
export const isWritingAgentPreparationFailure = (code?: string | null): boolean => !!code && (WRITING_AGENT_PREPARATION_FAILURES.has(code) || preparationGrpcStatus(code) !== null);
const modelFailure = (code?: string | null) => {
  if (!code) return null;
  if (AI_MODEL_ERROR_KEYS[code]) return t(AI_MODEL_ERROR_KEYS[code]);
  const status = preparationGrpcStatus(code);
  return status ? t("agent.budgetPrepareGrpc", { status }) : null;
};
const worldFailure = (code?: string | null) => {
  if(code === "WORLD_PROPOSAL_VERSION_CONFLICT" || code === "WORLD_PROPOSAL_DECIDED") return t("world3.candidateStale");
  if(code && ["WORLD_PROPOSAL_STALE","WORLD_PROPOSAL_TARGET_MISSING","WORLD_PROPOSAL_TARGET_EXISTS","WORLD_PROPOSAL_CONTEXT_CHANGED","WORLD_PROPOSAL_REVISION_CONFLICT","WORLD_DRAFT_SAVE_REQUIRED","WORLD_ASSISTANT_CONTEXT_TOO_LARGE","WORLD_TEMPLATE_VARIABLE_UNKNOWN","WORLD_TEMPLATE_FUNCTION_UNSUPPORTED","WORLD_PROPOSAL_ENDPOINT_MISSING","WORLD_OVERVIEW_STALE"].includes(code)) return t(`world3.reason.${code}`);
  if(code && ["WORLD_ASSISTANT_RESULT_INVALID","WORLD_PROPOSAL_TARGET_INVALID","WORLD_PROPOSAL_FIELDS_INVALID","WORLD_ASSISTANT_READ_INVALID","WORLD_PROPOSAL_DUPLICATE_TARGET","WORLD_TARGET_NOT_READ","WORLD_CONFLICT_SOURCE_INVALID","WORLD_CANDIDATE_STRUCTURE_CHANGED","WORLD_PROPOSAL_NO_CHANGE"].includes(code)) return t("world3.invalidResult");
  const keys: Record<string,string> = {WORLD_BINDING_CONFLICT:'bindingConflict',WORLD_BINDING_UNRESOLVED:'bindingUnavailable',WORLD_VERSION_UNAVAILABLE:'bindingUnavailable',WORLD_BINDING_CHANGED:'bindingChangedResult',WORLD_ENTRY_INVALID:'entryInvalid',WORLD_RELATION_INVALID:'entryInvalid',WORLD_ENTRY_NOT_EMPTY:'entryNotEmpty',WORLD_PUBLICATION_REQUIRED:'publishRequired',WORLD_BIND_AT_STORY:'useBindingPanel',WORLD_USE_BINDING_PANEL:'useBindingPanel',WORLD_PUBLISH_REQUEST_CONFLICT:'bindingConflict',WORKFLOW_VERSION_CONFLICT:'bindingConflict'};
  if (code && keys[code]) return t(`world2.${keys[code]}`);
  if (code === "WORLD_VERSION_CONFLICT") return t("worlds.taskConflict");
  if (code === "WORLD_TASK_BASELINE_REQUIRED") return t("worlds.taskBaselineMissing");
  if (code === "WORLD_EMPTY_CANDIDATE" || code === 'WORLD_AI_EMPTY_RESULT') return t("worlds.emptyCandidate");
  return null;
};

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
  return modelFailure(code) || worldFailure(code) || (code && sceneFailureMessage(code)) || (code && OPERATION_MESSAGES[code]) || t("errors.operationFailed");
}

function sceneFailureMessage(code: string): string | undefined {
  if (["STORY_RESOURCE_MISSING", "STORY_PARENT_CHANGED", "STORY_RESOURCE_ALREADY_EXISTS", "STORY_PROPOSAL_STALE", "STORY_RESOURCE_CONFLICT", "RESOURCE_VERSION_CONFLICT", "STORY_PROPOSAL_VERSION_CONFLICT", "STORY_PROPOSAL_DECIDED", "STORY_CREATE_UNDO_UNSUPPORTED", "STORY_ASSISTANT_CONTEXT_TOO_LARGE", "STORY_TARGET_NOT_READ", "STORY_ASSISTANT_SOURCES_INVALID", "STORY_OUTLINE_REQUIRED"].includes(code)) return t(`storyAssistant.reason.${code}`);
  const keys: Record<string, string> = {
    ASSISTANT_FORMAT_PRESERVATION_LIMIT: "assistant.formatLimit",
    ASSISTANT_SELECTION_INVALID: "assistant.invalidSource",
    ASSISTANT_SELECTION_UNSUPPORTED: "assistant.unsupportedSelection",
    ASSISTANT_REPLACEMENTS_INVALID: "assistant.invalidResult",
    ASSISTANT_SOURCE_INVALID: "assistant.invalidSource",
    ASSISTANT_PROPOSAL_STALE: "assistant.proposalStale",
    ASSISTANT_BRANCH_CHANGED: "assistant.identityChanged",
    ASSISTANT_SESSION_BUSY: "assistant.writeBusy",
    ASSISTANT_TURN_IN_PROGRESS: "assistant.turnBusy",
    ASSISTANT_CONTEXT_TOO_LARGE: "assistant.contextTooLarge",
    ASSISTANT_CONTEXT_CHANGED: "assistant.proposalStale",
    ASSISTANT_H2_SAVE_REQUIRED: "assistant.h2SaveRequired",
    ASSISTANT_IDEMPOTENCY_CONFLICT: "assistant.requestConflict",
    ASSISTANT_RESULT_INVALID: "assistant.invalidResult",
    ASSISTANT_TOOL_REQUEST_INVALID: "assistant.invalidResult",
    SCENE_LENGTH_OUT_OF_RANGE: "sceneCandidates.needsReview",
    SCENE_LEGACY_RECOVERY_REQUIRED: "sceneCandidates.legacyRecovery",
    SCENE_CANDIDATE_MANUAL_SAVE_REQUIRED: "sceneCandidates.incompleteSource",
    SCENE_CANDIDATE_VERSION_CONFLICT: "sceneCandidates.stale",
    SCENE_CANDIDATE_NOT_FOUND: "sceneCandidates.notFound",
    SCENE_CANDIDATE_SOURCE_UNAVAILABLE: "sceneCandidates.sourceUnavailable",
    SCENE_PROMPT_BUDGET_EXCEEDED: "sceneCandidates.budgetExceeded",
  };
  return keys[code] ? t(keys[code]) : undefined;
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
 * - 普通 Error 仅识别已知世界错误码和报价取消，其余未知值返回 fallbackKey，不泄露原始 message。
 */
export function localizedErrorMessage(error: unknown, fallbackKey = "errors.operationFailed"): string {
  if (isApiError(error)) {
    const message = modelFailure(error.message) || worldFailure(error.message) || sceneFailureMessage(error.message) || OPERATION_MESSAGES[error.message] || (error.status === 503 ? "服务暂时不可用，输入已保留，请稍后重试。" : t(errorKeyForStatus(error.status)));
    return error.requestId && /^[a-zA-Z0-9-]{1,64}$/.test(error.requestId)
      ? `${message}（请求编号：${error.requestId}）` : message;
  }
  if (error instanceof Error && error.message === "AI_QUOTE_CANCELLED") return t("aiModels.quoteCancelled");
  if (error instanceof Error && error.message === 'WORLD_CONTEXT_CANCELLED') return t('world4.cancelled');
  return (error instanceof Error && worldFailure(error.message)) || t(fallbackKey);
}
