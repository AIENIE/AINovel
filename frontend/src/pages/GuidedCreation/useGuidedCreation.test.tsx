import type { PropsWithChildren } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { useGuidedCreation } from "./useGuidedCreation";

const failedRun = {
  id: "failed-run",
  templateKey: "quick-book-v1",
  status: "FAILED",
  currentStep: "OUTLINE",
  seedIdea: "旧草稿",
  targetChapterCount: 6,
  autoRun: false,
  steps: {},
  version: 4,
};

describe("useGuidedCreation", () => {
  afterEach(() => vi.restoreAllMocks());

  it("keeps explicit new-draft mode blank instead of restoring a failed run", async () => {
    vi.spyOn(api.creationWorkflows, "list").mockResolvedValue([failedRun] as any);
    const wrapper = ({ children }: PropsWithChildren) => (
      <MemoryRouter initialEntries={["/novels/quick-create?new=1"]}>{children}</MemoryRouter>
    );
    const { result } = renderHook(() => useGuidedCreation(), { wrapper });

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.workflow).toBeNull();
    expect(result.current.runs).toHaveLength(1);
  });

  it("does not reselect the failed run after the user clicks new draft", async () => {
    vi.spyOn(api.creationWorkflows, "list").mockResolvedValue([failedRun] as any);
    const wrapper = ({ children }: PropsWithChildren) => (
      <MemoryRouter initialEntries={["/novels/quick-create"]}>{children}</MemoryRouter>
    );
    const { result } = renderHook(() => useGuidedCreation(), { wrapper });
    await waitFor(() => expect(result.current.workflow?.id).toBe("failed-run"));

    act(() => result.current.newDraft());

    await waitFor(() => expect(result.current.workflow).toBeNull());
    expect(api.creationWorkflows.list).toHaveBeenCalledTimes(1);
    expect(result.current.workflow).toBeNull();
  });
});
