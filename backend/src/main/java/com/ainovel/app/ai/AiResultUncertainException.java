package com.ainovel.app.ai;

import com.ainovel.app.common.BusinessException;

/** The legacy gateway contract cannot prove whether inference was sent or recover its result. */
public class AiResultUncertainException extends BusinessException {
    public AiResultUncertainException() { super("AI_RESULT_RECONCILIATION_REQUIRED"); }
    public AiResultUncertainException(Throwable cause) { super("AI_RESULT_RECONCILIATION_REQUIRED", cause); }
}
