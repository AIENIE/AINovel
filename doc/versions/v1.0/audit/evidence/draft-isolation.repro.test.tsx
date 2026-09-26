// Audit reproduction: asserts the current defect, not the desired behavior.
// No network, real account, browser, or persisted manuscript is used.
import { act, cleanup, renderHook } from "../../../../../frontend/node_modules/@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { useManuscriptEditorState } from "@/pages/Workbench/hooks/useManuscriptEditorState";

vi.mock("@/lib/api-client", () => ({
  api: { manuscripts: { saveSection: vi.fn() } },
  isApiError: () => false,
}));
vi.mock("@/pages/Workbench/hooks/useWritingSession", () => ({
  useWritingSession: () => ({ primeSceneHtml: () => {}, recordSceneHtml: () => {}, sessionDurationSeconds: 0, sessionNetWords: 0 }),
}));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

it("reproduces a cached scene draft being written into a different manuscript", async () => {
  const a = { id: "manuscript-a", outlineId: "shared-outline", title: "A", version: 1, updatedAt: "2026-09-26T00:00:00Z", sections: { "shared-scene": "A original" } };
  const b = { ...a, id: "manuscript-b", title: "B", version: 7, sections: { "shared-scene": "B original" } };
  const { result, rerender } = renderHook(({ manuscript }) => useManuscriptEditorState({
    selectedManuscript: manuscript, selectedManuscriptId: manuscript.id,
    selectedStoryId: "", selectedSceneId: "shared-scene", replaceManuscript: () => {}, toast: () => {},
  }), { initialProps: { manuscript: a } });
  act(() => result.current.updateSceneDraft("shared-scene", "A edited"));
  rerender({ manuscript: b });
  expect(result.current.content).toBe("A edited");
  expect(result.current.content).not.toBe("B original");
  vi.mocked(api.manuscripts.saveSection).mockResolvedValue({ ...b, version: 8, sections: { "shared-scene": "A edited" } });
  await act(async () => { await result.current.handleManualSave(); });
  expect(api.manuscripts.saveSection).toHaveBeenCalledWith("manuscript-b", "shared-scene", "A edited", 7);
});
