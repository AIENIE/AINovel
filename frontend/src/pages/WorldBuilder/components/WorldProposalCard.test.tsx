import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api-client";
import type { WorldProposal } from "@/lib/api/domains/world-assistant";
import { WorldProposalCard, type ProposalDraft } from "./WorldProposalCard";
import type { WorldEditorState } from "../useWorldDiscussion";
import { useWorldDraft } from "../useWorldDraft";
const original={title:"潮汐塔",type:"LOCATION",summary:"",content:"作者原文",aliases:[],tags:[]};
const proposal=(extra:Partial<WorldProposal>={}):WorldProposal=>({id:"p",turnId:"t",sessionId:"s",parentProposalId:null,action:"UPDATE",resourceType:"ENTRY",targetId:"e",fieldKey:null,status:"READY",revision:0,draftRevision:1,before:original,candidate:{...original,content:"模型候选"},modelCandidate:{...original,content:"模型候选"},current:original,rationale:"让约束更清晰",sources:[],sourceTurnIds:[],conflicts:[],contextChanges:[],candidateEdited:false,contextChanged:false,applicable:true,staleReason:null,createdAt:"2026-10-23",...extra});
let editor:WorldEditorState;let current:WorldProposal;
beforeEach(()=>{current=proposal();vi.spyOn(api.worldAssistant,"proposal").mockImplementation(async()=>current);editor={draft:{id:"world",revision:1,name:"潮汐城",tagline:"",themes:[],creativeIntent:"",notes:"",status:"draft",version:"v1",updatedAt:"",modules:[]},baseline:null,remote:null,loading:false,saving:false,error:null,conflict:false,dirty:false,id:"world",edit:vi.fn(),save:vi.fn(async()=>true),refresh:vi.fn(async()=>{}),resolveRemote:vi.fn(),canAct:()=>true};});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
function mount(drafts=new Map<string,ProposalDraft>()) {const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return {client,...render(<QueryClientProvider client={client}><WorldProposalCard world="world" proposalId="p" editor={editor} working={false} drafts={drafts} onDirty={vi.fn()} onRefine={vi.fn()} onReorganize={vi.fn()}/></QueryClientProvider>)};}
it("retains the edited candidate across unmounts and requires saving it before acceptance",async()=>{
  const drafts=new Map<string,ProposalDraft>();const first=mount(drafts);await screen.findByRole("button",{name:"采纳并保存"});
  fireEvent.click(screen.getByText("查看完整候选并手动修改"));fireEvent.change(screen.getByLabelText("正文"),{target:{value:"作者修改候选"}});
  expect(screen.getByRole("button",{name:"采纳并保存"}).hasAttribute("disabled")).toBe(true);first.unmount();mount(drafts);await screen.findByRole("button",{name:"保存候选"});
  fireEvent.click(screen.getByText("查看完整候选并手动修改"));expect((screen.getByLabelText("正文") as HTMLTextAreaElement).value).toBe("作者修改候选");
  const edit=vi.spyOn(api.worldAssistant,"editProposal").mockImplementation(async(_w,_p,_r,c)=>{current=proposal({revision:1,candidate:c,candidateEdited:true});return current;});
  fireEvent.click(screen.getByRole("button",{name:"保存候选"}));await waitFor(()=>expect(edit).toHaveBeenCalledOnce());await waitFor(()=>expect(screen.getByRole("button",{name:"采纳并保存"}).hasAttribute("disabled")).toBe(false));expect(current.modelCandidate.content).toBe("模型候选");
});
it("requires context review but blocks changed targets",async()=>{
  current=proposal({contextChanged:true});const {client}=mount();const button=await screen.findByRole("button",{name:"采纳并保存"});expect(button.hasAttribute("disabled")).toBe(true);
  fireEvent.click(screen.getByRole("checkbox"));expect(button.hasAttribute("disabled")).toBe(false);
  current=proposal({applicable:false,staleReason:"WORLD_PROPOSAL_STALE",current:{...original,content:"后来的正文"}});await act(()=>client.invalidateQueries());
  await waitFor(()=>expect(screen.getByRole("button",{name:"采纳并保存"}).hasAttribute("disabled")).toBe(true));expect(screen.getByRole("button",{name:"基于当前稿重新整理"})).toBeTruthy();
});
it("blocks acceptance while the editor has unsaved changes",async()=>{editor.canAct=()=>false;editor.dirty=true;const decide=vi.spyOn(api.worldAssistant,"decide");mount();await screen.findByRole("button",{name:"采纳并保存"});fireEvent.click(screen.getByRole("button",{name:"采纳并保存"}));expect(decide).not.toHaveBeenCalled();expect(screen.getByRole("button",{name:"保存世界草稿"})).toBeTruthy();});
it("retries an uncertain save with the same receipt identity",async()=>{
  const decide=vi.spyOn(api.worldAssistant,"decide").mockRejectedValueOnce(new ApiError(503,"offline")).mockImplementation(async()=>{current=proposal({status:"ACCEPTED",revision:1,applicable:false});return {proposal:current,draftRevision:2};});mount();fireEvent.click(await screen.findByRole("button",{name:"采纳并保存"}));fireEvent.click(await screen.findByRole("button",{name:"核对本次保存"}));
  await waitFor(()=>expect(decide).toHaveBeenCalledTimes(2));expect(decide.mock.calls[0]).toEqual(decide.mock.calls[1]);
});
it("preserves typing that arrives while an accepted proposal is being refreshed",async()=>{
  const world={...editor.draft!,entries:[{id:"e",...original,type:"LOCATION" as const,legacyModuleKey:null,legacyFieldKey:null}]};vi.spyOn(api.worlds,"getDetail").mockResolvedValue(world);
  let resolve!:(value:{proposal:WorldProposal;draftRevision:number})=>void;vi.spyOn(api.worldAssistant,"decide").mockImplementation(()=>new Promise(yes=>{resolve=yes;}));
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}}),drafts=new Map<string,ProposalDraft>();
  function Harness(){const draft=useWorldDraft("world");return <><textarea aria-label="继续输入" value={draft.draft?.notes||""} onChange={e=>draft.edit(w=>({...w,notes:e.target.value}))}/><WorldProposalCard world="world" proposalId="p" editor={draft} working={false} drafts={drafts} onDirty={()=>{}} onRefine={()=>{}} onReorganize={()=>{}}/></>;}
  render(<QueryClientProvider client={client}><Harness/></QueryClientProvider>);const accept=await screen.findByRole("button",{name:"采纳并保存"});await waitFor(()=>expect(accept.hasAttribute("disabled")).toBe(false));fireEvent.click(accept);fireEvent.change(screen.getByLabelText("继续输入"),{target:{value:"采纳期间继续输入的备注"}});
  current=proposal({status:"ACCEPTED",revision:1,applicable:false});vi.mocked(api.worlds.getDetail).mockResolvedValue({...world,revision:2});await act(async()=>resolve({proposal:current,draftRevision:2}));expect((screen.getByLabelText("继续输入") as HTMLTextAreaElement).value).toBe("采纳期间继续输入的备注");
});
it("edits event dates and dependency arrays without converting structured candidates to text",async()=>{
  const event={title:"启航",summary:"",content:"舰队出港",aliases:[],tags:[],date:{start:{year:-2},end:{year:-2}},entryIds:["pending-port"],eraIds:["era"],sortOrder:0};
  current=proposal({resourceType:"EVENT",action:"CREATE",before:null,current:null,candidate:event,modelCandidate:event,applicable:false,staleReason:"WORLD_PROPOSAL_DEPENDENCY_MISSING"});
  editor.draft={...editor.draft!,timeline:{schemaVersion:1,calendarName:"潮历",eras:[{id:"era",name:"迁徙纪",description:"",date:null,sortOrder:0}],events:[]}};
  const drafts=new Map<string,ProposalDraft>();mount(drafts);await screen.findByRole("button",{name:"采纳并保存"});fireEvent.click(screen.getByText("查看完整候选并手动修改"));
  fireEvent.change(screen.getByLabelText("日期 开始 年"),{target:{value:"0"}});fireEvent.click(screen.getByRole("checkbox",{name:/pending-port/}));
  expect(drafts.get("p")?.candidate).toMatchObject({date:{start:{year:0},end:{year:0}},entryIds:[],eraIds:["era"]});
  expect(screen.getByRole("button",{name:"采纳并保存"}).hasAttribute("disabled")).toBe(true);expect(screen.getByRole("button",{name:"保存候选"})).toBeTruthy();
});
