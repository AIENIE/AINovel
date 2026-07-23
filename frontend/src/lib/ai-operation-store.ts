import { useSyncExternalStore } from "react";
import { api } from "@/lib/api-client";
import type { AiOperationAccepted, AiOperationProgress } from "@/types";

const STORAGE_KEY = "ainovel.active-ai-operation";
let current: AiOperationProgress | null = null;
const listeners = new Set<() => void>();
let watching: string | null = null;

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
  const final = await api.aiOperations.wait(operationId, (progress) => {
    if (watching === operationId) emit(progress);
  });
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
