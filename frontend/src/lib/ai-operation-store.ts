import { useSyncExternalStore } from "react";
import { api } from "@/lib/api-client";
import type { AiOperationAccepted, AiOperationProgress } from "@/types";
import { t } from "@/i18n";

const STORAGE_KEY = "ainovel.active-ai-operation";
let userScope = "";
let visibility: "open" | "minimized" | "closed" = "open";
const storageKey = () => `${STORAGE_KEY}:${userScope}`;
const visibilityKey = () => `${storageKey()}:visibility`;
let current: AiOperationProgress | null = null;
const listeners = new Set<() => void>();
let watching: string | null = null;
let activeWatch: {
  operationId: string;
  controller: AbortController;
  cancelRequested: boolean;
} | null = null;

export class AiOperationCancelledError extends Error {
  constructor(message = t("aiOperation.cancelled")) {
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

export function setTrackedAiUser(userId: string | null) {
  const next = userId || "";
  if (next === userScope) return;
  activeWatch?.controller.abort();
  activeWatch = null; watching = null; userScope = next;
  visibility = "open";
  emit(null);
}

export const useTrackedAiVisibility = () => useSyncExternalStore(subscribe, () => visibility, () => "open");
export function setTrackedAiVisibility(mode: "open" | "minimized" | "closed") {
  visibility = mode;
  if (current) window.sessionStorage.setItem(visibilityKey(), JSON.stringify({ id: current.id, mode }));
  listeners.forEach((listener) => listener());
}

export async function refreshTrackedAiOperation(id: string) {
  const scope = userScope;
  const result = await api.aiOperations.get(id);
  if (scope === userScope && watching === id) emit(result);
}

function clearTracked(operationId: string) {
  if (watching !== operationId) return;
  watching = null;
  window.sessionStorage.removeItem(storageKey());
  window.sessionStorage.removeItem(visibilityKey());
  emit(null);
}

function refreshRecoveredResult(final: AiOperationProgress) {
  clearTracked(final.id);
  try {
    const result = final.resultJson ? JSON.parse(final.resultJson) : null;
    const storyId = result?.storyCard?.id;
    if (storyId) {
      window.location.assign(`/workbench?storyId=${encodeURIComponent(storyId)}`);
      return;
    }
  } catch { /* Other operation results are refreshed in place. */ }
  window.location.reload();
}

export const useTrackedAiOperation = () => useSyncExternalStore(subscribe, () => current, () => null);

async function watch(operationId: string): Promise<AiOperationProgress> {
  watching = operationId;
  window.sessionStorage.setItem(storageKey(), operationId);
  try {
    const saved = JSON.parse(window.sessionStorage.getItem(visibilityKey()) || "null");
    visibility = saved?.id === operationId && ["open", "minimized", "closed"].includes(saved.mode) ? saved.mode : "open";
  } catch { visibility = "open"; }
  const controller = new AbortController();
  const watchState = { operationId, controller, cancelRequested: false };
  activeWatch = watchState;
  let final: AiOperationProgress;
  try {
    final = await api.aiOperations.wait(operationId, (progress) => {
      if (watching === operationId) emit(progress);
    }, controller.signal);
  } catch (error) {
    if (watchState.cancelRequested) {
      const cancelled = await api.aiOperations.get(operationId).catch(() => null);
      if (cancelled) emit(cancelled);
      clearTracked(operationId);
      throw new AiOperationCancelledError();
    }
    throw error;
  } finally {
    if (activeWatch?.operationId === operationId) activeWatch = null;
  }
  if (watchState.cancelRequested || final.status === "CANCELLED") {
    clearTracked(operationId);
    throw new AiOperationCancelledError(final.errorMessage || t("aiOperation.cancelled"));
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
  if (final.status !== "SUCCEEDED") throw new Error(final.errorMessage || t("aiOperation.incomplete"));
  return final;
}

export async function cancelTrackedAiOperation(operationId: string): Promise<void> {
  if (!operationId) return;
  const outcome = await api.aiOperations.cancel(operationId);
  emit(outcome);
  if (outcome.status !== "CANCELLED") return;
  if (activeWatch?.operationId === operationId) activeWatch.cancelRequested = true;
  if (activeWatch?.operationId === operationId) {
    activeWatch.controller.abort();
  } else {
    const cancelled = await api.aiOperations.get(operationId).catch(() => null);
    if (cancelled) emit(cancelled);
    clearTracked(operationId);
  }
}

export async function resumeTrackedAiOperation(): Promise<void> {
  const id = window.sessionStorage.getItem(storageKey());
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
