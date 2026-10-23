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

  it("waits after world adoption and publication until the author requests the remaining generation", async () => {
    const worldRun={...failedRun,id:'world-run',status:'WAITING_USER',currentStep:'WORLD',steps:{WORLD:{candidates:[{candidateId:'one',name:'群岛'}]}}};
    vi.spyOn(api.creationWorkflows,'list').mockResolvedValue([worldRun] as never);
    const pending={...worldRun,version:5,steps:{WORLD:{...worldRun.steps.WORLD,awaitingPublication:true}}};
    vi.spyOn(api.creationWorkflows,'confirm').mockResolvedValue(pending as never);
    const next={...pending,currentStep:'CHARACTERS',version:6,steps:{WORLD:{awaitingPublication:false}}};
    const publish=vi.spyOn(api.creationWorkflows,'publishWorld').mockResolvedValue(next as never);
    const generate=vi.spyOn(api.creationWorkflows,'generate').mockResolvedValue({} as never);
    vi.spyOn(api.creationWorkflows,'get').mockResolvedValue(next as never);
    const wrapper=({children}:PropsWithChildren)=><MemoryRouter initialEntries={['/novels/quick-create']}>{children}</MemoryRouter>;
    const {result}=renderHook(()=>useGuidedCreation(),{wrapper});
    await waitFor(()=>expect(result.current.workflow?.id).toBe('world-run'));
    await act(async()=>{await result.current.confirm({candidateId:'one',name:'群岛'});});
    expect(result.current.workflow?.steps.WORLD?.awaitingPublication).toBe(true);expect(generate).not.toHaveBeenCalled();
    await act(async()=>{await result.current.publishWorld({expectedRevision:2,fromVersionId:null,expectedBindingRevision:0,releaseNote:'',requestKey:'receipt'});});
    expect(publish).toHaveBeenCalledWith('world-run',expect.objectContaining({version:5,requestKey:'receipt'}));expect(generate).not.toHaveBeenCalled();
    await act(async()=>{await result.current.generateCurrent();});expect(generate).toHaveBeenCalledWith('world-run','CHARACTERS');
  });

  it("keeps explicit new-draft mode blank instead of restoring a failed run", async () => {
    vi.spyOn(api.creationWorkflows, "list").mockResolvedValue([failedRun] as never);
    const wrapper = ({ children }: PropsWithChildren) => (
      <MemoryRouter initialEntries={["/novels/quick-create?new=1"]}>{children}</MemoryRouter>
    );
    const { result } = renderHook(() => useGuidedCreation(), { wrapper });

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.workflow).toBeNull();
    expect(result.current.runs).toHaveLength(1);
  });

  it("does not reselect the failed run after the user clicks new draft", async () => {
    vi.spyOn(api.creationWorkflows, "list").mockResolvedValue([failedRun] as never);
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
