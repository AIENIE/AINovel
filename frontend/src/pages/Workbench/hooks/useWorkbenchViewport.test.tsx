import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useWorkbenchViewport } from "./useWorkbenchViewport";

const setViewportWidth = (width: number) => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    writable: true,
    value: width,
  });
};

describe("useWorkbenchViewport", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setViewportWidth(1024);
  });

  it("tracks mobile and compact breakpoints from the viewport width", async () => {
    setViewportWidth(700);
    const setLeftPanelOpen = vi.fn();
    const setIsSidebarOpen = vi.fn();

    const { result } = renderHook(() =>
      useWorkbenchViewport({
        focusMode: false,
        setLeftPanelOpen: setLeftPanelOpen as any,
        setIsSidebarOpen: setIsSidebarOpen as any,
      }),
    );

    await waitFor(() => {
      expect(result.current.isMobile).toBe(true);
    });
    expect(result.current.isCompact).toBe(true);

    act(() => {
      setViewportWidth(1400);
      window.dispatchEvent(new Event("resize"));
    });

    await waitFor(() => {
      expect(result.current.isMobile).toBe(false);
    });
    expect(result.current.isCompact).toBe(false);
  });

  it("closes both panels on mobile and only the sidebar on compact screens", async () => {
    setViewportWidth(760);
    const setLeftPanelOpen = vi.fn();
    const setIsSidebarOpen = vi.fn();

    const { rerender } = renderHook(
      ({ focusMode }) =>
        useWorkbenchViewport({
          focusMode,
          setLeftPanelOpen: setLeftPanelOpen as any,
          setIsSidebarOpen: setIsSidebarOpen as any,
        }),
      {
        initialProps: { focusMode: false },
      },
    );

    await waitFor(() => {
      expect(setLeftPanelOpen).toHaveBeenCalledWith(false);
    });
    expect(setIsSidebarOpen).toHaveBeenCalledWith(false);

    setLeftPanelOpen.mockClear();
    setIsSidebarOpen.mockClear();

    act(() => {
      setViewportWidth(1000);
      window.dispatchEvent(new Event("resize"));
    });

    await waitFor(() => {
      expect(setIsSidebarOpen).toHaveBeenCalledWith(false);
    });
    expect(setLeftPanelOpen).not.toHaveBeenCalled();

    setLeftPanelOpen.mockClear();
    setIsSidebarOpen.mockClear();
    rerender({ focusMode: true });

    act(() => {
      setViewportWidth(700);
      window.dispatchEvent(new Event("resize"));
    });

    await waitFor(() => {
      expect(setIsSidebarOpen).not.toHaveBeenCalled();
    });
    expect(setLeftPanelOpen).not.toHaveBeenCalled();
  });
});
