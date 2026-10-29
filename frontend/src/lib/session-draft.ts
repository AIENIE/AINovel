export type SessionDraft<T> = { value: T | null; unreadable: boolean };

export function readSessionDraft<T>(key: string, valid: (value: unknown) => value is T): SessionDraft<T> {
  try {
    const text = sessionStorage.getItem(key);
    if (text === null || text === "null") return { value: null, unreadable: false };
    const value: unknown = JSON.parse(text);
    if (!valid(value)) return { value: null, unreadable: true };
    return { value, unreadable: false };
  } catch { return { value: null, unreadable: true }; }
}
