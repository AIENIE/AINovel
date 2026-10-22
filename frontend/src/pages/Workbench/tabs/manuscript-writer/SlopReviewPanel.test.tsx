import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { api } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import { SlopReviewPanel } from "./SlopReviewPanel";
import type { Manuscript, SlopQualityRun, SlopReview } from "@/types";
import type { LanguageReport } from "@/lib/api/domains/language-quality";
vi.mock("@/lib/ai-operation-store", () => ({ runTrackedAiOperation: (start: Promise<unknown>) => start }));
const id="11111111-1111-4111-8111-111111111111";
const manuscript: Manuscript={ id, outlineId:id, title:"稿件", currentBranchId:id, version:1, sections:{scene:"<p>动作还在。</p>"}, updatedAt:"2026-10-22T00:00:00Z" };
const source={branchId:id,bodyVersion:1,snapshotId:id,projectionVersion:"QUALITY_PARAGRAPH_V2",htmlHash:"hash",textHash:"hash",contextVersion:"context",standardVersion:"zh-naturalness-v3"};
function review(): SlopReview { return {source,promptVersion:"slop-evidence-v2",coverage:"COMPLETE",reason:"已检查",localRisk:88,localSignals:[],languageReportId:null,
  findings:[{id,judgment:"CONFIRMED",kind:"LANGUAGE",category:"AWKWARD",quote:"动作还在。",explanation:"抽象化生硬",advice:"说清意思",risk:40,location:{start:0,end:5,trust:"RELOCATED",replaceable:true}}]}; }
function run(): SlopQualityRun { return {id,storyId:id,manuscriptId:id,sceneId:"scene",status:"ACCEPTED",maxSeverity:"MEDIUM",overallRiskScore:40,revised:false,revisionCount:0,moduleScores:{},alternativeExplanations:[],revisionPriorities:[],rewriteTasks:[],issues:[],review:review()}; }
function props(r=run()) { return { manuscript, sceneId:"scene",content:manuscript.sections.scene,dirty:false,active:true,busy:false,run:r,onApplied:vi.fn() }; }
const wrapper=()=>createQueryClientWrapper(createTestQueryClient());
beforeEach(()=>{vi.spyOn(api.language,"settings").mockResolvedValue({available:true,generationStandard:false,checkAfterGeneration:false,standardVersion:"zh-naturalness-v3"});vi.spyOn(api.v2.quality,"listRuns").mockResolvedValue([]);vi.spyOn(api.language,"slopSuggest").mockResolvedValue({operationId:id});});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
it("只有确认且可定位的语言问题能生成候选，显示引文重定位且不自动调用",async()=>{
  const r=run();r.review!.findings.push({...r.review!.findings[0],id:"style",kind:"STYLE"},{...r.review!.findings[0],id:"artifact",kind:"OUTPUT_ARTIFACT"},{...r.review!.findings[0],id:"dismissed",judgment:"DISMISSED"});
  render(<SlopReviewPanel {...props(r)} />,{wrapper:wrapper()});
  const button=await screen.findByRole("button",{name:"生成局部候选"});await waitFor(()=>expect(button.hasAttribute("disabled")).toBe(false));
  expect(screen.getAllByRole("button",{name:"生成局部候选"})).toHaveLength(1);expect(api.language.slopSuggest).not.toHaveBeenCalled();
  fireEvent.click(button);await waitFor(()=>expect(api.language.slopSuggest).toHaveBeenCalledWith(id,id,id));
});
it("超限显示未检查，失效来源不能生成候选",async()=>{
  const r=run();r.review!.coverage="INPUT_LIMIT";r.review!.reason="正文未检查：输入超限";r.review!.source={...source,bodyVersion:0};
  render(<SlopReviewPanel {...props(r)} />,{wrapper:wrapper()});
  expect(await screen.findByText("正文未检查：输入超限")).toBeTruthy();expect(screen.getByText(/正文未完整检查/)).toBeTruthy();
  expect(screen.getByRole("button",{name:"生成局部候选"}).hasAttribute("disabled")).toBe(true);
});
it("历史报告仅可读，不补造来源或候选",()=>{
  const r=run();r.review=null;render(<SlopReviewPanel {...props(r)} />,{wrapper:wrapper()});
  expect(screen.getByText(/历史 Slop 报告缺少可靠来源快照/)).toBeTruthy();expect(screen.queryByRole("button",{name:"生成局部候选"})).toBeNull();
});
it("语义反转复核 FAIL 不能采纳，完整段落仍供作者核对",async()=>{
  const r=run();r.review!.languageReportId=id;
  const report: LanguageReport={id,manuscriptId:id,sceneId:"scene",source,status:"ISSUES",summary:"Slop 来源",issues:[],coverage:[],operationId:null,canContinueBatch:true,createdAt:"2026-10-22",patches:[{
    id,reportId:id,issueId:id,original:"没有交出钥匙。",replacement:"交出了钥匙。",beforeContext:"他没有交出钥匙。她在门外等着。",afterContext:"他交出了钥匙。她在门外等着。",review:{language:"PASS",languageReason:"通顺",meaning:"FAIL",meaningReason:"否定反转",changes:["改变了事件"]},status:"READY",applicability:"BLOCKED",operationId:id,appliedSnapshotId:null,undoneSnapshotId:null,createdAt:"2026-10-22"}]};
  vi.spyOn(api.language,"report").mockResolvedValue(report);const p=props(r);render(<SlopReviewPanel {...p} />,{wrapper:wrapper()});
  const button=await screen.findByRole("button",{name:"采纳此项"});expect(button.hasAttribute("disabled")).toBe(true);
  expect(screen.getByText("他没有交出钥匙。她在门外等着。")).toBeTruthy();expect(p.onApplied).not.toHaveBeenCalled();
});
