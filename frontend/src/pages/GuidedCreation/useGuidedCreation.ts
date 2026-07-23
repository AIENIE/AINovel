import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "@/lib/api-client";
import { CreationWorkflow, GuidedCreationCandidate } from "@/types";
import { showError } from "@/utils/toast";

const ACTIVE_JOB_STATES = new Set(["QUEUED", "RUNNING", "CALLING_AI"]);

export interface GuidedCreationSeed {
  seedIdea: string;
  genre: string;
  tone: string;
  targetChapterCount: number;
  autoRun: boolean;
}

export function useGuidedCreation() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [initialSelection] = useState(() => ({
    runId: searchParams.get("run"),
    blank: searchParams.get("new") === "1",
  }));
  const [runs, setRuns] = useState<CreationWorkflow[]>([]);
  const [workflow, setWorkflow] = useState<CreationWorkflow | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const refreshList = useCallback(async () => {
    const list = await api.creationWorkflows.list();
    setRuns(list);
    return list;
  }, []);

  const open = useCallback(async (id: string) => {
    setLoading(true);
    try {
      const next = await api.creationWorkflows.get(id);
      setWorkflow(next);
      navigate({ search: `?run=${encodeURIComponent(id)}` }, { replace: true });
    } catch (error) {
      showError(error instanceof Error ? error.message : "向导草稿加载失败");
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    refreshList()
      .then((list) => {
        if (cancelled) return;
        if (initialSelection.blank) {
          setWorkflow(null);
          return;
        }
        const selected = list.find((item) => item.id === initialSelection.runId)
          ?? list.find((item) => item.status !== "COMPLETED");
        if (selected) {
          setWorkflow(selected);
          navigate({ search: `?run=${encodeURIComponent(selected.id)}` }, { replace: true });
        }
      })
      .catch((error) => showError(error instanceof Error ? error.message : "向导草稿加载失败"))
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [initialSelection.blank, initialSelection.runId, navigate, refreshList]);

  const shouldPoll = Boolean(
    workflow && (
      workflow.status === "AUTO_RUNNING"
      || (workflow.activeJob && ACTIVE_JOB_STATES.has(workflow.activeJob.status))
    ),
  );

  const pollingWorkflowId = workflow?.id;
  useEffect(() => {
    if (!pollingWorkflowId || !shouldPoll) return;
    let cancelled = false;
    const poll = async () => {
      try {
        const next = await api.creationWorkflows.get(pollingWorkflowId);
        if (!cancelled) {
          setWorkflow(next);
          setRuns((current) => current.map((item) => item.id === next.id ? next : item));
        }
      } catch (error) {
        if (!cancelled) showError(error instanceof Error ? error.message : "生成状态刷新失败");
      }
    };
    const timer = window.setInterval(() => void poll(), 1400);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [pollingWorkflowId, shouldPoll]);

  const act = useCallback(async (action: () => Promise<CreationWorkflow>) => {
    setBusy(true);
    try {
      const next = await action();
      setWorkflow(next);
      await refreshList();
      return next;
    } catch (error) {
      showError(error instanceof Error ? error.message : "操作失败");
      return null;
    } finally {
      setBusy(false);
    }
  }, [refreshList]);

  const create = useCallback(async (seed: GuidedCreationSeed) => {
    setBusy(true);
    try {
      const created = await api.creationWorkflows.create(seed);
      setWorkflow(created);
      navigate({ search: `?run=${encodeURIComponent(created.id)}` }, { replace: true });
      if (!seed.autoRun) {
        await api.creationWorkflows.generate(created.id, created.currentStep);
        const queued = await api.creationWorkflows.get(created.id);
        setWorkflow(queued);
      }
      await refreshList();
    } catch (error) {
      showError(error instanceof Error ? error.message : "向导创建失败");
    } finally {
      setBusy(false);
    }
  }, [navigate, refreshList]);

  const confirm = useCallback(async (candidate: GuidedCreationCandidate) => {
    if (!workflow || workflow.currentStep === "COMPLETED") return;
    await act(async () => {
      const next = await api.creationWorkflows.confirm(
        workflow.id, workflow.currentStep, candidate.candidateId, candidate, workflow.version,
      );
      if (next.currentStep !== "COMPLETED") {
        await api.creationWorkflows.generate(next.id, next.currentStep);
        return await api.creationWorkflows.get(next.id);
      }
      return next;
    });
  }, [act, workflow]);

  const skipWorld = useCallback(async () => {
    if (!workflow) return;
    await act(async () => {
      const next = await api.creationWorkflows.skipWorld(workflow.id, workflow.version);
      if (next.currentStep !== "COMPLETED") {
        await api.creationWorkflows.generate(next.id, next.currentStep);
        return await api.creationWorkflows.get(next.id);
      }
      return next;
    });
  }, [act, workflow]);

  const startAuto = useCallback(async () => {
    if (!workflow) return;
    await act(() => api.creationWorkflows.startAuto(workflow.id, workflow.targetChapterCount));
  }, [act, workflow]);

  const retry = useCallback(async () => {
    if (!workflow) return;
    await act(() => api.creationWorkflows.retry(workflow.id));
  }, [act, workflow]);

  const developOutlineDirection = useCallback(async (
    candidate: GuidedCreationCandidate,
    action: "continue" | "rewrite",
    instruction: string,
  ) => {
    if (!workflow || workflow.currentStep !== "OUTLINE") return;
    await act(async () => {
      await api.creationWorkflows.developOutlineDirection(
        workflow.id, candidate.candidateId, action, instruction, candidate, workflow.version,
      );
      return await api.creationWorkflows.get(workflow.id);
    });
  }, [act, workflow]);

  const expandOutlineDirection = useCallback(async (candidate: GuidedCreationCandidate) => {
    if (!workflow || workflow.currentStep !== "OUTLINE") return;
    await act(async () => {
      await api.creationWorkflows.expandOutlineDirection(
        workflow.id, candidate.candidateId, candidate, workflow.version,
      );
      return await api.creationWorkflows.get(workflow.id);
    });
  }, [act, workflow]);

  const newDraft = useCallback(() => {
    setWorkflow(null);
    navigate({ search: "?new=1" }, { replace: true });
  }, [navigate]);

  const totalCharged = useMemo(() => workflow
    ? Object.values(workflow.steps).reduce((sum, step) => sum + Number(step?.chargedCredits || 0), 0)
    : 0, [workflow]);

  const remainingCredits = useMemo(() => {
    if (!workflow) return null;
    const values = Object.values(workflow.steps)
      .map((step) => step?.remainingCredits)
      .filter((value): value is number => typeof value === "number");
    return values.length ? values[values.length - 1] : null;
  }, [workflow]);

  return {
    runs, workflow, loading, busy, shouldPoll, totalCharged, remainingCredits,
    create, open, confirm, skipWorld, startAuto, retry, newDraft,
    developOutlineDirection, expandOutlineDirection,
  };
}
