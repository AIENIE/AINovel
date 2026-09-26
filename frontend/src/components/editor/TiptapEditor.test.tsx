import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TiptapEditor from "./TiptapEditor";

vi.mock("@/contexts/auth-state", () => ({ useAuth: () => ({ user: null, refreshProfile: vi.fn() }) }));
vi.mock("@/components/ai/AiRefineDialog", () => ({ default: () => null }));
// Tooltip positioning is outside this editor/state integration test (JSDOM has no layout).
vi.mock("@tiptap/react/menus", () => ({ BubbleMenu: () => null }));

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
});
