import { useSyncExternalStore } from "react";
import { api } from "@/lib/api-client";
import type { AiOperationAccepted, AiOperationProgress } from "@/types";

const STORAGE_KEY = "ainovel.active-ai-operation";
let current: AiOperationProgress | null = null;
const listeners = new Set<() => void>();
let watching: string | null = null;
const AI_OPERATION_TIMEOUT_MS = 180_000;
let activeWatch: {
  operationId: string;
  controller: AbortController;
  cancelRequested: boolean;
  timedOut: boolean;
} | null = null;

export class AiOperationCancelledError extends Error {
  constructor(message = "AI 操作已取消") {
    super(message);
    this.name = "AiOperationCancelledError";
  }
}

const emit = (next: AiOperationProgress | null) => {
  current = next;
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

function clearTracked(operationId: string) {
  if (watching !== operationId) return;
  watching = null;
  window.sessionStorage.removeItem(STORAGE_KEY);
  emit(null);
}

function refreshRecoveredResult(final: AiOperationProgress) {
  clearTracked(final.id);
  try {
    const result = final.resultJson ? JSON.parse(final.resultJson) : null;
    const storyId = result?.storyCard?.id;
    if (storyId) {
      window.location.assign(`/workbench?id=${encodeURIComponent(storyId)}`);
      return;
    }
  } catch { /* Other operation results are refreshed in place. */ }
  window.location.reload();
}

export const useTrackedAiOperation = () => useSyncExternalStore(subscribe, () => current, () => null);

async function watch(operationId: string): Promise<AiOperationProgress> {
  watching = operationId;
  window.sessionStorage.setItem(STORAGE_KEY, operationId);
  const controller = new AbortController();
  const watchState = { operationId, controller, cancelRequested: false, timedOut: false };
  activeWatch = watchState;
  const timeoutId = window.setTimeout(() => {
    watchState.timedOut = true;
    controller.abort();
  }, AI_OPERATION_TIMEOUT_MS);
  let final: AiOperationProgress;
  try {
    final = await api.aiOperations.wait(operationId, (progress) => {
      if (watching === operationId) emit(progress);
    }, controller.signal);
  } catch (error) {
    if (watchState.timedOut) {
      await api.aiOperations.cancel(operationId).catch(() => undefined);
      const cancelled = await api.aiOperations.get(operationId).catch(() => null);
      if (cancelled) emit(cancelled);
      clearTracked(operationId);
      throw new Error("AI 操作超时，已请求取消");
    }
    if (watchState.cancelRequested) {
      const cancelled = await api.aiOperations.get(operationId).catch(() => null);
      if (cancelled) emit(cancelled);
      clearTracked(operationId);
      throw new AiOperationCancelledError();
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
    if (activeWatch?.operationId === operationId) activeWatch = null;
  }
  if (watchState.cancelRequested || final.status === "CANCELLED") {
    clearTracked(operationId);
    throw new AiOperationCancelledError(final.errorMessage || "AI 操作已取消");
  }
  if (final.status === "SUCCEEDED") {
    window.setTimeout(() => {
      clearTracked(operationId);
    }, 4000);
  }
  return final;
}

export async function runTrackedAiOperation(start: Promise<AiOperationAccepted>): Promise<AiOperationProgress> {
  const accepted = await start;
  const final = await watch(accepted.operationId);
  if (final.status !== "SUCCEEDED") throw new Error(final.errorMessage || "AI 操作未完成");
  return final;
}

export async function cancelTrackedAiOperation(operationId: string): Promise<void> {
  if (!operationId) return;
  if (activeWatch?.operationId === operationId) activeWatch.cancelRequested = true;
  await api.aiOperations.cancel(operationId).catch(() => undefined);
  if (activeWatch?.operationId === operationId) {
    activeWatch.controller.abort();
  } else {
    const cancelled = await api.aiOperations.get(operationId).catch(() => null);
    if (cancelled) emit(cancelled);
    clearTracked(operationId);
  }
}

export async function resumeTrackedAiOperation(): Promise<void> {
  const id = window.sessionStorage.getItem(STORAGE_KEY);
  if (!id || watching === id) return;
  try {
    const final = await watch(id);
    if (final.status === "SUCCEEDED") refreshRecoveredResult(final);
  } catch { /* The panel keeps the terminal error state. */ }
}

export async function retryTrackedAiOperation(id: string): Promise<void> {
  await api.aiOperations.retry(id);
  const final = await watch(id);
  if (final.status === "SUCCEEDED") refreshRecoveredResult(final);
}
