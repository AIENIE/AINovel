import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { WorldDetail } from "@/types";

afterEach(() => vi.unstubAllGlobals());
describe("world atomic save", () => {
  const detail: WorldDetail = { id: "world-1", revision: 8, name: "海上城", tagline: "船就是国土", themes: ["迁徙"], creativeIntent: "世界独立构建", notes: "作者备注", status: "draft", version: "0.1", updatedAt: "", modules: [{ key: "geography", fields: { terrain: "群岛", climate: "" } }, { key: "society", fields: { politics: "议会" } }] };
  it("sends metadata, entries and relations, and expected revision in exactly one PUT", async () => {
    const fetch = vi.fn(async (_url: unknown, options?: RequestInit) => new Response(JSON.stringify({ world: {...detail,modules:{geography:{terrain:"群岛",climate:""},society:{politics:"议会"}},revision:9},entries:[],relations:[],unpublishedChanges:true })));
    vi.stubGlobal("fetch", fetch);
    const saved = await api.worlds.update(detail.id, detail);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String(fetch.mock.calls[0][0])).toMatch(/\/v1\/worlds\/world-1\/draft$/);
    expect(fetch.mock.calls[0][1]?.method).toBe("PUT");
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ metadata: {name: detail.name, tagline: detail.tagline, themes: detail.themes, creativeIntent: detail.creativeIntent, notes: detail.notes}, expectedRevision: 8, entries: [], relations: [] });
    expect(saved.revision).toBe(9);
    expect(saved.modules).toEqual(detail.modules);
  });
  it("does not send a mutation when the loaded revision is unavailable", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    await expect(api.worlds.update(detail.id, { ...detail, revision: undefined as never })).rejects.toThrow("WORLD_REVISION_REQUIRED");
    expect(fetch).not.toHaveBeenCalled();
  });
  it("round-trips an explicit timeline in the same revision-checked atomic draft write",async()=>{
    const timeline={schemaVersion:1,calendarName:'潮历',eras:[],events:[{id:'event',title:'启航',summary:'',content:'全文',aliases:[],tags:[],date:{start:{year:0},end:null},entryIds:[],eraIds:[],sortOrder:0}]};
    const fetch=vi.fn(async()=>new Response(JSON.stringify({world:{...detail,modules:{},revision:9},entries:[],relations:[],timeline})));vi.stubGlobal('fetch',fetch);
    const saved=await api.worlds.update(detail.id,{...detail,timeline});expect(saved.timeline).toEqual(timeline);expect(JSON.parse(String((fetch.mock.calls as unknown as [string,RequestInit][])[0][1].body))).toMatchObject({expectedRevision:8,timeline});
  });
  it("keeps era selection revisions separate and sends the explicit bound version",async()=>{
    const body={worldId:'world',worldVersionId:'version',expectedBindingRevision:4,expectedRevision:7,eraIds:['era-a','era-c']};
    const fetch=vi.fn(async(_url:string,_init?:RequestInit)=>new Response('{}'));vi.stubGlobal('fetch',fetch);await api.worldBindings.saveEras('story',body);
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/stories/story/world-eras');expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual(body);expect(fetch.mock.calls[0][1]?.method).toBe('PUT');
  });
  it("exports a chosen immutable source with authenticated download metadata",async()=>{
    const fetch=vi.fn(async(_url:string)=>new Response('完整设定',{headers:{'Content-Disposition':"attachment; filename*=UTF-8''%E7%BE%A4%E5%B2%9B.json"}}));vi.stubGlobal('fetch',fetch);
    const download=await api.worlds.export('world',{source:'VERSION',versionId:'version',format:'JSON'});expect(fetch.mock.calls[0][0]).toBe('/api/v1/worlds/world/export?source=VERSION&versionId=version&format=JSON');expect(download.fileName).toBe('群岛.json');expect(await download.blob.text()).toBe('完整设定');
  });
});
