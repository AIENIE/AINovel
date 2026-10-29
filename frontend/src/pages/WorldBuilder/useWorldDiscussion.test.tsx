import {act,cleanup,renderHook,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {afterEach,expect,it,vi} from 'vitest';
import type {ReactNode} from 'react';
import {api} from '@/lib/api-client';
import {useWorldDiscussion,type WorldEditorState} from './useWorldDiscussion';

vi.mock('@/lib/ai-operation-store',()=>({runTrackedAiOperation:vi.fn(async()=>{}),retryTrackedAiOperation:vi.fn()}));
afterEach(()=>{cleanup();sessionStorage.clear();vi.restoreAllMocks();});

it('clears only the submitted discussion text and preserves typing received while acceptance is pending',async()=>{
  vi.spyOn(api.worldAssistant,'sessions').mockResolvedValue([]);
  vi.spyOn(api.worldAssistant,'session').mockResolvedValue({id:'topic',title:'话题',worldId:'world',turns:[],hasMore:false,nextBefore:null});
  let resolve!:(value:Awaited<ReturnType<typeof api.worldAssistant.submit>>)=>void;
  const submit=vi.spyOn(api.worldAssistant,'submit').mockImplementation(()=>new Promise(yes=>{resolve=yes;}));
  const editor={draft:{revision:4},canAct:()=>true} as unknown as WorldEditorState;
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const wrapper=({children}:{children:ReactNode})=><QueryClientProvider client={client}>{children}</QueryClientProvider>;
  const {result}=renderHook(()=>useWorldDiscussion('world',editor),{wrapper});
  await waitFor(()=>expect(result.current.selected).toBe('topic'));
  act(()=>result.current.setInput('第一条讨论'));
  let pending:ReturnType<typeof result.current.submit>;
  act(()=>{pending=result.current.submit();});await waitFor(()=>expect(submit).toHaveBeenCalledOnce());
  act(()=>result.current.setInput('下一条尚未提交的想法'));
  await act(async()=>{resolve({} as Awaited<ReturnType<typeof api.worldAssistant.submit>>);await pending;});
  expect(result.current.input).toBe('下一条尚未提交的想法');
  act(()=>{pending=result.current.submit();});await waitFor(()=>expect(submit).toHaveBeenCalledTimes(2));
  await act(async()=>{resolve({} as Awaited<ReturnType<typeof api.worldAssistant.submit>>);await pending;});
  expect(result.current.input).toBe('');
});
