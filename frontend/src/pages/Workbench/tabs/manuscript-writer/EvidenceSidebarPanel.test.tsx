import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EvidenceSidebarPanel } from "./EvidenceSidebarPanel";
import type { Manuscript } from "@/types";

const mocks = vi.hoisted(() => ({ package: vi.fn(), settings: vi.fn(), versions: vi.fn(), branches: vi.fn(), tasks: vi.fn(), task: vi.fn(), citations: vi.fn(), saveSettings: vi.fn(), hints: vi.fn(), search: vi.fn(), savePackage: vi.fn(), preview:vi.fn() }));
vi.mock("@/lib/api-client", () => ({ api: { evidence: mocks, v2: { version: { listVersions: mocks.versions, listBranches: mocks.branches } } } }));
const manuscript: Manuscript = { id: "manuscript", outlineId: "outline", title: "稿件", version: 1, currentBranchId: "branch", sections: {}, updatedAt: "2026-10-03T00:00:00Z" };
const props = { storyId: "story", manuscript, sceneId: "scene", content: "雨桥", dirty: false, active: true };
const settings = { version: 1, semanticProfile: "basic", hints: false, checks: false, rerank: false, bindings: [] };
async function flush() { await act(async () => { await Promise.resolve(); await Promise.resolve(); }); }
beforeEach(() => {
  vi.useFakeTimers(); vi.setSystemTime(new Date("2026-10-03T00:00:00Z")); vi.resetAllMocks();
  mocks.package.mockResolvedValue({ version: 0, pinned: [], excluded: [] }); mocks.settings.mockResolvedValue(settings); mocks.tasks.mockResolvedValue([]); mocks.versions.mockResolvedValue([]); mocks.branches.mockResolvedValue([]);
  mocks.saveSettings.mockImplementation(async (_story, input) => ({ ...settings, ...input, version: 2 })); mocks.hints.mockResolvedValue({ items: [] }); mocks.search.mockResolvedValue({ items: [] });
  mocks.citations.mockResolvedValue([]);
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe("EvidenceSidebarPanel", () => {
  it("opens the original machine-association report without running another check", async () => {
    const task = { id: "original-report", kind: "CHECK", status: "COMPLETED", errorCode: null, result: null, stale: false, callsReserved: 1, externalLimit: 24 };
    mocks.citations.mockResolvedValue([{ id: "possible", scene_id: "scene", relation_type: "POSSIBLE", state: "CURRENT", quote: "日落后", report_task_id: task.id }]);
    mocks.task.mockResolvedValue(task);
    render(<EvidenceSidebarPanel {...props} />); await flush();
    fireEvent.click(screen.getByRole("button", { name: "查看关联的核验报告", hidden: true })); await flush();
    expect(mocks.task).toHaveBeenCalledWith(task.id);
    expect((document.getElementById("evidence-report-original-report") as HTMLDetailsElement).open).toBe(true);
    expect(mocks.preview).not.toHaveBeenCalled();
  });
  it("freezes only the author-selected chapter scenes in a cost preview", async () => {
    mocks.versions.mockResolvedValue([{ id:"body",label:"检查点",branchId:"branch" }]);
    mocks.branches.mockResolvedValue([{ id:"branch",name:"主线" }]);
    mocks.preview.mockResolvedValue({ available:false,maximumCredits:11,plannedExternal:1,reason:"未启用" });
    const chapters=[{ id:"c1",title:"第一章",scenes:[{ id:"s1" }] },{ id:"c2",title:"第二章",scenes:[{ id:"s2" }] }] as import("@/types").Chapter[];
    render(<EvidenceSidebarPanel {...props} chapters={chapters} />); await flush();
    fireEvent.change(screen.getByRole("combobox",{ name:"正文版本" }),{ target:{ value:"body" } });
    fireEvent.change(screen.getByRole("textbox",{ name:"核验问题" }),{ target:{ value:"通行条件是否一致" } });
    fireEvent.change(screen.getByRole("combobox",{ name:"核验范围" }),{ target:{ value:"custom" } });
    expect((screen.getByRole("button",{ name:"预览费用上限" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole("checkbox",{ name:"第二章" }));
    fireEvent.click(screen.getByRole("button",{ name:"预览费用上限" })); await flush();
    expect(mocks.preview.mock.calls[0][0]).toMatchObject({ scenes:["s2"],bodyVersion:"body",branchId:"branch" });
  });
  it("starts disabled, waits 1500 ms, throttles per scene and aborts observation when closed", async () => {
    let finish!: (value: unknown) => void;
    mocks.hints.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const view = render(<EvidenceSidebarPanel {...props} />); await flush();
    await act(async () => vi.advanceTimersByTime(2000)); expect(mocks.hints).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("checkbox", { name: "自动资料提示" })); await flush();
    await act(async () => vi.advanceTimersByTime(1499)); expect(mocks.hints).not.toHaveBeenCalled();
    await act(async () => vi.advanceTimersByTime(1)); expect(mocks.hints).toHaveBeenCalledTimes(1);
    const signal = mocks.hints.mock.calls[0][3] as AbortSignal;
    view.rerender(<EvidenceSidebarPanel {...props} content="新的雨桥描述" />);
    await act(async () => vi.advanceTimersByTime(1500)); expect(mocks.hints).toHaveBeenCalledTimes(1);
    view.rerender(<EvidenceSidebarPanel {...props} active={false} />); expect(signal.aborted).toBe(true);
    await act(async () => finish({ items: [{ chunkId: "old", title: "旧场景结果", text: "不应显示", reasons: [] }] }));
    expect(screen.queryByText("旧场景结果")).toBeNull();
  });
  it("keeps the same save intent after failure and blocks duplicate delayed submissions", async () => {
    let reject!: (value: Error) => void;
    mocks.savePackage.mockImplementationOnce(() => new Promise((_resolve, fail) => { reject = fail; })).mockResolvedValue({ version: 1, pinned: [], excluded: [] });
    render(<EvidenceSidebarPanel {...props} />); await flush();
    const save = screen.getByRole("button", { name: /保存参考选择/ });
    fireEvent.click(save); fireEvent.click(save); expect(mocks.savePackage).toHaveBeenCalledTimes(1);
    await act(async () => reject(new Error("暂时失败")));
    expect(screen.getByRole("alert").textContent).toContain("暂时失败");
    fireEvent.click(save); await flush(); expect(mocks.savePackage).toHaveBeenCalledTimes(2);
    expect(mocks.savePackage.mock.calls[0][2].requestKey).toBe(mocks.savePackage.mock.calls[1][2].requestKey);
  });
});
