import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { WorldDetail } from "@/types";

afterEach(() => vi.unstubAllGlobals());
describe("world atomic save", () => {
  const detail: WorldDetail = { id: "world-1", revision: 8, name: "海上城", tagline: "船就是国土", themes: ["迁徙"], creativeIntent: "世界独立构建", notes: "作者备注", status: "draft", version: "0.1", updatedAt: "", modules: [{ key: "geography", fields: { terrain: "群岛", climate: "" } }, { key: "society", fields: { politics: "议会" } }] };
  it("sends metadata, all module fields, and expected revision in exactly one PUT", async () => {
    const fetch = vi.fn(async (_url: unknown, options?: RequestInit) => new Response(JSON.stringify({ ...JSON.parse(String(options?.body)), id: "world-1", revision: 9 })));
    vi.stubGlobal("fetch", fetch);
    const saved = await api.worlds.update(detail.id, detail);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String(fetch.mock.calls[0][0])).toMatch(/\/v1\/worlds\/world-1$/);
    expect(fetch.mock.calls[0][1]?.method).toBe("PUT");
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ name: detail.name, tagline: detail.tagline, themes: detail.themes, creativeIntent: detail.creativeIntent, notes: detail.notes, expectedRevision: 8, modules: { geography: { terrain: "群岛", climate: "" }, society: { politics: "议会" } } });
    expect(saved.revision).toBe(9);
    expect(saved.modules).toEqual(detail.modules);
  });
  it("does not send a mutation when the loaded revision is unavailable", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    await expect(api.worlds.update(detail.id, { ...detail, revision: undefined as never })).rejects.toThrow("WORLD_REVISION_REQUIRED");
    expect(fetch).not.toHaveBeenCalled();
  });
});
