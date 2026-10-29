import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { WorldDetail, WorldModuleDefinition } from "@/types";
import WorldManager from "./WorldManager";

vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock("@/lib/api-client", () => ({ api: { worlds: { list: vi.fn(), getDefinitions: vi.fn(), getDetail: vi.fn(), delete: vi.fn() } } }));
vi.mock("@/lib/error-messages", () => ({ localizedErrorMessage: () => "读取失败" }));
vi.mock("@/utils/toast", () => ({ showError: vi.fn(), showSuccess: vi.fn() }));

const definitions: WorldModuleDefinition[] = [{ key: "geography", label: "地理", description: "", fields: [
  { key: "terrain", label: "地形", type: "textarea" },
  { key: "climate", label: "气候", type: "textarea" },
  { key: "locations", label: "地点", type: "textarea" },
] }];
const world: WorldDetail = {
  id: "islands", name: "退潮群岛", tagline: "潮汐下的城", status: "draft", version: "0.1.0", revision: 0,
  themes: [], creativeIntent: "", notes: "", updatedAt: "2026-10-23T00:00:00Z",
  modules: [{ key: "geography", fields: { terrain: "海岛", climate: "  ", old_custom_field: "旧资料" } }],
};
const show = () => render(<MemoryRouter><WorldManager /></MemoryRouter>);
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.worlds.list).mockResolvedValue([world]);
  vi.mocked(api.worlds.getDefinitions).mockResolvedValue(definitions);
  vi.mocked(api.worlds.getDetail).mockResolvedValue(world);
});
afterEach(cleanup);

it("waits for definitions and counts only defined nonblank fields, without a false 100 percent fallback", async () => {
  let resolve!: (value: WorldModuleDefinition[]) => void;
  vi.mocked(api.worlds.getDefinitions).mockReturnValue(new Promise(done => { resolve = done; }));
  show();
  await act(async () => {});
  expect(screen.queryByText("100%")).toBeNull();
  expect(api.worlds.getDetail).not.toHaveBeenCalled();
  await act(async () => resolve(definitions));
  expect(await screen.findByText("1 / 3")).toBeTruthy();
  expect(screen.getByText("33%")).toBeTruthy();
  expect(api.worlds.list).toHaveBeenCalledTimes(1);
});

it("keeps worlds visible when definitions fail and retries instead of inventing zero coverage", async () => {
  vi.mocked(api.worlds.getDefinitions).mockRejectedValueOnce(new Error("offline"));
  show();
  expect(await screen.findByText("退潮群岛")).toBeTruthy();
  expect(screen.getByText("worlds.statsUnavailable")).toBeTruthy();
  expect(screen.queryByText("0%")).toBeNull();
  expect(screen.queryByText("100%")).toBeNull();
  fireEvent.click(screen.getAllByRole("button", { name: "common.retry" })[0]);
  expect(await screen.findByText("1 / 3")).toBeTruthy();
});

it("shows failed detail reads separately from an empty world", async () => {
  vi.mocked(api.worlds.getDetail).mockRejectedValueOnce(new Error("offline"));
  show();
  expect(await screen.findByText("worlds.statsUnavailable")).toBeTruthy();
  expect(screen.queryByText("0%")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "common.retry" }));
  expect(await screen.findByText("33%")).toBeTruthy();
});
