import { useCallback, useEffect, useRef, useState } from "react";
import { api, isApiError } from "@/lib/api-client";
import type { WorldDetail } from "@/types";
import { mergeLocalWorldChanges, worldContent } from "./world-draft";

type DraftState = { id: string; draft: WorldDetail | null; baseline: WorldDetail | null; remote: WorldDetail | null; loading: boolean; saving: boolean; error: unknown; conflict: boolean };
const empty = (id: string): DraftState => ({ id, draft: null, baseline: null, remote: null, loading: !!id, saving: false, error: null, conflict: false });

export function useWorldDraft(id: string) {
  const [state, setState] = useState(() => empty(id));
  const current = useRef(state);
  const activeId = useRef(id); activeId.current = id;
  const request = useRef(0);
  const epoch = useRef(0);
  const refreshPending = useRef(false);
  const mounted = useRef(true);
  const commit = useCallback((next: DraftState) => { current.current = next; setState(next); }, []);
  useEffect(() => { const pendingRequest = request; mounted.current = true; return () => { mounted.current = false; pendingRequest.current++; }; }, []);

  const refresh = useCallback(async () => {
    if (!id || activeId.current !== id || current.current.id !== id) return;
    if (current.current.id === id && current.current.saving) { refreshPending.current = true; return; }
    const scope = epoch.current;
    const sequence = ++request.current;
    try {
      const server = await api.worlds.getDetail(id);
      if (!mounted.current || activeId.current !== id || scope !== epoch.current || sequence !== request.current) return;
      if (server.id !== id || !Number.isInteger(server.revision)) throw new Error("WORLD_REVISION_REQUIRED");
      const latest = current.current;
      if (latest.id !== id) return;
      if (server.revision < Math.max(latest.baseline?.revision ?? -1, latest.remote?.revision ?? -1)) return;
      const dirty = latest.draft && latest.baseline && worldContent(latest.draft) !== worldContent(latest.baseline);
      if (dirty) {
        commit({ ...latest, loading: false, remote: server.revision !== latest.baseline!.revision ? server : latest.remote, error: null });
      } else {
        commit({ ...latest, draft: server, baseline: server, remote: null, loading: false, conflict: false, error: null });
      }
    } catch (error) {
      if (mounted.current && activeId.current === id && scope === epoch.current && sequence === request.current) commit({ ...current.current, loading: false, error });
    }
  }, [id, commit]);

  useEffect(() => { const lifecycle = epoch, pendingRequest = request; lifecycle.current++; refreshPending.current = false; commit(empty(id)); void refresh(); return () => { lifecycle.current++; pendingRequest.current++; }; }, [id, commit, refresh]);

  const edit = useCallback((update: (draft: WorldDetail) => WorldDetail) => {
    const latest = current.current;
    if (latest.id === activeId.current && latest.draft) commit({ ...latest, draft: update(latest.draft) });
  }, [commit]);

  const save = useCallback(async (): Promise<boolean> => {
    const start = current.current;
    if (!start.draft || start.id !== activeId.current || start.saving || start.remote || start.conflict) return false;
    const submitted = start.draft;
    const scope = epoch.current;
    request.current++;
    commit({ ...start, saving: true, error: null });
    try {
      const saved = await api.worlds.update(start.id, submitted);
      if (!mounted.current || scope !== epoch.current || activeId.current !== start.id || current.current.id !== start.id) return false;
      if (saved.id !== start.id || !Number.isInteger(saved.revision) || saved.revision < submitted.revision) throw new Error("WORLD_SAVE_RESPONSE_MISMATCH");
      const latest = current.current;
      const draft = latest.draft === submitted ? saved : { ...latest.draft!, revision: saved.revision };
      commit({ ...latest, draft, baseline: saved, saving: false, error: null });
      if (refreshPending.current) { refreshPending.current = false; await refresh(); }
      if (!mounted.current || scope !== epoch.current || activeId.current !== start.id || current.current.id !== start.id) return false;
      return !current.current.remote && worldContent(current.current.draft!) === worldContent(current.current.baseline!);
    } catch (error) {
      if (mounted.current && scope === epoch.current && activeId.current === start.id && current.current.id === start.id) {
        const conflict = isApiError(error) && error.status === 409;
        commit({ ...current.current, saving: false, error, conflict });
        if (conflict || refreshPending.current) { refreshPending.current = false; await refresh(); }
      }
      return false;
    }
  }, [commit, refresh]);

  const resolveRemote = useCallback((keepLocal: boolean) => {
    const latest = current.current;
    if (!latest.remote || !latest.draft || !latest.baseline || latest.saving) return;
    const draft = keepLocal ? mergeLocalWorldChanges(latest.baseline, latest.draft, latest.remote) : latest.remote;
    commit({ ...latest, draft, baseline: latest.remote, remote: null, conflict: false, error: null });
  }, [commit]);

  const visible = state.id === id ? state : empty(id);
  const dirty = !!visible.draft && !!visible.baseline && worldContent(visible.draft) !== worldContent(visible.baseline);
  const canAct = () => {
    const latest = current.current;
    return latest.id === activeId.current && !!latest.draft && !!latest.baseline && !latest.saving && !latest.remote && !latest.conflict && worldContent(latest.draft) === worldContent(latest.baseline);
  };
  return { ...visible, dirty, edit, save, refresh, resolveRemote, canAct };
}
