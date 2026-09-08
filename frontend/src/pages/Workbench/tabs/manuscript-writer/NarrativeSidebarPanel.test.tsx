import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Tabs } from "@/components/ui/tabs";
import type { Manuscript } from "@/types";
import type { NarrativeExtraction, NarrativeSource, NarrativeState } from "@/types/narrative";
import { api } from "@/lib/api-client";
import { NarrativeSidebarPanel } from "./NarrativeSidebarPanel";
import { NarrativeEvidenceDialog } from "./NarrativeEvidenceDialog";

vi.mock("@/contexts/auth-state", () => ({ useAuth: () => ({ user: { id: "author" } }) }));
vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock("@/lib/api-client", () => ({ api: { narrative: { state: vi.fn(), evidence: vi.fn(), review: vi.fn(), approve: vi.fn() } }, isApiError: () => false }));
const manuscript = { id: "manuscript", currentBranchId: "branch", version: 4 } as Manuscript;
const position = { chapterId: "chapter", chapterTitle: "一", chapterOrder: 1, sceneId: "scene", sceneTitle: "桥", sceneOrder: 1, index: 0, orderHash: "hash" };
const source: NarrativeSource = { approvalId: "approval", versionId: "version", sceneId: "scene", disclosedAt: position, blocks: [{ id: "b1", text: "𠮷😀声称桥断" }], textHash: "hash", confirmedAt: "2026-09-08T00:00:00Z" };
const extraction: NarrativeExtraction = {
  id: "extraction", approvalId: "approval", sceneId: "scene", operationId: "operation", status: "READY", stale: false,
  reviewed: false, baseCanonRevision: 0, promptVersion: "p11", model: "test", usage: null, error: null, review: null, createdAt: source.confirmedAt,
  candidates: [{ id: "c1", validationError: null, assertion: { subject: "林青", statement: "声称桥断", kind: "UTTERANCE", characterId: null,
    holderCharacterId: null, worldTime: null, uncertainty: "未知", evidence: [{ blockId: "b1", quote: "声称桥断", start: 2, end: 6 }], supersedesId: null } }],
};
const state: NarrativeState = { canonRevision: 0, manuscriptVersion: 4, branchId: "branch", records: [], extractions: [extraction] };
function mount(dirty = false) {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <Tabs defaultValue="narrative"><NarrativeSidebarPanel active manuscript={manuscript} sceneId="scene" dirty={dirty} busy={false} structureKey="scene" characters={[]} onManuscript={vi.fn()} /></Tabs>
  </QueryClientProvider>);
}
beforeEach(() => {
  vi.resetAllMocks(); sessionStorage.clear();
  vi.mocked(api.narrative.state).mockResolvedValue(state);
  vi.mocked(api.narrative.evidence).mockResolvedValue(source);
});
afterEach(cleanup);
describe("narrative author workflow", () => {
  it("never starts a paid extraction while the scene is unsaved", async () => {
    mount(true);
    const button = await screen.findByText("narrative.approve");
    fireEvent.click(button);
    expect((button as HTMLButtonElement).disabled).toBe(true);
    expect(api.narrative.approve).not.toHaveBeenCalled();
    expect(screen.getByText("narrative.saveFirst")).toBeTruthy();
  });
  it("restores review choices and repeats the exact request after a transport error", async () => {
    vi.mocked(api.narrative.review).mockRejectedValueOnce(new Error("connection lost")).mockResolvedValueOnce({ commitId: "commit", canonRevision: 1, recordIds: ["record"] });
    const first = mount();
    await screen.findByText("narrative.submit");
    fireEvent.click(screen.getByText("narrative.all.ACCEPT"));
    await waitFor(() => expect(sessionStorage.getItem("ainovel.narrative.review:author:manuscript:branch:extraction")).toContain("ACCEPT"));
    first.unmount(); mount();
    await screen.findByText("narrative.submit");
    await waitFor(() => expect((screen.getByText("narrative.submit") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(screen.getByText("narrative.submit"));
    await screen.findByRole("alert");
    fireEvent.click(screen.getByText("narrative.submit"));
    await waitFor(() => expect(api.narrative.review).toHaveBeenCalledTimes(2));
    expect(vi.mocked(api.narrative.review).mock.calls[0]).toEqual(vi.mocked(api.narrative.review).mock.calls[1]);
    await screen.findByText("narrative.reviewedHint");
  });
  it("blocks a candidate whose confirmed source became stale", async () => {
    vi.mocked(api.narrative.state).mockResolvedValue({ ...state, extractions: [{ ...extraction, stale: true }] });
    mount(); await screen.findByText("narrative.staleHint");
    expect((screen.getByText("narrative.submit") as HTMLButtonElement).disabled).toBe(true);
    expect(api.narrative.review).not.toHaveBeenCalled();
  });
  it("keeps malformed model candidates reviewable when evidence is missing", async () => {
    const incomplete = { ...extraction.candidates[0].assertion!, evidence: null };
    vi.mocked(api.narrative.state).mockResolvedValue({ ...state, extractions: [{ ...extraction,
      candidates: [{ id: "c1", validationError: "NARRATIVE_INVALID_ASSERTION", assertion: incomplete as unknown as NonNullable<NarrativeExtraction["candidates"][number]["assertion"]> }],
    }] });
    vi.mocked(api.narrative.review).mockResolvedValue({ commitId: "empty", canonRevision: 1, recordIds: [] });
    mount(); await screen.findByText("narrative.invalidCandidate");
    fireEvent.click(screen.getByText("narrative.all.REJECT"));
    await waitFor(() => expect((screen.getByText("narrative.submit") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(screen.getByText("narrative.submit"));
    await screen.findByText("narrative.reviewedHint");
    expect(vi.mocked(api.narrative.review).mock.calls[0][3].decisions).toEqual([{ candidateId: "c1", decision: "REJECT", edited: null }]);
  });
  it("highlights Unicode code points in the immutable evidence source", () => {
    render(<NarrativeEvidenceDialog source={source} evidence={extraction.candidates[0].assertion!.evidence} onClose={vi.fn()} />);
    expect(Array.from(document.querySelectorAll("mark")).map(m => m.textContent).join("")).toBe("声称桥断");
    expect(screen.getByText(/𠮷😀/)).toBeTruthy();
  });
});
