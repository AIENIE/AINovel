import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/api-client";
import { sceneContextPath } from '@/lib/api/domains/world-context';
import { reviewWorldPreview } from '@/lib/world-context-preview';
import { runTrackedAiOperation, retryTrackedAiOperation } from "@/lib/ai-operation-store";
import { AssistantInteractionError, sameAssistantDocument, type AssistantEditorTarget } from "@/lib/assistant-editor";
import type { AssistantIntent, AssistantProposal, AssistantTurn, AssistantTurnRequest } from "@/lib/api/domains/writing-assistant";
import type { Manuscript } from "@/types";
import { localizedErrorMessage } from "@/lib/error-messages";
import { t } from "@/i18n";

export const WRITING_ASSISTANT_EVENT = "ainovel:writing-assistant-updated";
const activeTurn = (turn: AssistantTurn) => ["PENDING", "QUEUED", "RUNNING", "STREAMING", "PROCESSING"].includes(turn.status);
type Options = { manuscript?: Manuscript | null; sceneId: string; sceneTitle: string; content: string; locked: boolean; busy: boolean; open: () => void;
  initializeBranch: () => Promise<Manuscript>; onSourceResolved: (manuscript: Manuscript) => void;
  reconcileWrite: (expected: string, expectedServer: string, action: (current: Manuscript) => Promise<Manuscript>) => Promise<{ manuscript: Manuscript; applied: boolean }>;
  runWrite: (expected: string, action: (current: Manuscript) => Promise<Manuscript>, saveBaseline?: boolean) => Promise<Manuscript> };
type PendingDecision = { key: string; version: number; identity: string; proposalId: string; action: "accept" | "reject" | "undo"; beforeHtml: string; afterHtml: string; uncertain: boolean };
export function useWritingAssistant(options: Options) {
  const { manuscript, sceneId, sceneTitle, content, locked, busy, runWrite, open } = options;
  const id = manuscript?.id || "", branch = manuscript?.currentBranchId || null;
  const identity = JSON.stringify([id, branch, sceneId]);
  const latest = useRef({ ...options, identity }); latest.current = { ...options, identity };
  const [input, setInput] = useState("");
  const [intent, setIntent] = useState<AssistantIntent>("AUTO");
  const [target, setTarget] = useState<AssistantEditorTarget | null>(null);
  const [parent, setParent] = useState<AssistantProposal | null>(null);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [older, setOlder] = useState<AssistantTurn[]>([]);
  const [olderCursor, setOlderCursor] = useState<string | null | undefined>(undefined);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const submissions = useRef(new Map<string, { key: string; request: AssistantTurnRequest }>());
  const decisions = useRef(new Map<string, PendingDecision>());
  const client = useQueryClient();
  const queryKey = ["writing-assistant", id, branch, sceneId];
  const initialBranch = useQuery({ queryKey: ["assistant-initial-branch", id], queryFn: options.initializeBranch, enabled: !!id && !branch, retry: false, staleTime: Infinity });
  useEffect(() => { if (!branch && initialBranch.data?.id === id) latest.current.onSourceResolved(initialBranch.data); }, [branch, id, initialBranch.data]);
  const session = useQuery({ queryKey, queryFn: ({ signal }) => api.writingAssistant.session(id, sceneId, branch, undefined, signal), enabled: !!id && !!sceneId && !!branch,
    retry: false, staleTime: 0, refetchInterval: query => query.state.data?.turns.some(activeTurn) ? 2000 : false });
  const turns = useMemo(() => Array.from(new Map([...older, ...(session.data?.turns || [])].map(turn => [turn.id, turn])).values()).sort((a, b) => a.createdAt.localeCompare(b.createdAt)), [older, session.data]);
  useEffect(() => { setInput(""); setTarget(null); setParent(null); setError(""); setNotice(""); setOlder([]); setOlderCursor(undefined); setWorking(false); setIntent("AUTO"); }, [identity]);
  useEffect(() => {
    const refresh = () => { void client.invalidateQueries({ queryKey: ["writing-assistant"] }); void client.invalidateQueries({ queryKey: ["assistant-proposal"] }); };
    window.addEventListener(WRITING_ASSISTANT_EVENT, refresh);
    return () => window.removeEventListener(WRITING_ASSISTANT_EVENT, refresh);
  }, [client]);
  const refresh = useCallback(async () => {
    await Promise.all([...(branch ? [] : [client.invalidateQueries({ queryKey: ["assistant-initial-branch", id] })]), client.invalidateQueries({ queryKey: ["writing-assistant", id, branch, sceneId] }), client.invalidateQueries({ queryKey: ["assistant-proposal", id, branch, sceneId] })]);
  }, [branch, client, id, sceneId]);
  const capture = useCallback((value: AssistantEditorTarget | null) => { setTarget(value); setParent(null); }, []);
  const openEditorAction = useCallback((value: AssistantEditorTarget, action: "polish" | "continue") => {
    setTarget(value); setParent(null); setIntent("EDIT"); setInput(t(action === "polish" ? "assistant.polishPrompt" : "assistant.continuePrompt")); setError(""); open();
  }, [open]);
  const execute = async (action: () => Promise<void>) => {
    setWorking(true); setError(""); setNotice("");
    try { await action(); } catch (e) { if (latest.current.identity === identity) setError(e instanceof AssistantInteractionError ? e.message : localizedErrorMessage(e)); }
    finally { await refresh(); if (latest.current.identity === identity) setWorking(false); }
  };
  const submit = () => execute(async () => {
    if (!manuscript || !input.trim() || !session.data || busy || turns.some(activeTurn)) return;
    if (target && !target.supported && intent !== "AUTHOR_QA") throw new AssistantInteractionError(t("assistant.unsupportedSelection"));
    if (target && !sameAssistantDocument(target.html, content)) throw new AssistantInteractionError(t("assistant.selectionChanged"));
    let source = manuscript;
    if (session.data.h2Enabled) source = await runWrite(content, async current => current);
    if (latest.current.identity !== identity || !sameAssistantDocument(latest.current.content, content)) throw new AssistantInteractionError(t("assistant.localChanged"));
    const request: AssistantTurnRequest = { branchId: branch, expectedVersion: source.version, instruction: input.trim(), sourceHtml: parent?.status === "READY" ? parent.beforeHtml : content,
      selection: intent === "AUTHOR_QA" && !target?.supported ? null : target?.selection || null, parentProposalId: parent?.id, intent };
    const path=sceneContextPath(id,sceneId), previewRequest={...request,purpose:'ASSISTANT'};
    const preview=await reviewWorldPreview(path,previewRequest,await api.worldContext.preview(path,previewRequest));
    if(latest.current.identity!==identity||!sameAssistantDocument(latest.current.content,content))throw new AssistantInteractionError(t('assistant.localChanged'));
    request.contextHash=preview.contextHash;request.expectedSelectionRevision=preview.preferences.revision;
    const submissionId = `${identity}:${intent}:${input}:${parent?.id || ""}:${JSON.stringify(target?.selection || null)}:${content}`;
    const pending = submissions.current.get(submissionId) || { key: crypto.randomUUID(), request };
    submissions.current.set(submissionId, pending);
    let accepted;
    try { accepted = await api.writingAssistant.submit(id, sceneId, pending.request, pending.key); }
    catch (e) { if (isApiError(e) && e.status >= 400 && e.status < 500 && e.status !== 408) submissions.current.delete(submissionId); throw e; }
    submissions.current.delete(submissionId);
    if (latest.current.identity === identity) { setInput(""); setParent(null); }
    await refresh();
    await runTrackedAiOperation(Promise.resolve(accepted));
  });
  const decide = (proposal: AssistantProposal, action: "accept" | "reject" | "undo") => execute(async () => {
    if (!manuscript) return;
    if (action !== "reject" && busy) throw new AssistantInteractionError(t("assistant.writeBusy"));
    const key = `${identity}:${proposal.id}:${action}`;
    const send = async (current: Manuscript) => {
      const receipt = decisions.current.get(key) || { key: crypto.randomUUID(), version: current.version, identity, proposalId: proposal.id, action,
        beforeHtml: action === "undo" ? proposal.afterHtml : proposal.beforeHtml, afterHtml: action === "undo" ? proposal.beforeHtml : proposal.afterHtml, uncertain: false };
      decisions.current.set(key, receipt);
      try {
        const result = await api.writingAssistant.decide(id, sceneId, proposal.id, action, receipt.version, branch, receipt.key);
        return result.manuscript;
      } catch (e) {
        if (isApiError(e) && e.status >= 400 && e.status < 500 && e.status !== 408) decisions.current.delete(key);
        else receipt.uncertain = true;
        throw e;
      }
    };
    if (action === "reject") await send(manuscript);
    else await runWrite(action === "accept" ? proposal.beforeHtml : proposal.afterHtml, send, action === "accept");
    decisions.current.delete(key);
  });
  const pendingDecision = (proposalId: string) => Array.from(decisions.current.values()).find(item => item.identity === identity && item.proposalId === proposalId && item.uncertain);
  const reconcileDecision = (proposalId: string) => execute(async () => {
    if (busy) throw new AssistantInteractionError(t("assistant.writeBusy"));
    const pending = pendingDecision(proposalId);
    if (!pending) return;
    const key = `${identity}:${pending.proposalId}:${pending.action}`;
    const replay = async () => {
      let receipt;
      try { receipt = await api.writingAssistant.decide(id, sceneId, pending.proposalId, pending.action, pending.version, branch, pending.key); }
      catch (e) { if (isApiError(e) && e.status >= 400 && e.status < 500 && e.status !== 408) decisions.current.delete(key); throw e; }
      // An idempotent response may be older than later saves in this or another scene.
      const current = await api.manuscripts.get(id);
      if (current.version < receipt.manuscript.version) throw new AssistantInteractionError(t("assistant.receiptOlder"));
      return current;
    };
    const result = pending.action === "reject" ? { manuscript: await replay(), applied: true }
      : await latest.current.reconcileWrite(pending.beforeHtml, pending.afterHtml, replay);
    decisions.current.delete(key);
    if (latest.current.identity === identity) setNotice(t(result.applied ? "assistant.receiptConfirmed" : "assistant.receiptPreserved"));
  });
  const refine = (proposal: AssistantProposal) => {
    setParent(proposal); setTarget(null); setIntent("EDIT"); setInput(""); setError(""); open();
  };
  const loadOlder = async () => {
    const before = olderCursor === undefined ? session.data?.nextBefore : olderCursor;
    if (!before || loadingOlder) return;
    setLoadingOlder(true);
    try { const page = await api.writingAssistant.session(id, sceneId, branch, before); if (latest.current.identity === identity) { setOlder(value => [...page.turns, ...value]); setOlderCursor(page.nextBefore); } }
    catch (e) { if (latest.current.identity === identity) setError(e instanceof AssistantInteractionError ? e.message : localizedErrorMessage(e)); }
    finally { setLoadingOlder(false); }
  };
  const recover = (operationId: string) => execute(() => retryTrackedAiOperation(operationId));
  return { id, branch, sceneId, sceneTitle, identity, content, locked, writeBusy: busy, input, setInput, intent, setIntent, target, capture, openEditorAction, parent, clearParent: () => setParent(null), working, notice,
    disabled: working || busy || !session.data || turns.some(activeTurn), error: error || (session.error || initialBranch.error ? localizedErrorMessage(session.error || initialBranch.error) : ""), session, turns, submit, decide, pendingDecision, reconcileDecision, refine, recover, refresh,
    loadOlder, loadingOlder, hasOlder: Boolean(olderCursor === undefined ? session.data?.nextBefore : olderCursor), manuscriptVersion: manuscript?.version ?? 0 };
}
