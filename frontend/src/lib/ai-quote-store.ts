import type { AiQuote } from "./ai-model-config";

export type PendingAiQuote = {
  quote: AiQuote;
  refresh: (selections: Record<string, string>) => Promise<AiQuote>;
  remember: (selections: Record<string, string>) => Promise<void>;
  resolve: (id: string) => void;
  reject: (error: Error) => void;
};
const listeners = new Set<() => void>();
let queue: PendingAiQuote[] = [];
const notify = () => listeners.forEach((listener) => listener());
export const subscribeAiQuotes = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
export const pendingAiQuote = () => queue[0] ?? null;
export function requestAiQuote(quote: AiQuote, refresh: PendingAiQuote["refresh"], remember: PendingAiQuote["remember"], signal?: AbortSignal | null) {
  return new Promise<string>((resolve, reject) => {
    const pending: PendingAiQuote = { quote, refresh, remember, resolve, reject };
    const abort = () => { queue = queue.filter((item) => item !== pending); reject(new Error("AI_QUOTE_CANCELLED")); notify(); };
    if (signal?.aborted) { abort(); return; }
    signal?.addEventListener("abort", abort, { once: true });
    pending.resolve = (id) => { signal?.removeEventListener("abort", abort); resolve(id); };
    pending.reject = (error) => { signal?.removeEventListener("abort", abort); reject(error); };
    queue = [...queue, pending]; notify();
  });
}
export function finishAiQuote(pending: PendingAiQuote, id?: string) {
  queue = queue.filter((item) => item !== pending);
  if (id) pending.resolve(id); else pending.reject(new Error("AI_QUOTE_CANCELLED"));
  notify();
}
