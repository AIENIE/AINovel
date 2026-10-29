import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,describe,it,expect,vi} from 'vitest';
import {MemoryRouter} from 'react-router-dom';
import {api,ApiError} from '@/lib/api-client';
import type {WorldDetail,WorldBindingView,WorldVersion} from '@/types';
import {WorldPublishDialog,WorldVersionHistory} from './WorldVersionViews';
import {WorldBindingPanel} from './WorldBindingPanel';

afterEach(()=>{cleanup();vi.restoreAllMocks();});
describe('world publication and binding review',()=>{
  it('shows a failed historical snapshot read inside its dialog and retries that exact version',async()=>{
    vi.spyOn(api.worlds,'versions').mockResolvedValue([{id:'v1',sequenceNumber:1,origin:'PUBLISHED',createdAt:'2026-10-25'}] as WorldVersion[]);
    const read=vi.spyOn(api.worlds,'version').mockRejectedValueOnce(new Error('snapshot offline')).mockResolvedValue({id:'v1',sequenceNumber:1,content:{name:'历史世界',tagline:'',themes:[],creativeIntent:'',notes:'',entries:[],relations:[]}} as unknown as WorldVersion);
    render(<WorldVersionHistory worldId="world" revision={4}/>);fireEvent.click(await screen.findByRole('button',{name:/第 1 版/}));
    const dialog=screen.getByRole('dialog');await waitFor(()=>expect(dialog.querySelector('[role="alert"]')?.textContent).toContain('操作失败'));
    fireEvent.click(screen.getByRole('button',{name:'重试'}));await screen.findByText('历史世界');
    expect(read.mock.calls).toEqual([['world','v1'],['world','v1']]);expect(screen.queryByRole('alert')).toBeNull();
  });
  it('publishes an incomplete saved draft without generation and retries the same receipt',async()=>{
    const draft={id:'world',name:'群岛',revision:4,tagline:'',creativeIntent:'',themes:[],entries:[],relations:[],modules:[]} as unknown as WorldDetail;
    vi.spyOn(api.worlds,'diff').mockResolvedValue({fromVersionId:null,toVersionId:null,draftRevision:4,changes:[]});
    const publish=vi.spyOn(api.worlds,'publish').mockRejectedValueOnce(new Error('offline')).mockResolvedValue({id:'v1'} as WorldVersion);
    const generate=vi.spyOn(api.worlds,'startGenerateEntry');const close=vi.fn();
    render(<WorldPublishDialog draft={draft} stale={false} onClose={close} onPublished={async()=>{}}/>);
    const confirm=await screen.findByRole('button',{name:'确认发布'});
    await waitFor(()=>expect(confirm.hasAttribute('disabled')).toBe(false));
    fireEvent.change(screen.getByRole('textbox'),{target:{value:'首版'}});fireEvent.click(confirm);
    await screen.findByRole('alert');expect(close).not.toHaveBeenCalled();
    expect(screen.getByRole('textbox').hasAttribute('disabled')).toBe(true);
    fireEvent.click(confirm);await waitFor(()=>expect(close).toHaveBeenCalled());
    expect(publish).toHaveBeenCalledTimes(2);expect(publish.mock.calls[0]).toEqual(publish.mock.calls[1]);
    expect(publish.mock.calls[0]).toEqual(['world',4,'首版',expect.any(String)]);expect(generate).not.toHaveBeenCalled();
  });
  it('requires a fresh comparison after a concurrent binding change',async()=>{
    const view:WorldBindingView={resourceType:'STORY',resourceId:'story',bindingRevision:3,explicitVersionId:'v1',explicit:true,overrides:[],effective:{worldId:'world',versionId:'v1',name:'群岛',sequenceNumber:1,origin:'PUBLISHED',sourceType:'STORY',sourceId:'story',bindingRevision:3,contentHash:'hash',unavailableReason:null}};
    vi.spyOn(api.worldBindings,'get').mockResolvedValue(view);vi.spyOn(api.worlds,'list').mockResolvedValue([{id:'world',name:'群岛'}] as never);
    vi.spyOn(api.worlds,'versions').mockResolvedValue([{id:'v2',sequenceNumber:2,origin:'PUBLISHED'},{id:'v1',sequenceNumber:1,origin:'PUBLISHED'}] as WorldVersion[]);
    const diff=vi.spyOn(api.worldBindings,'diff').mockResolvedValue({fromVersionId:'v1',toVersionId:'v2',draftRevision:null,changes:[{kind:'ENTRY',id:'e',label:'潮汐',before:{content:'十七年'},after:{content:'十九年'}}]});
    const change=vi.spyOn(api.worldBindings,'change').mockRejectedValueOnce(new ApiError(409,'WORLD_BINDING_CONFLICT')).mockResolvedValue(view);
    render(<MemoryRouter><WorldBindingPanel scope="STORY" resourceId="story"/></MemoryRouter>);
    const review=await screen.findByRole('button',{name:'查看版本差异'});
    await waitFor(()=>expect(review.hasAttribute('disabled')).toBe(false));expect(change).not.toHaveBeenCalled();
    fireEvent.click(review);fireEvent.click(await screen.findByRole('button',{name:'确认采用此版本'}));
    await waitFor(()=>expect(screen.getByRole('button',{name:'查看版本差异'}).hasAttribute('disabled')).toBe(true));
    expect(change).toHaveBeenCalledWith('STORY','story',{fromVersionId:'v1',toVersionId:'v2',expectedBindingRevision:3});
    fireEvent.click(screen.getByRole('button',{name:'刷新后重新比较'}));
    await waitFor(()=>expect(screen.getByRole('button',{name:'查看版本差异'}).hasAttribute('disabled')).toBe(false));
    expect(screen.queryByRole('button',{name:'确认采用此版本'})).toBeNull();expect(change).toHaveBeenCalledTimes(1);expect(diff).toHaveBeenCalledTimes(1);
  });
});
