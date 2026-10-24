import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {WorldContextPreview} from './WorldContextPreview';
import {api} from '@/lib/api-client';
import {reviewWorldPreview,finishWorldPreview,pendingWorldPreview} from '@/lib/world-context-preview';
import type {WorldPreview} from '@/lib/api/domains/world-context';
const fixture=():WorldPreview=>({purpose:'SCENE',contextHash:'preview-1',compiledContext:'场景规划',firstSources:[],availableSources:[],
 preferences:{worldId:'w',revision:4,reviewedVersionId:'v1',choices:{},reviewedHashes:{}},
 selection:{compilerVersion:'world-context-v1',contextHash:'selection',selectionRevision:4,binding:{worldId:'w',versionId:'v1',name:'退潮世界',sequenceNumber:1,sourceType:'STORY'},tokenBudget:500,tokenUsed:0,content:'',included:[],issues:['REQUIRED_CONTEXT_TOO_LARGE:overview'],excluded:[{id:'overview',type:'OVERVIEW',title:'世界概览',content:'完整概览',contentHash:'hash',choice:'AUTO',reason:'CAPACITY_EXCLUDED',estimatedTokens:501,required:true}]}});
afterEach(()=>{const pending=pendingWorldPreview();if(pending)finishWorldPreview(pending);cleanup();vi.restoreAllMocks();});
describe('world context review',()=>{
 it('blocks over-capacity context and persists a choice before confirmation',async()=>{
   const first=fixture(),next=fixture();next.preferences.revision=5;next.preferences.choices={overview:'EXCLUDE'};next.contextHash='preview-2';next.selection!.issues=[];next.selection!.excluded[0].choice='EXCLUDE';
   vi.spyOn(api.worldContext,'save').mockResolvedValue(next.preferences);vi.spyOn(api.worldContext,'preview').mockResolvedValue(next);
   render(<WorldContextPreview/>);let promise:Promise<WorldPreview>;act(()=>{promise=reviewWorldPreview('/scope',{},first);});void promise!.catch(()=>{});
   expect((screen.getByRole('button',{name:'确认材料，继续报价'}) as HTMLButtonElement).disabled).toBe(true);
   fireEvent.change(screen.getByLabelText('世界概览 材料选择'),{target:{value:'EXCLUDE'}});
   await waitFor(()=>expect((screen.getByRole('button',{name:'确认材料，继续报价'}) as HTMLButtonElement).disabled).toBe(false));
   expect(api.worldContext.save).toHaveBeenCalledWith('/scope',first,{overview:'EXCLUDE'});
   fireEvent.click(screen.getByRole('button',{name:'确认材料，继续报价'}));expect((await promise!).contextHash).toBe('preview-2');
 });
 it('keeps concurrent-save failures visible and requires refreshing',async()=>{
   vi.spyOn(api.worldContext,'save').mockRejectedValue(new Error('conflict'));render(<WorldContextPreview/>);
   let promise:Promise<WorldPreview>;act(()=>{promise=reviewWorldPreview('/scope',{},fixture());});void promise!.catch(()=>{});
   fireEvent.change(screen.getByLabelText('世界概览 材料选择'),{target:{value:'EXCLUDE'}});
   await waitFor(()=>expect(screen.getAllByRole('alert').length).toBe(2));expect((screen.getByRole('button',{name:'确认材料，继续报价'}) as HTMLButtonElement).disabled).toBe(true);
 });
});
