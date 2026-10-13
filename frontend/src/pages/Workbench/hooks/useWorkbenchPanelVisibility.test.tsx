import { render, waitFor } from "@testing-library/react";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import { describe, expect, it, vi } from "vitest";
import { Panel, PanelGroup, PanelResizeHandle } from "react-resizable-panels";
import { useWorkbenchPanelVisibility } from "./useWorkbenchPanelVisibility";

// Vitest otherwise selects the package's Node/SSR build, which omits panel registration.
vi.mock("react-resizable-panels", () => {
  const require = createRequire(import.meta.url);
  return require(resolve(dirname(require.resolve("react-resizable-panels/package.json")), "dist/react-resizable-panels.browser.cjs.js"));
});

function Workspace({ paged, leftOpen, rightOpen }: { paged: boolean; leftOpen: boolean; rightOpen: boolean }) {
  const leftRef = useWorkbenchPanelVisibility(leftOpen, paged);
  const rightRef = useWorkbenchPanelVisibility(rightOpen, paged);
  if (paged) return <div>paged workspace</div>;
  return <PanelGroup direction="horizontal">
    <Panel id="outline" ref={leftRef} collapsible defaultSize={24} minSize={16}>outline</Panel>
    <PanelResizeHandle />
    <Panel id="editor" minSize={35}>editor</Panel>
    <PanelResizeHandle />
    <Panel id="assistant" ref={rightRef} collapsible defaultSize={30} minSize={20}>assistant</Panel>
  </PanelGroup>;
}

const size = (container: HTMLElement, id: string) => Number(container.querySelector(`[data-panel-id="${id}"]`)?.getAttribute("data-panel-size"));

describe("workbench desktop panel visibility", () => {
  it("collapses panels on an initial desktop mount when both controls are off", async () => {
    const { container } = render(<Workspace paged={false} leftOpen={false} rightOpen={false} />);
    await waitFor(() => expect(size(container, "editor")).toBe(100));
    expect(size(container, "outline")).toBe(0);
    expect(size(container, "assistant")).toBe(0);
  });

  it.each([false, true])("resynchronizes both panels after a compact visit when leftOpen=%s", async leftOpen => {
    const { container, rerender } = render(<Workspace paged={false} leftOpen={leftOpen} rightOpen={!leftOpen} />);
    await waitFor(() => expect(size(container, leftOpen ? "assistant" : "outline")).toBe(0));
    rerender(<Workspace paged leftOpen={leftOpen} rightOpen={!leftOpen} />);
    expect(container.querySelector("[data-panel-id]")).toBeNull();
    rerender(<Workspace paged={false} leftOpen={leftOpen} rightOpen={!leftOpen} />);
    await waitFor(() => expect(size(container, leftOpen ? "assistant" : "outline")).toBe(0));
    expect(size(container, leftOpen ? "outline" : "assistant")).toBeGreaterThan(0);
    expect(size(container, "editor")).toBeGreaterThan(60);
  });

  it("applies visibility changed while only the paged workspace was mounted", async () => {
    const { container, rerender } = render(<Workspace paged leftOpen={true} rightOpen={false} />);
    rerender(<Workspace paged leftOpen={false} rightOpen={true} />);
    rerender(<Workspace paged={false} leftOpen={false} rightOpen={true} />);
    await waitFor(() => expect(size(container, "outline")).toBe(0));
    expect(size(container, "assistant")).toBeGreaterThan(0);
    expect(size(container, "editor")).toBeGreaterThan(60);
  });
});
