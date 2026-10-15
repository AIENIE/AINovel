import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/api-client";
import { retryTrackedAiOperation, runTrackedAiOperation } from "@/lib/ai-operation-store";
import type { StoryAssistantProposal, StoryAssistantSuggestion, StoryAssistantTurn, StoryAssistantTurnRequest } from "@/lib/api/domains/story-assistant";
import { STORY_ASSISTANT_UPDATED, STORY_RESOURCE_CHANGED } from "@/lib/story-assistant-events";
import { localizedErrorMessage } from "@/lib/error-messages";

const active = (turn: StoryAssistantTurn) => ["PENDING", "QUEUED", "RUNNING", "STREAMING", "PROCESSING"].includes(turn.status);
const selectionKey = (story: string) => `ainovel.story-discussion:${story}`;
const storedSession = (story: string) => { try { return sessionStorage.getItem(selectionKey(story)) || ""; } catch { return ""; } };
type Refinement = { id: string; outlineId: string | null; label: string };

export function useStoryDiscussion(storyId: string, outlineId: string) {
  const client = useQueryClient();
  const [selections, setSelections] = useState<Record<string, string>>({});
  const selectedSessionId = selections[storyId] ?? storedSession(storyId);
  const identity = `${storyId}:${selectedSessionId}`;
  const latestIdentity = useRef(identity); latestIdentity.current = identity;
  const [inputs, setInputs] = useState<Record<string, string>>({});
  const [parents, setParents] = useState<Record<string, Refinement | null>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [workingIds, setWorkingIds] = useState<Set<string>>(new Set());
  const [olderPages, setOlderPages] = useState<Record<string, { turns: StoryAssistantTurn[]; nextBefore: string | null }>>({});
  const [loadingOlder, setLoadingOlder] = useState(false);
  const requests = useRef(new Map<string, string>());
  const locks = useRef(new Set<string>());
  const chooseSession = useCallback((sessionId: string) => {
    setSelections(current => ({ ...current, [storyId]: sessionId }));
    try { sessionStorage.setItem(selectionKey(storyId), sessionId); } catch { /* The server remains the session source. */ }
  }, [storyId]);
  const sessions = useQuery({ queryKey: ["story-discussion-sessions", storyId], queryFn: ({ signal }) => api.storyAssistant.sessions(storyId, signal), enabled: !!storyId, retry: false });
  const session = useQuery({ queryKey: ["story-discussion", storyId, selectedSessionId], queryFn: ({ signal }) => api.storyAssistant.session(storyId, selectedSessionId || undefined, undefined, signal), enabled: !!storyId, retry: false,
    refetchInterval: query => query.state.data?.turns.some(active) ? 2000 : false });
  useEffect(() => { if (!selectedSessionId && session.data?.id) chooseSession(session.data.id); }, [chooseSession, selectedSessionId, session.data?.id]);
  const turns = useMemo(() => Array.from(new Map([...(olderPages[identity]?.turns || []), ...(session.data?.turns || [])].map(turn => [turn.id, turn])).values()).sort((a, b) => a.createdAt.localeCompare(b.createdAt)), [identity, olderPages, session.data]);
  const refresh = useCallback(async () => {
    await Promise.all([client.invalidateQueries({ queryKey: ["story-discussion", storyId] }), client.invalidateQueries({ queryKey: ["story-discussion-sessions", storyId] }), client.invalidateQueries({ queryKey: ["story-proposal", storyId] })]);
  }, [client, storyId]);
  useEffect(() => {
    const listener = () => { void refresh(); };
    window.addEventListener(STORY_ASSISTANT_UPDATED, listener);
    return () => window.removeEventListener(STORY_ASSISTANT_UPDATED, listener);
  }, [refresh]);
  useEffect(() => {
    const listener = () => { void client.invalidateQueries({ queryKey: ["story-proposal", storyId] }); void client.invalidateQueries({ queryKey: ["story-discussion-outlines", storyId] }); };
    window.addEventListener(STORY_RESOURCE_CHANGED, listener);
    return () => window.removeEventListener(STORY_RESOURCE_CHANGED, listener);
  }, [client, storyId]);
  const perform = async (action: () => Promise<void>) => {
    if (locks.current.has(identity)) return;
    locks.current.add(identity);
    setWorkingIds(current => new Set(current).add(identity));
    setErrors(current => ({ ...current, [identity]: "" }));
    try { await action(); return true; }
    catch (error) { setErrors(current => ({ ...current, [identity]: localizedErrorMessage(error) })); return false; }
    finally { locks.current.delete(identity); setWorkingIds(current => { const next = new Set(current); next.delete(identity); return next; }); await refresh(); }
  };
  const withKey = async <T,>(name: string, action: (key: string) => Promise<T>): Promise<T> => {
    const key = requests.current.get(name) || crypto.randomUUID(); requests.current.set(name, key);
    try { const result = await action(key); requests.current.delete(name); return result; }
    catch (error) { if (isApiError(error) && error.status >= 400 && error.status < 500 && error.status !== 408) requests.current.delete(name); throw error; }
  };
  const createSession = (title: string) => perform(async () => {
    if (!storyId || !title.trim()) return;
    const created = await withKey(`create:${storyId}:${title}`, key => api.storyAssistant.createSession(storyId, title.trim(), key));
    if (latestIdentity.current === identity) chooseSession(created.id);
  });
  const renameSession = (title: string) => perform(async () => {
    if (!selectedSessionId || !title.trim()) return;
    await withKey(`rename:${identity}:${title}`, key => api.storyAssistant.renameSession(storyId, selectedSessionId, title.trim(), key));
  });
  const input = inputs[identity] || "";
  const parent = parents[identity] || null;
  const setInput = (value: string) => setInputs(current => ({ ...current, [identity]: value }));
  const clearParent = () => setParents(current => ({ ...current, [identity]: null }));
  const send = (request: StoryAssistantTurnRequest) => perform(async () => {
    if (!storyId || !session.data?.id || turns.some(active)) return;
    const accepted = await withKey(`turn:${identity}:${JSON.stringify(request)}`, key => api.storyAssistant.submit(storyId, request, key));
    setInputs(current => ({ ...current, [identity]: "" }));
    setParents(current => ({ ...current, [identity]: null }));
    await refresh();
    await runTrackedAiOperation(Promise.resolve(accepted));
  });
  const submit = () => {
    if (!input.trim() || !session.data?.id) return Promise.resolve();
    return send({ sessionId: session.data.id, intent: parent ? "REFINE" : "DISCUSS", instruction: input.trim(), outlineId: (parent ? parent.outlineId : outlineId) || undefined, ...(parent ? { parentProposalId: parent.id } : {}) });
  };
  const propose = (turn: StoryAssistantTurn, suggestion?: StoryAssistantSuggestion) => {
    if (!session.data?.id) return Promise.resolve();
    const frozen = turn.sources.filter(source => source.type === "DISCUSSION").map(source => /^turn:([0-9a-f-]{36})$/i.exec(source.id)?.[1]).filter((value): value is string => !!value);
    const sourceTurns = frozen.length ? [...new Set([...frozen.slice(-7), turn.id])] : turns.slice(0, turns.findIndex(item => item.id === turn.id) + 1).filter(item => item.answer).slice(-8).map(item => item.id);
    return send({ sessionId: session.data.id, intent: "PROPOSE", instruction: suggestion?.title || turn.instruction, outlineId: outlineId || undefined,
      sourceTurnIds: suggestion?.sourceTurnIds.length ? suggestion.sourceTurnIds : sourceTurns.length ? sourceTurns : [turn.id], ...(suggestion ? { suggestionId: suggestion.id } : {}) });
  };
  const refine = (proposal: StoryAssistantProposal) => {
    setParents(current => ({ ...current, [identity]: { id: proposal.id, outlineId: proposal.outlineId, label: String(proposal.candidate.name || proposal.candidate.title || "") } }));
    setInput("");
  };
  const loadOlder = async () => {
    const before = olderPages[identity]?.nextBefore ?? session.data?.nextBefore;
    if (!before || !session.data?.id || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await api.storyAssistant.session(storyId, session.data.id, before);
      setOlderPages(current => ({ ...current, [identity]: { turns: [...page.turns, ...(current[identity]?.turns || [])], nextBefore: page.nextBefore } }));
    } catch (error) { setErrors(current => ({ ...current, [identity]: localizedErrorMessage(error) })); }
    finally { setLoadingOlder(false); }
  };
  return { identity, selectedSessionId, sessions, session, turns, chooseSession, createSession, renameSession, input, setInput, parent, clearParent, submit, propose, refine, refresh,
    working: workingIds.has(identity) || turns.some(active), error: errors[identity] || (session.error || sessions.error ? localizedErrorMessage(session.error || sessions.error) : ""),
    recover: (operationId: string) => perform(() => retryTrackedAiOperation(operationId)), loadOlder, loadingOlder,
    hasOlder: !!(olderPages[identity] ? olderPages[identity].nextBefore : session.data?.nextBefore) };
}
