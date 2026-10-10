import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { api } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import type { LanguageReport, LanguagePatch } from "@/lib/api/domains/language-quality";
import type { Manuscript } from "@/types";
import { LanguageQualityPanel } from "./LanguageQualityPanel";
vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: (start: Promise<unknown>) => start }));
const id = "11111111-1111-4111-8111-111111111111";
const manuscript: Manuscript = { id, outlineId: id, title: "验收作品", currentBranchId: id, version: 1, sections: { scene: "<p>找这个动作还在。</p>" }, updatedAt: "2026-10-07T00:00:00Z" };
const report = (): LanguageReport => ({
  id, manuscriptId: id, sceneId: "scene", source: { branchId: id, bodyVersion: 1, snapshotId: id, projectionVersion: "QUALITY_PARAGRAPH_V2", htmlHash: "hash", textHash: "hash", contextVersion: "context", standardVersion: "zh-naturalness-v1" },
  status: "ISSUES", summary: "发现生硬表达。", issues: [{ id: "0-0", kind: "LANGUAGE", category: "AWKWARD", quote: "找这个动作还在。", impact: "把人物想法写成动作的存在。", direction: "说清人物仍想去找。", start: 0, end: 8, location: "EXACT", paragraph: 0, context: "找这个动作还在。", currentStart: 0, currentEnd: 8, availability: "AVAILABLE" }],
  coverage: [{ index: 0, start: 0, end: 8, contextStart: 0, contextEnd: 8, state: "COMPLETE", reason: null }], patches: [], operationId: id, canContinueBatch: true, createdAt: "2026-10-07T00:00:00Z",
});
const patch = (): LanguagePatch => ({ id, reportId: id, issueId: "0-0", original: "找这个动作还在。", replacement: "她还是想去找。", beforeContext: "找这个动作还在。", afterContext: "她还是想去找。", review: { language: "PASS", languageReason: "表达自然", meaning: "UNCERTAIN", meaningReason: "还需作者核对", changes: [] }, status: "READY", applicability: "UNCERTAIN", operationId: id, appliedSnapshotId: null, undoneSnapshotId: null, createdAt: "2026-10-07T00:00:00Z" });
const props = () => ({ manuscript, sceneId: "scene", content: manuscript.sections.scene, dirty: false, active: true, busy: false, save: vi.fn(), onApplied: vi.fn() });
const wrapper = () => createQueryClientWrapper(createTestQueryClient());
describe("语言审阅", () => {
  beforeEach(() => {
    vi.spyOn(api.language, "settings").mockResolvedValue({ available: true, generationStandard: true, checkAfterGeneration: true, standardVersion: "zh-naturalness-v1" });
    vi.spyOn(api.language, "reports").mockResolvedValue([report()]);
    vi.spyOn(api.language, "suggest").mockResolvedValue({ operationId: id });
  });
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it("信息不足是正常结果，显示原因且没有采纳或重新生成入口", async () => {
    vi.mocked(api.language.reports).mockResolvedValue([{ ...report(), patches: [{ ...patch(), status: "NEEDS_CONTEXT", applicability: "NEEDS_CONTEXT", reason: "无法确定谁划掉名字", replacement: null, review: null }] }]);
    render(<LanguageQualityPanel {...props()} />, { wrapper: wrapper() });
    await screen.findByText("缺少必要信息，未生成修改建议");
    expect(screen.getByText("无法确定谁划掉名字")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /采纳/ })).toBeNull();
    expect(screen.queryByRole("button", { name: "生成修改建议" })).toBeNull();
    expect(api.language.suggest).not.toHaveBeenCalled();
  });
  it("诊断结果不会自动生成建议，点击后才调用", async () => {
    render(<LanguageQualityPanel {...props()} />, { wrapper: wrapper() });
    await screen.findByText("把人物想法写成动作的存在。"); expect(api.language.suggest).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "生成修改建议" }));
    await waitFor(() => expect(api.language.suggest).toHaveBeenCalledTimes(1));
  });
  it("未保存的修改立即令报告过期并禁用采纳", async () => {
    vi.mocked(api.language.reports).mockResolvedValue([{ ...report(), patches: [patch()] }]);
    const p = props(); const view = render(<LanguageQualityPanel {...p} />, { wrapper: wrapper() });
    await screen.findByRole("button", { name: "已核对，仍采纳" });
    view.rerender(<LanguageQualityPanel {...p} content="新写的句子" dirty />);
    expect(screen.getByRole("status").textContent).toBe("报告已过期");
    expect(screen.getByRole("button", { name: "已核对，仍采纳" }).hasAttribute("disabled")).toBe(true);
  });
  it("迟到报告不能进入另一个场景", async () => {
    let resolve!: (value: LanguageReport[]) => void;
    vi.mocked(api.language.reports).mockImplementation((_m, scene) => scene === "scene" ? new Promise(r => { resolve = r; }) : Promise.resolve([]));
    const p = props(); const view = render(<LanguageQualityPanel {...p} />, { wrapper: wrapper() });
    await waitFor(() => expect(api.language.reports).toHaveBeenCalled());
    view.rerender(<LanguageQualityPanel {...p} sceneId="other-scene" />); resolve([report()]);
    await screen.findByText(/尚无语言报告/); expect(screen.queryByText("把人物想法写成动作的存在。")).toBeNull();
  });
  it("提交采纳期间新写的草稿不会被迟到正文覆盖", async () => {
    const candidate = patch(); vi.mocked(api.language.reports).mockResolvedValue([{ ...report(), patches: [candidate] }]);
    let resolve!: (value: Awaited<ReturnType<typeof api.language.decide>>) => void;
    vi.spyOn(api.language, "decide").mockImplementation(() => new Promise(r => { resolve = r; }));
    const p = props(); const view = render(<LanguageQualityPanel {...p} />, { wrapper: wrapper() });
    fireEvent.click(await screen.findByRole("button", { name: "已核对，仍采纳" }));
    await waitFor(() => expect(api.language.decide).toHaveBeenCalledTimes(1));
    view.rerender(<LanguageQualityPanel {...p} content="作者还在写" dirty />);
    resolve({ patch: candidate, branchId: id, bodyVersion: 2, content: "<p>服务端候选</p>" });
    await screen.findByText(/未覆盖本地草稿/); expect(p.onApplied).not.toHaveBeenCalled();
  });
  it("明确内容改变不能采纳，完整差异仍展示", async () => {
    const candidate = { ...patch(), applicability: "BLOCKED", review: { ...patch().review!, meaning: "FAIL" as const, meaningReason: "删除了否定" } };
    vi.mocked(api.language.reports).mockResolvedValue([{ ...report(), patches: [candidate] }]);
    render(<LanguageQualityPanel {...props()} />, { wrapper: wrapper() });
    expect((await screen.findByRole("button", { name: "采纳此项" })).hasAttribute("disabled")).toBe(true);
    expect(screen.getByText(/删除了否定/)).toBeTruthy(); expect(screen.getByText("完整差异（删除／新增）")).toBeTruthy();
  });
  it("场景组件卸载后不应用迟到的采纳响应", async () => {
    const candidate=patch();vi.mocked(api.language.reports).mockResolvedValue([{...report(),patches:[candidate]}]);
    let resolve!: (value: Awaited<ReturnType<typeof api.language.decide>>) => void;
    vi.spyOn(api.language,"decide").mockImplementation(()=>new Promise(r=>{resolve=r;}));
    const p=props();const view=render(<LanguageQualityPanel {...p}/>,{wrapper:wrapper()});
    fireEvent.click(await screen.findByRole("button",{name:"已核对，仍采纳"}));
    await waitFor(()=>expect(api.language.decide).toHaveBeenCalled());view.unmount();
    resolve({patch:candidate,branchId:id,bodyVersion:2,content:"<p>服务端候选</p>"});
    await new Promise(r=>setTimeout(r,0));expect(p.onApplied).not.toHaveBeenCalled();
  });
  it("完整显示超过十二项问题并分离文风和观察", async () => {
    const r = report(); r.issues = Array.from({ length: 15 }, (_, i) => ({ ...r.issues[0], id: `0-${i}`, kind: i === 13 ? "STYLE" : i === 14 ? "OBSERVATION" : "LANGUAGE" }));
    vi.mocked(api.language.reports).mockResolvedValue([r]); render(<LanguageQualityPanel {...props()} />, { wrapper: wrapper() });
    expect(await screen.findByText("明确语言问题（13）")).toBeTruthy(); expect(screen.getByText("文风建议（1）")).toBeTruthy(); expect(screen.getAllByRole("button", { name: "生成修改建议" }).length).toBe(15);
  });
});
