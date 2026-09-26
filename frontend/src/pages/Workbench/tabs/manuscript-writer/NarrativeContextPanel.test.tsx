import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NarrativeContextPanel } from './NarrativeContextPanel';
import { api, ApiError } from '@/lib/api-client';
import type { ContextState } from '@/types/narrative-context';
import type { Manuscript } from '@/types';

vi.mock('@/contexts/auth-state',()=>({useAuth:()=>({user:{id:'author'}})}));
vi.mock('@/lib/api-client',async(importOriginal)=>{
  const original=await importOriginal<typeof import('@/lib/api-client')>();
  return {...original,api:{narrativeContext:{state:vi.fn(),update:vi.fn(),preview:vi.fn(),history:vi.fn(),candidates:vi.fn()},narrative:{evidence:vi.fn()}}};
});
const base:ContextState={enabled:false,revision:0,settingsRevision:0,manuscriptVersion:1,canonRevision:0,document:null};
const manuscript:Manuscript={id:'m',outlineId:'o',currentBranchId:'b',version:1,title:'测试',sections:{},updatedAt:''};
const mount=()=>render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>
  <NarrativeContextPanel active manuscript={manuscript} sceneId="s" disabled={false} characters={[{id:'p',name:'林青'}]} scenes={[{id:'s',title:'第二场'}]} records={[]}/>
</QueryClientProvider>);
describe('H2 configuration review',()=>{
  afterEach(cleanup);
  beforeEach(()=>{sessionStorage.clear();vi.clearAllMocks();vi.mocked(api.narrativeContext.state).mockResolvedValue(base);});
  it('shows historical safe wording and attribution instead of substituting the current values',async()=>{
    vi.mocked(api.narrativeContext.history).mockResolvedValue([{revision:1,createdAt:'2026-09-22T00:00:00Z',document:{
      policy:{perspective:'LIMITED_THIRD',allowInner:true,viewpointByScene:{s:'p'}},entries:[],grants:[{
        id:'g',recordId:'r',characterId:'p',approvalId:'a',evidence:[{blockId:'b1',quote:'原文证据'}],fromSceneId:'s',uncertainty:'作者纠错仅供核对',stale:false,
        view:{content:'林青相信戏票作废',kind:'BELIEF',certainty:'BELIEVED',eventActor:null,acquisitionBasis:'仅有猜测'}
      }]
    }}]);
    mount();fireEvent.click(await screen.findByText('查看配置历史'));
    expect(await screen.findByText('当时的人物可用表述：林青相信戏票作废 · BELIEF / BELIEVED')).toBeTruthy();
    expect(screen.getByText('当时的事件实施者：未提供')).toBeTruthy();
    expect(screen.getByText('当时的获知依据：仅有猜测')).toBeTruthy();
  });
  it('preserves an unsaved plan on 409 and refresh, without overwriting its version baseline',async()=>{
    vi.mocked(api.narrativeContext.update).mockRejectedValue(new ApiError(409,'NARRATIVE_VERSION_CHANGED','req-409'));
    const page=mount();
    fireEvent.click(await screen.findByText('补充作者条目'));
    fireEvent.change(screen.getByLabelText('条目内容'),{target:{value:'林青准备开门，还没有进去。'}});
    fireEvent.click(screen.getByText('保存隔离配置'));
    await screen.findByRole('alert');
    expect(screen.getByRole('alert').textContent).toContain('req-409');
    const first=vi.mocked(api.narrativeContext.update).mock.calls[0];
    expect(first[2].expectedManuscriptVersion).toBe(1);
    page.unmount();
    vi.mocked(api.narrativeContext.state).mockResolvedValue({...base,manuscriptVersion:2});
    mount();
    expect((await screen.findByLabelText('条目内容') as HTMLTextAreaElement).value).toContain('准备开门');
    fireEvent.click(screen.getByText('保存隔离配置'));
    await waitFor(()=>expect(api.narrativeContext.update).toHaveBeenCalledTimes(2));
    expect(vi.mocked(api.narrativeContext.update).mock.calls[1][3]).toBe(first[3]);
    expect(vi.mocked(api.narrativeContext.update).mock.calls[1][2].expectedManuscriptVersion).toBe(1);
  });
  it('saves the opt-in and viewpoint before requesting a scoped preview',async()=>{
    vi.mocked(api.narrativeContext.update).mockImplementation(async(_m,_b,v)=>({...base,enabled:v.enabled,revision:1,settingsRevision:1,document:v.document}));
    mount();
    fireEvent.click(await screen.findByLabelText('启用本作品的上下文隔离'));
    fireEvent.change(screen.getByLabelText('当前场景的视角人物'),{target:{value:'p'}});
    fireEvent.click(screen.getByText('保存隔离配置'));
    await waitFor(()=>expect(api.narrativeContext.update).toHaveBeenCalledTimes(1));
    const request=vi.mocked(api.narrativeContext.update).mock.calls[0][2];
    expect(request.enabled).toBe(true);expect(request.document.policy.viewpointByScene.s).toBe('p');
    fireEvent.change(screen.getByLabelText('预览视图'),{target:{value:'CHARACTER'}});
    fireEvent.change(screen.getByLabelText('查询人物'),{target:{value:'p'}});
    fireEvent.click(screen.getByText('预览实际上下文'));
    await waitFor(()=>expect(api.narrativeContext.preview).toHaveBeenCalledWith('m','b','s','CHARACTER','p'));
  });
});
