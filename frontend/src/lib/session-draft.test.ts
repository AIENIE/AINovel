import { afterEach, describe, expect, it, vi } from "vitest";
import { readSessionDraft } from "./session-draft";
const valid = (value: unknown): value is { text: string } => !!value && typeof value === "object" && typeof (value as { text: unknown }).text === "string";
describe("browser draft recovery", () => {
  afterEach(() => { sessionStorage.clear(); vi.restoreAllMocks(); });
  it("distinguishes an absent draft from corrupt JSON without rewriting the record", () => {
    expect(readSessionDraft("draft", valid)).toEqual({ value: null, unreadable: false });
    for (const text of ["{broken", "[]", '{"text":12}']) {
      sessionStorage.setItem("draft", text);
      expect(readSessionDraft("draft", valid)).toEqual({ value: null, unreadable: true });
      expect(sessionStorage.getItem("draft")).toBe(text);
    }
  });
  it("recovers actual unsaved text and distinguishes a blocked storage read", () => {
    sessionStorage.setItem("draft", JSON.stringify({ text: "未提交内容" }));
    expect(readSessionDraft("draft", valid).value?.text).toBe("未提交内容");
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
    expect(readSessionDraft("draft", valid).unreadable).toBe(true);
  });
});
