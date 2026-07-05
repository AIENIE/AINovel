import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/lib/api-client";

type UseWritingSessionOptions = {
  selectedStoryId: string;
  selectedManuscriptId: string;
  selectedSceneId: string;
  selectedSceneDirty: boolean;
  autoSaveIntervalSeconds?: number | null;
  measureHtmlWords: (html: string) => number;
};

export function useWritingSession({
  selectedStoryId,
  selectedManuscriptId,
  selectedSceneId,
  selectedSceneDirty,
  autoSaveIntervalSeconds,
  measureHtmlWords,
}: UseWritingSessionOptions) {
  const [sessionId, setSessionId] = useState("");
  const [sessionStartedAt, setSessionStartedAt] = useState<number>(0);
  const [sessionWordsWritten, setSessionWordsWritten] = useState(0);
  const [sessionWordsDeleted, setSessionWordsDeleted] = useState(0);
  const [tick, setTick] = useState(0);

  const sessionIdRef = useRef("");
  const sessionWordsWrittenRef = useRef(0);
  const sessionWordsDeletedRef = useRef(0);
  const sceneWordCacheRef = useRef<Record<string, number>>({});
  const autoSnapshotRef = useRef(0);

  const sessionNetWords = sessionWordsWritten - sessionWordsDeleted;
  const sessionDurationSeconds = useMemo(
    () => (sessionStartedAt ? Math.max(0, Math.floor((Date.now() - sessionStartedAt) / 1000)) : 0),
    [sessionStartedAt, tick],
  );

  const primeSceneHtml = useCallback(
    (sceneId: string, html: string) => {
      if (!sceneId) return;
      sceneWordCacheRef.current[sceneId] = measureHtmlWords(html);
    },
    [measureHtmlWords],
  );

  const recordSceneHtml = useCallback(
    (sceneId: string, html: string) => {
      if (!sceneId) return;
      const previousWordCount = sceneWordCacheRef.current[sceneId] ?? 0;
      const nextWordCount = measureHtmlWords(html);
      const delta = nextWordCount - previousWordCount;
      sceneWordCacheRef.current[sceneId] = nextWordCount;
      if (delta > 0) setSessionWordsWritten((value) => value + delta);
      if (delta < 0) setSessionWordsDeleted((value) => value + Math.abs(delta));
    },
    [measureHtmlWords],
  );

  useEffect(() => {
    const timer = window.setInterval(() => setTick((prev) => prev + 1), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    sessionIdRef.current = sessionId;
  }, [sessionId]);

  useEffect(() => {
    sessionWordsWrittenRef.current = sessionWordsWritten;
    sessionWordsDeletedRef.current = sessionWordsDeleted;
  }, [sessionWordsDeleted, sessionWordsWritten]);

  useEffect(() => {
    if (!selectedStoryId) {
      setSessionId("");
      setSessionStartedAt(0);
      setSessionWordsWritten(0);
      setSessionWordsDeleted(0);
      return;
    }

    void api.v2.workspace
      .startSession({ storyId: selectedStoryId })
      .then((session) => {
        setSessionId(String(session.id || ""));
        setSessionStartedAt(new Date(session.startedAt || Date.now()).getTime());
        setSessionWordsWritten(Number(session.wordsWritten || 0));
        setSessionWordsDeleted(Number(session.wordsDeleted || 0));
      })
      .catch(() => undefined);

    return () => {
      if (!sessionIdRef.current) return;
      void api.v2.workspace.endSession(sessionIdRef.current, {
        wordsWritten: sessionWordsWrittenRef.current,
        wordsDeleted: sessionWordsDeletedRef.current,
      });
      setSessionId("");
    };
  }, [selectedStoryId]);

  useEffect(() => {
    if (!sessionId) return;
    const timer = window.setInterval(() => {
      void api.v2.workspace.heartbeatSession(sessionId, {
        wordsWritten: sessionWordsWrittenRef.current,
        wordsDeleted: sessionWordsDeletedRef.current,
      });
    }, 30000);
    return () => window.clearInterval(timer);
  }, [sessionId]);

  useEffect(() => {
    const onBeforeUnload = () => {
      if (!sessionIdRef.current) return;
      void api.v2.workspace.endSession(sessionIdRef.current, {
        wordsWritten: sessionWordsWrittenRef.current,
        wordsDeleted: sessionWordsDeletedRef.current,
      });
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, []);

  useEffect(() => {
    if (!autoSaveIntervalSeconds || !selectedManuscriptId || !selectedSceneId || !selectedSceneDirty) return;
    const intervalMs = Math.max(30000, Number(autoSaveIntervalSeconds) * 1000);
    const now = Date.now();
    if (now - autoSnapshotRef.current < intervalMs) return;
    autoSnapshotRef.current = now;
    void api.v2.version
      .createVersion(selectedManuscriptId, {
        snapshotType: "auto",
        label: `auto-${new Date().toLocaleTimeString()}`,
      })
      .catch(() => undefined);
  }, [autoSaveIntervalSeconds, selectedManuscriptId, selectedSceneDirty, selectedSceneId, tick]);

  return {
    primeSceneHtml,
    recordSceneHtml,
    sessionDurationSeconds,
    sessionNetWords,
    sessionStartedAt,
    sessionWordsDeleted,
    sessionWordsWritten,
  };
}
