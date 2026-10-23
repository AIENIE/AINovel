import { sceneGenerationOutcome, SCENE_CANDIDATES_EVENT } from "./scene-candidates";
import { useSyncExternalStore } from "react";
import { api } from "@/lib/api-client";
import type { AiOperationAccepted, AiOperationProgress } from "@/types";
import { t } from "@/i18n";
import { STORY_ASSISTANT_UPDATED } from "./story-assistant-events";
import { WORLD_UPDATED, type WorldUpdatedDetail } from "./world-operation-events";

const STORAGE_KEY = "ainovel.active-ai-operation";
let userScope = "";
let visibility: "open" | "minimized" | "closed" = "open";
const storageKey = () => `${STORAGE_KEY}:${userScope}`;
const visibilityKey = () => `${storageKey()}:visibility`;
let current: AiOperationProgress | null = null;
const listeners = new Set<() => void>();
let watching: string | null = null;
let watchRevision = 0;
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
  watchRevision++;
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
  const revision = watchRevision;
  const result = await api.aiOperations.get(id);
  if (scope !== userScope || watching !== id || revision !== watchRevision) return;
  if (result.scopeType === "WORLD" && ["SUCCEEDED", "FAILED", "RECOVERY_REQUIRED", "CANCELLED"].includes(result.status)) {
    // A completed read supersedes older stream snapshots, including a disconnected watcher.
    const completedRevision = ++watchRevision;
    emit(result);
    if (result.status === "SUCCEEDED") {
      notifyWorldUpdated(result);
      window.setTimeout(() => {
        if (scope === userScope && watching === id && watchRevision === completedRevision) clearTracked(id);
      }, 4000);
    }
    return;
  }
  emit(result);
}

function clearTracked(operationId: string) {
  if (watching !== operationId) return;
  watching = null;
  window.sessionStorage.removeItem(storageKey());
  window.sessionStorage.removeItem(visibilityKey());
  emit(null);
}

function refreshRecoveredResult(final: AiOperationProgress) {
  // World watchers notify the mounted editor without discarding its local draft.
  if (final.scopeType === "WORLD") return;
  if (final.operationType === "WORLD_ASSISTANT_TURN") {
    window.dispatchEvent(new CustomEvent("ainovel:world-assistant-updated"));
    return;
  }
  if (final.operationType === "STORY_ASSISTANT_TURN") {
    window.dispatchEvent(new CustomEvent(STORY_ASSISTANT_UPDATED));
    return;
  }
  if (final.operationType === "WRITING_ASSISTANT_TURN") {
    window.dispatchEvent(new CustomEvent("ainovel:writing-assistant-updated"));
    return;
  }
  const scene = sceneGenerationOutcome(final.resultJson);
  if (scene) {
    window.dispatchEvent(new CustomEvent(SCENE_CANDIDATES_EVENT, { detail: scene }));
    return;
  }
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

function notifyWorldUpdated(final: AiOperationProgress) {
  if (final.scopeType !== "WORLD") return;
  let worldId = final.scopeId?.trim();
  if (!worldId) {
    try {
      const result: unknown = final.resultJson ? JSON.parse(final.resultJson) : null;
      if (result && typeof result === "object" && "id" in result && typeof result.id === "string") {
        worldId = result.id.trim();
      }
    } catch { /* A missing result cannot identify an editor to refresh. */ }
  }
  if (!worldId) return;
  window.dispatchEvent(new CustomEvent<WorldUpdatedDetail>(WORLD_UPDATED, {
    detail: { worldId, operationId: final.id },
  }));
}

async function watch(operationId: string): Promise<AiOperationProgress> {
  const scope = userScope;
  const revision = ++watchRevision;
  const isCurrentWatch = () => scope === userScope && watching === operationId && watchRevision === revision;
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
      if (isCurrentWatch()) emit(progress);
    }, controller.signal);
  } catch (error) {
    if (watchState.cancelRequested) {
      const cancelled = await api.aiOperations.get(operationId).catch(() => null);
      if (isCurrentWatch()) {
        if (cancelled) emit(cancelled);
        clearTracked(operationId);
      }
      throw new AiOperationCancelledError();
    }
    throw error;
  } finally {
    if (activeWatch === watchState) activeWatch = null;
  }
  if (watchState.cancelRequested || final.status === "CANCELLED") {
    if (isCurrentWatch()) clearTracked(operationId);
    throw new AiOperationCancelledError(final.errorMessage || t("aiOperation.cancelled"));
  }
  if (final.status === "SUCCEEDED" && isCurrentWatch()) {
    notifyWorldUpdated(final);
    window.setTimeout(() => {
      if (isCurrentWatch()) clearTracked(operationId);
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
  const scope = userScope;
  const revision = watchRevision;
  await api.aiOperations.retry(id);
  if (scope !== userScope || revision !== watchRevision) return;
  const final = await watch(id);
  if (final.status === "SUCCEEDED") refreshRecoveredResult(final);
}
