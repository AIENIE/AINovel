import { createContext, useContext, useEffect } from "react";
import type { StoryAssistantCandidate } from "@/lib/api/domains/story-assistant";

export type StoryProposalDraft = { revision: number; candidate: StoryAssistantCandidate };

export const StoryDiscussionWorkspaceContext = createContext<{
  dirtyTargets: ReadonlySet<string>;
  setDirty: (key: string, dirty: boolean) => void;
  proposalDrafts?: Map<string, StoryProposalDraft>;
} | null>(null);

export function useStoryResourceDirty(key: string, dirty: boolean) {
  const workspace = useContext(StoryDiscussionWorkspaceContext);
  const setDirty = workspace?.setDirty;
  useEffect(() => {
    if (!key || !setDirty) return;
    setDirty(key, dirty);
    return () => setDirty(key, false);
  }, [key, dirty, setDirty]);
}
