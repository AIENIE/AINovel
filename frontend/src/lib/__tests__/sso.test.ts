import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { buildSsoUrl, issueSsoState, validateSsoState } from "@/lib/sso";

const createSessionStorage = () => {
  const store: Record<string, string> = {};
  return {
    getItem: (k: string) => (k in store ? store[k] : null),
    setItem: (k: string, v: string) => {
      store[k] = String(v);
    },
    removeItem: (k: string) => {
      delete store[k];
    },
    clear: () => {
      Object.keys(store).forEach((k) => delete store[k]);
    },
  };
};

describe("sso helpers", () => {
  beforeEach(() => {
    const sessionStorage = createSessionStorage();
    vi.stubGlobal("window", {
      location: { origin: "http://ainovel.seekerhut.com" },
      sessionStorage,
    });
    vi.stubGlobal("sessionStorage", sessionStorage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("builds backend sso entry url", () => {
    const url = buildSsoUrl("login", "/workbench", "state123");
    expect(url).toBe("http://ainovel.seekerhut.com/api/v1/sso/login?next=%2Fworkbench&state=state123");
  });

  it("issues and validates one-time state", () => {
    const state = issueSsoState();
    expect(state.length).toBeGreaterThan(0);
    expect(validateSsoState(state)).toBe(true);
    expect(validateSsoState(state)).toBe(false);
  });

  it("rejects mismatched state", () => {
    issueSsoState();
    expect(validateSsoState("mismatch")).toBe(false);
  });
});
