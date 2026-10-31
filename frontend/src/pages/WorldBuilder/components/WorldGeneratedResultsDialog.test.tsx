import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { WorldModuleDefinition } from "@/types";
import { WorldGeneratedResultsDialog } from "./WorldGeneratedResultsDialog";

vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key: string, options?: { field: string }) => options?.field ? `${key}:${options.field}` : key }) }));
vi.mock("@/lib/error-messages", () => ({ localizedErrorMessage: (_error: unknown, fallback: string) => fallback }));
vi.mock("@/lib/api-client", () => ({ isApiError: (error: unknown) => error instanceof Error && "status" in error, api: {
  worlds: { generatedResults: vi.fn(), update: vi.fn(), publish: vi.fn(), startGenerateEntry: vi.fn() },
  aiOperations: { retry: vi.fn() },
} }));

type Results = Awaited<ReturnType<typeof api.worlds.generatedResults>>;
const definitions: WorldModuleDefinition[] = [{ key: "geography", label: "地理环境", description: "", fields: [
  { key: "terrain", label: "地形地貌", type: "textarea" },
] }];
const originalClipboard = Object.getOwnPropertyDescriptor(navigator, "clipboard");
const writeText = vi.fn();
const baseResult = (): Results => ({
  worldId: "world-a", operationId: "operation-a",
  modules: { geography: { terrain: "  海上山城\n雾中的码头。  " }, society: { politics: "城邦议会" } },
  appliedModules: ["geography"],
});
const props = { worldId: "world-a", operationId: "operation-a", open: true, onOpenChange: vi.fn(), definitions };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.worlds.generatedResults).mockResolvedValue(baseResult());
  writeText.mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
});
afterEach(() => {
  cleanup();
  if (originalClipboard) Object.defineProperty(navigator, "clipboard", originalClipboard);
  else Reflect.deleteProperty(navigator, "clipboard");
});

describe("world generated result review", () => {
  it("only reads results when opened, shows saved state, and copies exact text without mutating or retrying", async () => {
    const view = render(<WorldGeneratedResultsDialog {...props} open={false} />);
    expect(api.worlds.generatedResults).not.toHaveBeenCalled();
    view.rerender(<WorldGeneratedResultsDialog {...props} />);

    await screen.findByText("城邦议会");
    expect(screen.getByText("地理环境")).toBeTruthy();
    expect(screen.getByText("地形地貌")).toBeTruthy();
    expect(screen.getByText("worldGeneratedResults.applied")).toBeTruthy();
    expect(screen.getByText("worldGeneratedResults.unapplied")).toBeTruthy();
    expect(api.worlds.generatedResults).toHaveBeenCalledExactlyOnceWith("world-a", "operation-a");

    fireEvent.click(screen.getByRole("button", { name: "worldGeneratedResults.copyField:地形地貌" }));
    await screen.findByText("worldGeneratedResults.copied");
    expect(writeText).toHaveBeenCalledExactlyOnceWith("  海上山城\n雾中的码头。  ");
    expect(api.worlds.update).not.toHaveBeenCalled();
    expect(api.worlds.publish).not.toHaveBeenCalled();
    expect(api.worlds.startGenerateEntry).not.toHaveBeenCalled();
    expect(api.aiOperations.retry).not.toHaveBeenCalled();
  });

  it("retries only the read request after a loading error", async () => {
    vi.mocked(api.worlds.generatedResults).mockRejectedValueOnce(new Error("connection failed"));
    render(<WorldGeneratedResultsDialog {...props} />);
    await screen.findByRole("alert");
    expect(screen.queryByText("城邦议会")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "worldGeneratedResults.retry" }));

    await screen.findByText("城邦议会");
    expect(api.worlds.generatedResults).toHaveBeenCalledTimes(2);
    expect(vi.mocked(api.worlds.generatedResults).mock.calls).toEqual([["world-a", "operation-a"], ["world-a", "operation-a"]]);
    expect(api.aiOperations.retry).not.toHaveBeenCalled();
  });

  it("ignores an old response after the selected operation changes", async () => {
    const earlier = deferred<Results>();
    vi.mocked(api.worlds.generatedResults).mockReturnValueOnce(earlier.promise).mockResolvedValueOnce({
      worldId: "world-b", operationId: "operation-b", modules: { geography: { terrain: "新世界" } }, appliedModules: [],
    });
    const view = render(<WorldGeneratedResultsDialog {...props} />);
    view.rerender(<WorldGeneratedResultsDialog {...props} worldId="world-b" operationId="operation-b" />);
    await screen.findByText("新世界");

    await act(async () => { earlier.resolve(baseResult()); });

    expect(screen.getByText("新世界")).toBeTruthy();
    expect(screen.queryByText("城邦议会")).toBeNull();
  });

  it("explains why a historical task without a baseline cannot load generated results", async () => {
    vi.mocked(api.worlds.generatedResults).mockRejectedValueOnce(Object.assign(new Error("WORLD_TASK_BASELINE_REQUIRED"), { status: 409 }));
    render(<WorldGeneratedResultsDialog {...props} />);

    await screen.findByText("worlds.taskBaselineMissing");

    expect(screen.queryByText("worldGeneratedResults.loadFailed")).toBeNull();
    expect(api.worlds.generatedResults).toHaveBeenCalledExactlyOnceWith("world-a", "operation-a");
    expect(api.aiOperations.retry).not.toHaveBeenCalled();
  });

  it("does not display results from a mismatched operation response", async () => {
    vi.mocked(api.worlds.generatedResults).mockResolvedValue({ ...baseResult(), operationId: "another-operation" });
    render(<WorldGeneratedResultsDialog {...props} />);

    await screen.findByRole("alert");

    expect(screen.queryByText("城邦议会")).toBeNull();
    expect(screen.getByRole("button", { name: "worldGeneratedResults.retry" })).toBeTruthy();
  });

  it("keeps generated text visible when clipboard access fails", async () => {
    writeText.mockRejectedValueOnce(new Error("clipboard denied"));
    render(<WorldGeneratedResultsDialog {...props} />);
    await screen.findByText("城邦议会");

    fireEvent.click(screen.getByRole("button", { name: "worldGeneratedResults.copyField:politics" }));

    await screen.findByText("worldGeneratedResults.copyFailed");
    expect(screen.getByText("城邦议会")).toBeTruthy();
    expect(api.worlds.generatedResults).toHaveBeenCalledTimes(1);
    expect(api.aiOperations.retry).not.toHaveBeenCalled();
  });

  it("shows an empty result without offering a generation action", async () => {
    vi.mocked(api.worlds.generatedResults).mockResolvedValue({ ...baseResult(), modules: { geography: {} }, appliedModules: [] });
    render(<WorldGeneratedResultsDialog {...props} />);

    await screen.findByText("worldGeneratedResults.empty");

    expect(screen.queryByRole("button", { name: /worldGeneratedResults.copyField/ })).toBeNull();
    expect(api.worlds.startGenerateEntry).not.toHaveBeenCalled();
  });

  it("ignores a response received after closing and fetches again on reopening", async () => {
    const earlier = deferred<Results>();
    vi.mocked(api.worlds.generatedResults).mockReturnValueOnce(earlier.promise).mockResolvedValueOnce({
      ...baseResult(), modules: { geography: { terrain: "更新输出" } },
    });
    const view = render(<WorldGeneratedResultsDialog {...props} />);
    view.rerender(<WorldGeneratedResultsDialog {...props} open={false} />);
    await act(async () => { earlier.resolve(baseResult()); });
    expect(screen.queryByRole("dialog")).toBeNull();

    view.rerender(<WorldGeneratedResultsDialog {...props} />);

    await screen.findByText("更新输出");
    await waitFor(() => expect(api.worlds.generatedResults).toHaveBeenCalledTimes(2));
    expect(screen.queryByText("城邦议会")).toBeNull();
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(resolvePromise => { resolve = resolvePromise; });
  return { promise, resolve };
}
