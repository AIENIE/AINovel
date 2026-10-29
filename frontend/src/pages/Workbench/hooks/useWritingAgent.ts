import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/api-client";
import { agentLimits, type AgentCandidate, type AgentPreview, type AgentRequest, type AgentRun } from "@/lib/api/domains/writing-agent";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { AssistantInteractionError, sameAssistantDocument, type AssistantEditorTarget } from "@/lib/assistant-editor";
import { localizedErrorMessage } from "@/lib/error-messages";
import type { Manuscript } from "@/types";
import { t } from "@/i18n";

export type WritingAgentOptions = { manuscript?: Manuscript | null; sceneId: string; content: string; dirty: boolean; busy: boolean; locked: boolean; h2: boolean; open: boolean;
  target?: AssistantEditorTarget | null;
  runWrite: (expected: string, action: (current: Manuscript) => Promise<Manuscript>, saveBaseline?: boolean) => Promise<Manuscript>;
  reconcileWrite: (expected: string, expectedServer: string, action: (current: Manuscript) => Promise<Manuscript>) => Promise<{ manuscript: Manuscript; applied: boolean }> };
type PendingSubmission = { identity: string; request: AgentRequest; key: string; inputRevision: number; input: string };
type PendingAdoption = { identity: string; runId: string; candidateId: string; before: string; after: string; key: string; decision: { expectedRevision: number; expectedVersion: number; branchId: string; candidateId: string } };
export const agentActive = (status: string) => ["QUEUED", "RUNNING", "PAUSE_REQUESTED", "PAUSING", "STREAMING", "PROCESSING"].includes(status);
export function useWritingAgent(options: WritingAgentOptions) {
  const id = options.manuscript?.id || "", branch = options.manuscript?.currentBranchId || "", scene = options.sceneId;
  const identity = JSON.stringify([id, branch, scene]);
  const latest = useRef({ ...options, identity }); latest.current = { ...options, identity };
  const [input, setInputValue] = useState("");const inputRevision = useRef(0);
  const [intent, setIntent] = useState<AgentRequest["intent"]>("EDIT");const [mode, setMode] = useState<AgentRequest["mode"]>("fast");
  const [languageCheck, setLanguageCheck] = useState(false), [plotCheck, setPlotCheck] = useState(false);
  const [maxCost, setMaxCost] = useState("5"), [maxCredits, setMaxCredits] = useState("100"), [maxCalls, setMaxCalls] = useState(24);
  const [useSelection, setUseSelection] = useState(false), [preview, setPreview] = useState<AgentPreview | null>(null);
  const [working, setWorking] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState("");
  const [selectedRunId, setSelectedRunId] = useState("");const [pending, setPending] = useState<PendingSubmission | null>(null);
  const [pendingAdoption, setPendingAdoption] = useState<PendingAdoption | null>(null);
  const workingIdentity = useRef<string | null>(null);
  const controlKeys = useRef(new Map<string, string>());
  const client = useQueryClient();const key = ["writing-agent", id, branch, scene];
  const history = useQuery({ queryKey: key, queryFn: ({ signal }) => api.writingAgent.list(id, scene, branch, signal), enabled: options.open && !!id && !!branch && !!scene,
    retry: false, refetchInterval: query => query.state.data?.some(run => agentActive(run.status)) ? 2000 : false });
  const selectedRun = history.data?.find(run => run.id === selectedRunId) || history.data?.[0];
  const updateInput = (value: string) => { inputRevision.current++;setInputValue(value); };
  const fingerprint = JSON.stringify([identity, options.manuscript?.version, options.content, input, intent, mode, languageCheck, plotCheck, maxCost, maxCredits, maxCalls, useSelection, options.target?.selection]);
  const fingerprintRef = useRef(fingerprint);fingerprintRef.current = fingerprint;
  useEffect(() => { setPreview(null); }, [fingerprint]);
  useEffect(() => { setInputValue("");inputRevision.current++;setPreview(null);setError("");setNotice("");setSelectedRunId("");setWorking(false);setUseSelection(false);setPending(null);setPendingAdoption(null); }, [identity]);
  useEffect(() => {
    if (!id || !branch || !scene) return;
    const updated = () => { void client.invalidateQueries({ queryKey: ["writing-agent", id, branch, scene], exact: true }); };
    window.addEventListener("ainovel:writing-agent-updated", updated);
    return () => window.removeEventListener("ainovel:writing-agent-updated", updated);
  }, [branch, client, id, scene]);
  const refresh = () => client.invalidateQueries({ queryKey: key });
  const execute = async (action: () => Promise<void>) => {
    if (workingIdentity.current === identity) return;
    workingIdentity.current = identity;
    setWorking(true);setError("");setNotice("");
    try { await action(); }
    catch (e) { if (latest.current.identity === identity && !(e instanceof Error && e.message === "AI_QUOTE_CANCELLED")) setError(e instanceof AssistantInteractionError ? e.message : localizedErrorMessage(e)); }
    finally { await refresh();if (workingIdentity.current === identity) workingIdentity.current = null;if (latest.current.identity === identity) setWorking(false); }
  };
  const request = (): AgentRequest => {
    const maxCostCny = Number(maxCost), maxProjectCredits = Number(maxCredits);
    if (!branch || !options.manuscript || !input.trim()) throw new AssistantInteractionError(t("agent.chooseScene"));
    if (!/^\d+(?:\.\d{1,6})?$/.test(maxCost.trim()) || !Number.isFinite(maxCostCny) || maxCostCny <= 0 || !Number.isSafeInteger(maxProjectCredits) || maxProjectCredits < 1 || !Number.isSafeInteger(maxCalls) || maxCalls < 1 || maxCalls > 24) throw new AssistantInteractionError(t("agent.invalidBudget"));
    if (intent === "EDIT" && options.dirty) throw new AssistantInteractionError(t("agent.saveRequired"));
    if (useSelection && (!options.target?.supported || !sameAssistantDocument(options.target.html, options.content))) throw new AssistantInteractionError(t("assistant.selectionChanged"));
    return { branchId: branch, expectedVersion: options.manuscript.version, sourceHtml: options.content, selection: useSelection ? options.target?.selection || null : null,
      instruction: input.trim(), intent, mode, languageCheck, plotCheck, maxCostCny, maxProjectCredits, limits: { ...agentLimits, modelCalls: maxCalls } };
  };
  const prepare = () => execute(async () => {
    const baseline = fingerprintRef.current;const value = await api.writingAgent.preview(id, scene, request());
    if (latest.current.identity !== identity || fingerprintRef.current !== baseline) return;
    setPreview(value);
  });
  const send = async (submission: PendingSubmission) => {
    let accepted;
    try { accepted = await api.writingAgent.start(id, scene, submission.request, submission.key); }
    catch (e) {
      const cancelled = e instanceof Error && e.message === "AI_QUOTE_CANCELLED";
      if (latest.current.identity === identity) setPending(cancelled || isApiError(e) && e.status >= 400 && e.status < 500 && e.status !== 408 ? null : submission);
      throw e;
    }
    if (latest.current.identity === identity) {
      setPending(null);setSelectedRunId(accepted.runId);setPreview(null);
      if (inputRevision.current === submission.inputRevision) setInputValue("");
      setNotice(t("agent.started"));
    }
    void runTrackedAiOperation(Promise.resolve(accepted)).catch(() => undefined).finally(() => { void refresh(); });
  };
  const start = () => execute(async () => {
    if (!preview || preview.issues.length || options.busy || options.locked || pending || history.data?.some(run => agentActive(run.status))) return;
    const value = request();value.contextHash = preview.contextHash;value.expectedSelectionRevision = preview.selectionRevision;
    await send({ identity, request: value, key: crypto.randomUUID(), inputRevision: inputRevision.current, input });
  });
  const reconcileSubmit = () => execute(async () => { if (pending?.identity === identity) await send(pending); });
  const control = (run: AgentRun, action: "pause" | "resume" | "cancel", answer?: string) => execute(async () => {
    const command = `${identity}:${run.id}:${action}:${run.revision}:${answer || ""}`;
    const commandKey = controlKeys.current.get(command) || crypto.randomUUID();controlKeys.current.set(command, commandKey);
    try { await api.writingAgent.control(id, scene, run.id, action, run.revision, commandKey, answer);controlKeys.current.delete(command); }
    catch (e) { if (isApiError(e) && e.status >= 400 && e.status < 500 && e.status !== 408) controlKeys.current.delete(command);throw e; }
  });
  const accept = (run: AgentRun, candidate: AgentCandidate) => execute(async () => {
    if (options.dirty || options.busy || options.locked || !options.manuscript || run.intent === "AUTHOR_QA" || pendingAdoption) return;
    if (!sameAssistantDocument(options.content, run.sourceHtml) || options.manuscript.version !== run.sourceVersion) throw new AssistantInteractionError(t("agent.stale"));
    const receipt: PendingAdoption = { identity, runId: run.id, candidateId: candidate.id, before: run.sourceHtml, after: candidate.html, key: crypto.randomUUID(),
      decision: { expectedRevision: run.revision, expectedVersion: options.manuscript.version, branchId: branch, candidateId: candidate.id } };
    try { await options.runWrite(run.sourceHtml, () => api.writingAgent.accept(id, scene, run.id, receipt.decision, receipt.key), false); }
    catch (e) { if (latest.current.identity === identity && !(isApiError(e) && e.status >= 400 && e.status < 500 && e.status !== 408)) setPendingAdoption(receipt);throw e; }
    if (latest.current.identity === identity) setNotice(t("agent.adopted"));
  });
  const reconcileAccept = () => execute(async () => {
    if (!pendingAdoption || pendingAdoption.identity !== identity || options.busy || options.locked) return;
    const receipt = pendingAdoption;
    const result = await options.reconcileWrite(receipt.before, receipt.after, async () => {
      await api.writingAgent.accept(id, scene, receipt.runId, receipt.decision, receipt.key);
      return api.manuscripts.get(id);
    });
    if (latest.current.identity === identity) { setPendingAdoption(null);setNotice(t(result.applied ? "agent.adopted" : "agent.localPreserved")); }
  });
  return { input, updateInput, intent, setIntent, mode, setMode, languageCheck, setLanguageCheck, plotCheck, setPlotCheck, maxCost, setMaxCost, maxCredits, setMaxCredits, maxCalls, setMaxCalls,
    useSelection, setUseSelection, preview, working, error: error || (history.error ? localizedErrorMessage(history.error) : ""), notice, history, selectedRun, selectedRunId, setSelectedRunId,
    pending, pendingAdoption, prepare, start, reconcileSubmit, reconcileAccept, control, accept, refresh };
}
