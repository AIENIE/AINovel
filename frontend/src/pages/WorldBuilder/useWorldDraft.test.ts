import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api-client";
import type { WorldDetail } from "@/types";
import { useWorldDraft } from "./useWorldDraft";
import { worldContent } from "./world-draft";

const world = (id = "a", revision = 1, overrides: Partial<WorldDetail> = {}): WorldDetail => ({
  id, revision, name: id, tagline: "", themes: [], creativeIntent: "", notes: "", status: "draft", version: "1", updatedAt: "", modules: [{ key: "geo", fields: { terrain: "old", climate: "cold" } }], ...overrides,
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe("useWorldDraft", () => {
  it("preserves timeline edits made while an earlier timeline save completes", async () => {
    const event={id:'event',title:'启航',summary:'',content:'原文',aliases:[],tags:[],date:null,entryIds:[],eraIds:[],sortOrder:0};
    const first=world('a',1,{entries:[],relations:[],timeline:{schemaVersion:1,calendarName:'潮历',eras:[],events:[event]}});
    vi.spyOn(api.worlds,'getDetail').mockResolvedValue(first);const pending=deferred<WorldDetail>();vi.spyOn(api.worlds,'update').mockReturnValue(pending.promise);
    const {result}=renderHook(()=>useWorldDraft('a'));await waitFor(()=>expect(result.current.loading).toBe(false));
    act(()=>result.current.edit(draft=>({...draft,timeline:{...draft.timeline!,calendarName:'新潮历'}})));let save!:Promise<boolean>;act(()=>{save=result.current.save();});
    act(()=>result.current.edit(draft=>({...draft,timeline:{...draft.timeline!,events:[{...event,content:'保存期间继续编辑'}]}})));
    await act(async()=>{pending.resolve({...first,revision:2,timeline:{...first.timeline!,calendarName:'新潮历'}});expect(await save).toBe(false);});
    expect(result.current.draft?.timeline?.events[0].content).toBe('保存期间继续编辑');expect(result.current.dirty).toBe(true);expect(result.current.draft?.revision).toBe(2);
  });
  it("keeps newer typing dirty when an earlier save completes and uses the returned revision for the next save", async () => {
    vi.spyOn(api.worlds, "getDetail").mockResolvedValue(world());
    const pending = deferred<WorldDetail>();
    const update = vi.spyOn(api.worlds, "update").mockReturnValueOnce(pending.promise).mockImplementation(async (_id, draft) => ({ ...draft, revision: 3 }));
    const { result } = renderHook(() => useWorldDraft("a"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.edit(draft => ({ ...draft, notes: "submitted" })));
    let save!: Promise<boolean>;
    act(() => { save = result.current.save(); });
    act(() => result.current.edit(draft => ({ ...draft, notes: "typed during save" })));
    await act(async () => { pending.resolve(world("a", 2, { notes: "submitted" })); expect(await save).toBe(false); });
    expect(result.current.draft?.notes).toBe("typed during save");
    expect(result.current.draft?.revision).toBe(2);
    expect(result.current.dirty).toBe(true);
    await act(async () => { expect(await result.current.save()).toBe(true); });
    expect(update).toHaveBeenLastCalledWith("a", expect.objectContaining({ notes: "typed during save", revision: 2 }));
  });

  it("retains local input after failure, then explicitly rebases changed fields onto the reviewed server copy", async () => {
    const get = vi.spyOn(api.worlds, "getDetail").mockResolvedValueOnce(world()).mockResolvedValue(world("a", 2, { notes: "remote notes", modules: [{ key: "geo", fields: { terrain: "remote terrain", climate: "warm" } }] }));
    vi.spyOn(api.worlds, "update").mockRejectedValue(new ApiError(409, "WORLD_VERSION_CONFLICT"));
    const { result } = renderHook(() => useWorldDraft("a"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.edit(draft => ({ ...draft, modules: [{ key: "geo", fields: { terrain: "my terrain", climate: "cold" } }] })));
    await act(async () => { expect(await result.current.save()).toBe(false); });
    expect(result.current.draft?.modules[0].fields.terrain).toBe("my terrain");
    expect(result.current.remote?.revision).toBe(2);
    expect(result.current.canAct()).toBe(false);
    act(() => result.current.resolveRemote(true));
    expect(result.current.draft).toMatchObject({ revision: 2, notes: "remote notes", modules: [{ key: "geo", fields: { terrain: "my terrain", climate: "warm" } }] });
    expect(result.current.dirty).toBe(true);
    expect(result.current.conflict).toBe(false);
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("retains a dirty draft on offline save failure", async () => {
    vi.spyOn(api.worlds, "getDetail").mockResolvedValue(world());
    vi.spyOn(api.worlds, "update").mockRejectedValue(new Error("offline"));
    const { result } = renderHook(() => useWorldDraft("a"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.edit(draft => ({ ...draft, notes: "keep me" })));
    await act(async () => { expect(await result.current.save()).toBe(false); });
    expect(result.current.draft?.notes).toBe("keep me");
    expect(result.current.dirty).toBe(true);
    expect(result.current.saving).toBe(false);
  });

  it("queues a task refresh during saving and preserves typing against its newer server content", async () => {
    const get = vi.spyOn(api.worlds, "getDetail").mockResolvedValueOnce(world()).mockResolvedValue(world("a", 3, { notes: "submitted", modules: [{ key: "geo", fields: { terrain: "generated", climate: "cold" } }] }));
    const pending = deferred<WorldDetail>();
    vi.spyOn(api.worlds, "update").mockReturnValue(pending.promise);
    const { result } = renderHook(() => useWorldDraft("a"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.edit(draft => ({ ...draft, notes: "submitted" })));
    let save!: Promise<boolean>;
    act(() => { save = result.current.save(); });
    await act(async () => { await result.current.refresh(); });
    expect(get).toHaveBeenCalledTimes(1);
    act(() => result.current.edit(draft => ({ ...draft, notes: "later" })));
    await act(async () => { pending.resolve(world("a", 2, { notes: "submitted" })); expect(await save).toBe(false); });
    expect(result.current.draft?.notes).toBe("later");
    expect(result.current.baseline?.revision).toBe(2);
    expect(result.current.remote?.revision).toBe(3);
  });

  it("ignores reads started before save and revisions older than the current server notice", async () => {
    const oldRead = deferred<WorldDetail>();
    const get = vi.spyOn(api.worlds, "getDetail").mockResolvedValueOnce(world()).mockReturnValueOnce(oldRead.promise);
    vi.spyOn(api.worlds, "update").mockImplementation(async (_id, draft) => ({ ...draft, revision: 2 }));
    const { result } = renderHook(() => useWorldDraft("a"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    let read!: Promise<void>;
    act(() => { read = result.current.refresh(); });
    act(() => result.current.edit(draft => ({ ...draft, notes: "saved" })));
    await act(async () => { await result.current.save(); });
    await act(async () => { oldRead.resolve(world()); await read; });
    expect(result.current.draft?.notes).toBe("saved");
    act(() => result.current.edit(draft => ({ ...draft, notes: "local" })));
    get.mockResolvedValueOnce(world("a", 4));
    await act(async () => { await result.current.refresh(); });
    get.mockResolvedValueOnce(world("a", 3));
    await act(async () => { await result.current.refresh(); });
    expect(result.current.remote?.revision).toBe(4);
  });

  it("ignores the original A save after navigating A to B to A", async () => {
    vi.spyOn(api.worlds, "getDetail").mockImplementation(async id => world(id, id === "b" ? 5 : 1));
    const pending = deferred<WorldDetail>();
    vi.spyOn(api.worlds, "update").mockReturnValue(pending.promise);
    const { result, rerender } = renderHook(({ id }) => useWorldDraft(id), { initialProps: { id: "a" } });
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.edit(draft => ({ ...draft, notes: "original A" })));
    let save!: Promise<boolean>;
    act(() => { save = result.current.save(); });
    rerender({ id: "b" });
    await waitFor(() => expect(result.current.draft?.id).toBe("b"));
    rerender({ id: "a" });
    await waitFor(() => expect(result.current.draft?.id).toBe("a"));
    act(() => result.current.edit(draft => ({ ...draft, notes: "new A session" })));
    await act(async () => { pending.resolve(world("a", 2, { notes: "original A" })); expect(await save).toBe(false); });
    expect(result.current.draft?.notes).toBe("new A session");
    expect(result.current.draft?.revision).toBe(1);
  });

  it("ignores old-scope refresh callbacks without invalidating the new world's load", async () => {
    const first = deferred<WorldDetail>(), second = deferred<WorldDetail>();
    const get = vi.spyOn(api.worlds, "getDetail").mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { result, rerender } = renderHook(({ id }) => useWorldDraft(id), { initialProps: { id: "a" } });
    const oldRefresh = result.current.refresh;
    rerender({ id: "b" });
    await act(async () => { await oldRefresh(); first.resolve(world("a")); second.resolve(world("b")); });
    expect(result.current.draft?.id).toBe("b");
    expect(result.current.loading).toBe(false);
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("refuses to report save success if the route changes during its queued refresh", async () => {
    const pendingSave = deferred<WorldDetail>(), pendingRefresh = deferred<WorldDetail>();
    vi.spyOn(api.worlds, "getDetail").mockResolvedValueOnce(world()).mockReturnValueOnce(pendingRefresh.promise).mockResolvedValue(world("b"));
    vi.spyOn(api.worlds, "update").mockReturnValue(pendingSave.promise);
    const { result, rerender } = renderHook(({ id }) => useWorldDraft(id), { initialProps: { id: "a" } });
    await waitFor(() => expect(result.current.loading).toBe(false));
    let save!: Promise<boolean>;
    act(() => { save = result.current.save(); });
    await act(async () => { await result.current.refresh(); pendingSave.resolve(world("a", 2)); });
    rerender({ id: "b" });
    await waitFor(() => expect(result.current.draft?.id).toBe("b"));
    await act(async () => { pendingRefresh.resolve(world("a", 2)); expect(await save).toBe(false); });
  });

  it("treats reordered module and field maps as the same content", () => {
    const a = world("a", 1, { modules: [{ key: "geo", fields: { a: "a", b: "b" } }, { key: "society", fields: { x: "x" } }] });
    const b = world("a", 2, { modules: [{ key: "society", fields: { x: "x" } }, { key: "geo", fields: { b: "b", a: "a" } }] });
    expect(worldContent(a)).toBe(worldContent(b));
  });
});
