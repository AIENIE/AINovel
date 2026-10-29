import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import TiptapEditor from "./TiptapEditor";
import { WritingAssistantContext } from "@/components/ai/writing-assistant-context";
import { useManuscriptEditorState } from "@/pages/Workbench/hooks/useManuscriptEditorState";
import type { useWritingAssistant } from "@/pages/Workbench/hooks/useWritingAssistant";
import type { Manuscript } from "@/types";

vi.mock("@/contexts/auth-state", () => ({ useAuth: () => ({ user: null, refreshProfile: vi.fn() }) }));
// Tooltip positioning is outside this editor/state integration test (JSDOM has no layout).
vi.mock("@tiptap/react/menus", () => ({ BubbleMenu: () => null }));
afterEach(cleanup);

describe("TiptapEditor hydration", () => {
  it("does not emit a saveable edit when mounting, switching text, or changing permissions", async () => {
    const onChange = vi.fn();
    const { rerender } = render(<TiptapEditor content="<p>前一场正文</p>" onChange={onChange} />);
    await screen.findByText("前一场正文");
    expect(onChange).not.toHaveBeenCalled();
    rerender(<TiptapEditor content="<p>下一场正文</p>" onChange={onChange} editable={false} />);
    await screen.findByText("下一场正文");
    await waitFor(() => expect(screen.queryByText("前一场正文")).toBeNull());
    expect(onChange).not.toHaveBeenCalled();
    rerender(<TiptapEditor content="<p>下一场正文</p>" onChange={onChange} editable />);
    expect(onChange).not.toHaveBeenCalled();
  });
  it("locks the actual contenteditable and formatting controls during the shared assistant write", async () => {
    const manuscript: Manuscript = { id: "m", currentBranchId: "b", outlineId: "o", title: "稿件", updatedAt: "2026-10-13", version: 1, sections: { s: "<p>正文</p>" } };
    let state!: ReturnType<typeof useManuscriptEditorState>;
    function Harness() {
      state = useManuscriptEditorState({ selectedManuscript: manuscript, selectedManuscriptId: "m", selectedSceneId: "s", selectedStoryId: "", replaceManuscript: vi.fn(), toast: vi.fn() });
      const assistant = { locked: state.assistantWriteLocked } as ReturnType<typeof useWritingAssistant>;
      return <WritingAssistantContext.Provider value={assistant}><TiptapEditor content={state.content} onChange={state.handleEditorChange} /></WritingAssistantContext.Provider>;
    }
    const { container } = render(<Harness />);
    await screen.findByText("正文");
    let release!: (value: Manuscript) => void;
    const response = new Promise<Manuscript>(resolve => { release = resolve; });
    let writing!: Promise<Manuscript>;
    act(() => { writing = state.runAssistantWrite("<p>正文</p>", () => response); });
    await waitFor(() => expect(container.querySelector(".tiptap")?.getAttribute("contenteditable")).toBe("false"));
    expect(container.querySelector("fieldset")?.disabled).toBe(true);
    await act(async () => { release({ ...manuscript, version: 2, sections: { s: "<p>已采纳</p>" } }); await writing; });
    expect(container.querySelector(".tiptap")?.getAttribute("contenteditable")).toBe("true");
    expect(container.querySelector("fieldset")?.disabled).toBe(false);
    expect(screen.getByText("已采纳")).toBeTruthy();
  });
});
