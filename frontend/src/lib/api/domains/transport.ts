/** Shared authenticated transport is supplied by the composition root. */
export interface DomainTransport {
  json<T = unknown>(path: string, init?: RequestInit): Promise<T>;
  void(path: string, init?: RequestInit): Promise<void>;
  blob(path: string): Promise<{ blob: Blob; fileName?: string }>;
}
