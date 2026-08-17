import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import {
  AiOperationCancelledError,
  cancelTrackedAiOperation,
  runTrackedAiOperation,
} from "@/lib/ai-operation-store";
import type { AiOperationProgress } from "@/types";

describe("ai operation tracking", () => {
  afterEach(() => {
    window.sessionStorage.clear();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("keeps waiting past the former 180 second client timeout", async () => {
    vi.useFakeTimers();
    const pending = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "wait").mockReturnValue(pending.promise);
    const cancel = vi.spyOn(api.aiOperations, "cancel").mockResolvedValue(undefined);

    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "operation-long" }));
    await vi.advanceTimersByTimeAsync(10 * 60_000);

    expect(cancel).not.toHaveBeenCalled();
    pending.resolve(completedOperation("operation-long"));
    await expect(operation).resolves.toMatchObject({ status: "SUCCEEDED" });
    await vi.advanceTimersByTimeAsync(4_000);
  });

  it("still aborts the watcher when the user explicitly cancels", async () => {
    const watcherStarted = deferred<void>();
    vi.spyOn(api.aiOperations, "wait").mockImplementation((_id, _onProgress, signal) => {
      watcherStarted.resolve(undefined);
      return new Promise((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new DOMException("Operation aborted", "AbortError")));
      });
    });
    const cancel = vi.spyOn(api.aiOperations, "cancel").mockResolvedValue(undefined);
    vi.spyOn(api.aiOperations, "get").mockResolvedValue(cancelledOperation("operation-cancelled"));

    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "operation-cancelled" }));
    await watcherStarted.promise;
    await cancelTrackedAiOperation("operation-cancelled");

    await expect(operation).rejects.toBeInstanceOf(AiOperationCancelledError);
    expect(cancel).toHaveBeenCalledWith("operation-cancelled");
  });
});

function completedOperation(id: string): AiOperationProgress {
  return {
    id,
    operationType: "AINOVEL_LONG_TASK",
    status: "SUCCEEDED",
    totalSteps: 5,
    completedSteps: 5,
    remainingSteps: 0,
    currentStepOutputTokens: 3200,
    outputTokensEstimated: false,
    attemptCount: 1,
  } as AiOperationProgress;
}

function cancelledOperation(id: string): AiOperationProgress {
  return {
    ...completedOperation(id),
    status: "CANCELLED",
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}
