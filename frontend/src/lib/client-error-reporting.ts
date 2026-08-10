export type ClientErrorSummary = {
  source: string;
  errorType: string;
  count: 1;
};

let globalHandlersInstalled = false;

function safeSource(source: string): string {
  return /^[a-z][a-z0-9._-]{0,63}$/i.test(source) ? source : "unknown";
}

function safeErrorType(error: unknown): string {
  if (!(error instanceof Error)) {
    return "NonErrorRejection";
  }
  return /^[A-Za-z][A-Za-z0-9]{0,63}$/.test(error.name) ? error.name : "Error";
}

export function summarizeClientError(error: unknown, source: string): ClientErrorSummary {
  return {
    source: safeSource(source),
    errorType: safeErrorType(error),
    count: 1,
  };
}

export function reportClientError(error: unknown, source: string): void {
  const summary = summarizeClientError(error, source);
  // Never pass the original Error object: browser extensions and remote consoles may serialize it.
  console.error("[client-error]", summary);
  window.dispatchEvent(new CustomEvent<ClientErrorSummary>("aienie:client-error", { detail: summary }));
}

export function installGlobalErrorHandlers(): void {
  if (globalHandlersInstalled) {
    return;
  }
  globalHandlersInstalled = true;
  window.addEventListener("error", (event: ErrorEvent) => {
    reportClientError(event.error ?? event.message, "window.error");
  });
  window.addEventListener("unhandledrejection", (event: PromiseRejectionEvent) => {
    reportClientError(event.reason, "window.unhandledrejection");
  });
}
