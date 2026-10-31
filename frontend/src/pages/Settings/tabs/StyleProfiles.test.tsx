import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import StyleProfiles from "./StyleProfiles";

const { toast } = vi.hoisted(() => ({ toast: vi.fn() }));
vi.mock("@/components/ui/use-toast", () => ({ useToast: () => ({ toast }) }));
vi.mock("recharts", () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  return Object.fromEntries(["Radar", "RadarChart", "PolarGrid", "PolarAngleAxis", "ResponsiveContainer"].map(name => [name, Container]));
});

beforeEach(() => vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); toast.mockClear(); });

it.each([
  { profiles: [{ id: "profile", name: "损坏画像", dimensions: { pacing: "invalid" } }], voices: [] },
  { profiles: [], voices: [{ id: "voice", sampleDialogues: [{ context: "缺少对白" }] }] },
])("rejects corrupt settings on refresh and preserves the author's draft", async ({ profiles, voices }) => {
  vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story", title: "验收作品" }] as never);
  vi.spyOn(api.stories, "listCharacters").mockResolvedValue([]);
  const listProfiles = vi.spyOn(api.v2.style, "listProfiles").mockResolvedValue([]);
  const listVoices = vi.spyOn(api.v2.style, "listVoices").mockResolvedValue([]);
  render(<StyleProfiles initialStoryId="story" />);
  const refresh = screen.getByRole("button", { name: "刷新" });
  await waitFor(() => expect(refresh.hasAttribute("disabled")).toBe(false));
  const draft = screen.getAllByRole("textbox")[0] as HTMLInputElement;
  fireEvent.change(draft, { target: { value: "作者尚未保存的画像名" } });
  listProfiles.mockResolvedValue(profiles as never);
  listVoices.mockResolvedValue(voices as never);
  fireEvent.click(refresh);
  await waitFor(() => expect(toast).toHaveBeenCalledWith(expect.objectContaining({ variant: "destructive" })));
  expect(draft.value).toBe("作者尚未保存的画像名");
  expect(screen.queryByText("损坏画像")).toBeNull();
});
