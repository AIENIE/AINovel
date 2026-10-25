import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest';
import {MemoryRouter} from 'react-router-dom';
import {api,ApiError} from '@/lib/api-client';
import type {StoryWorldEras as EraView,WorldBindingReference,WorldDetail,WorldVersion} from '@/types';
import type {WorldEditorState} from '../useWorldDiscussion';
import {WorldManageDialog} from './WorldManageDialog';
import {StoryWorldEras} from './StoryWorldEras';

const draft={id:'w',name:'群岛',revision:5,entries:[],relations:[],modules:[]} as unknown as WorldDetail;
const eras=[{id:'e1',name:'迁徙纪',description:'',date:null,sortOrder:0},{id:'e2',name:'回航纪',description:'',date:null,sortOrder:1}];
const binding:WorldBindingReference={worldId:'w',versionId:'v2',sourceType:'STORY',sourceId:'s',bindingRevision:2,name:'群岛',sequenceNumber:2,origin:'PUBLISHED',contentHash:'hash',unavailableReason:null};
const selection:EraView={worldId:'w',worldVersionId:'v2',bindingRevision:2,revision:3,reviewedVersionId:'v1',eraIds:['e1'],eras,changedEraIds:['e1'],missingEraIds:[]};
beforeEach(()=>{vi.spyOn(api.worlds,'getDetail').mockResolvedValue(draft);vi.spyOn(api.worlds,'versions').mockResolvedValue([{id:'v2',sequenceNumber:2,origin:'PUBLISHED'}] as WorldVersion[]);});
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();});
describe('world copy and export',()=>{
  it('uses the selected historical name and excludes migration baselines from source choices',async()=>{
    vi.mocked(api.worlds.versions).mockResolvedValue([{id:'v2',sequenceNumber:2,origin:'PUBLISHED'},{id:'baseline',sequenceNumber:0,origin:'MIGRATION_BASELINE'}] as WorldVersion[]);
    vi.spyOn(api.worlds,'version').mockResolvedValue({content:{name:'旧称海国'}} as WorldVersion);const copy=vi.spyOn(api.worlds,'copy').mockResolvedValue({...draft,id:'copy'});
    render(<MemoryRouter><WorldManageDialog worldId="w" onClose={()=>{}}/></MemoryRouter>);await screen.findByRole('button',{name:'复制为新世界'});expect(screen.queryByRole('option',{name:/迁移基线/})).toBeNull();
    fireEvent.change(screen.getByRole('combobox'),{target:{value:'v2'}});await waitFor(()=>expect((screen.getByLabelText('副本名称') as HTMLInputElement).value).toBe('旧称海国（副本）'));
    fireEvent.click(screen.getByRole('button',{name:'复制为新世界'}));await waitFor(()=>expect(copy).toHaveBeenCalledWith('w',expect.objectContaining({source:'VERSION',versionId:'v2',name:'旧称海国（副本）'})));
  });
  it('offers a fresh source after a definite revision conflict and starts a new receipt only after review',async()=>{
    const copy=vi.spyOn(api.worlds,'copy').mockRejectedValueOnce(new ApiError(409,'WORLD_VERSION_CONFLICT')).mockResolvedValue({...draft,id:'copy'});
    render(<MemoryRouter><WorldManageDialog worldId="w" onClose={()=>{}}/></MemoryRouter>);fireEvent.click(await screen.findByRole('button',{name:'复制为新世界'}));await screen.findByRole('alert');expect(screen.getByRole('button',{name:'复制为新世界'}).hasAttribute('disabled')).toBe(true);
    vi.mocked(api.worlds.getDetail).mockResolvedValue({...draft,revision:6});fireEvent.click(screen.getByRole('button',{name:'重新读取并选择来源'}));await screen.findByRole('option',{name:/r6/});fireEvent.click(screen.getByRole('button',{name:'复制为新世界'}));await waitFor(()=>expect(copy).toHaveBeenCalledTimes(2));expect(copy.mock.calls[1][1].expectedRevision).toBe(6);expect(copy.mock.calls[1][1].requestKey).not.toBe(copy.mock.calls[0][1].requestKey);
  });
  it('retries a copy with the same source revision and receipt, without another copy on repeated clicks',async()=>{
    const copy=vi.spyOn(api.worlds,'copy').mockRejectedValueOnce(new Error('offline')).mockResolvedValue({...draft,id:'copy'});
    render(<MemoryRouter><WorldManageDialog worldId="w" onClose={()=>{}}/></MemoryRouter>);
    const button=await screen.findByRole('button',{name:'复制为新世界'});fireEvent.click(button);await screen.findByRole('alert');fireEvent.click(button);
    await screen.findByRole('link',{name:'打开副本'});expect(copy).toHaveBeenCalledTimes(2);expect(copy.mock.calls[0]).toEqual(copy.mock.calls[1]);expect(copy.mock.calls[0][1]).toMatchObject({source:'DRAFT',expectedRevision:5,name:'群岛（副本）'});expect(button.hasAttribute('disabled')).toBe(true);
  });
  it('blocks management while there is unsaved input and does not auto-save or export it',async()=>{
    const save=vi.fn(async()=>false),exportWorld=vi.spyOn(api.worlds,'export');const editor={draft,canAct:()=>false,save,dirty:true,saving:false,remote:null,conflict:false} as unknown as WorldEditorState;
    render(<MemoryRouter><WorldManageDialog worldId="w" editor={editor} onClose={()=>{}}/></MemoryRouter>);
    const button=await screen.findByRole('button',{name:'导出 JSON'});expect(button.hasAttribute('disabled')).toBe(true);fireEvent.click(button);expect(exportWorld).not.toHaveBeenCalled();expect(save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button',{name:'保存草稿后继续'}));await waitFor(()=>expect(save).toHaveBeenCalledOnce());expect(exportWorld).not.toHaveBeenCalled();
  });
  it('downloads the selected immutable version, not the currently saved draft',async()=>{
    vi.spyOn(api.worlds,'version').mockResolvedValue({content:{name:'旧群岛'}} as WorldVersion);
    const blob=new Blob(['{"name":"群岛"}'],{type:'application/json'}),exportWorld=vi.spyOn(api.worlds,'export').mockResolvedValue({blob,fileName:'群岛-v2.json'});
    vi.stubGlobal('URL',Object.assign(URL,{createObjectURL:vi.fn(()=> 'blob:world'),revokeObjectURL:vi.fn()}));const click=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});
    render(<MemoryRouter><WorldManageDialog worldId="w" onClose={()=>{}}/></MemoryRouter>);await screen.findByRole('button',{name:'导出 JSON'});fireEvent.change(screen.getByRole('combobox'),{target:{value:'v2'}});await waitFor(()=>expect(screen.getByRole('button',{name:'导出 JSON'}).hasAttribute('disabled')).toBe(false));fireEvent.click(screen.getByRole('button',{name:'导出 JSON'}));
    await waitFor(()=>expect(click).toHaveBeenCalledOnce());expect(exportWorld).toHaveBeenCalledWith('w',{source:'VERSION',versionId:'v2',format:'JSON'});expect((click.mock.instances[0] as HTMLAnchorElement).download).toBe('群岛-v2.json');
  });
});
describe('story era labels',()=>{
  beforeEach(()=>{vi.spyOn(api.worldBindings,'eras').mockResolvedValue(selection);vi.spyOn(api.worlds,'version').mockResolvedValue({content:{entries:[],timeline:{schemaVersion:1,calendarName:'',eras,events:[]}}} as unknown as WorldVersion);});
  it.each([{worldVersionId:'v3',bindingRevision:3},{worldVersionId:'v2',bindingRevision:3}])('requires the whole binding panel to refresh when the label read has a different binding baseline: %j',async newer=>{
    vi.mocked(api.worldBindings.eras).mockResolvedValue({...selection,...newer});const save=vi.spyOn(api.worldBindings,'saveEras'),refresh=vi.fn();
    render(<StoryWorldEras storyId="s" effective={binding} editable onRefreshBinding={refresh}/>);
    const button=await screen.findByRole('button',{name:'刷新世界绑定'});expect(screen.getByRole('alert').textContent).toContain('世界绑定已变化');
    expect(screen.queryByRole('button',{name:'保存时代标注'})).toBeNull();expect(screen.queryByRole('checkbox',{name:/迁徙纪/})).toBeNull();fireEvent.click(button);expect(refresh).toHaveBeenCalledOnce();expect(save).not.toHaveBeenCalled();
  });
  it('preserves an additional era selected while saving an earlier selection',async()=>{
    let resolve!:(view:EraView)=>void;const save=vi.spyOn(api.worldBindings,'saveEras').mockImplementation(()=>new Promise(yes=>{resolve=yes;}));
    render(<StoryWorldEras storyId="s" effective={binding} editable/>);fireEvent.click(await screen.findByRole('button',{name:'保存时代标注'}));fireEvent.click(screen.getByLabelText('回航纪 · 日期未知'));
    await act(async()=>resolve({...selection,revision:4,changedEraIds:[],reviewedVersionId:'v2'}));expect((screen.getByLabelText('回航纪 · 日期未知') as HTMLInputElement).checked).toBe(true);expect(screen.getByText(/未保存/)).toBeTruthy();expect(save).toHaveBeenCalledWith('s',{worldId:'w',worldVersionId:'v2',expectedBindingRevision:2,expectedRevision:3,eraIds:['e1']});
  });
  it('ignores a late save when the same component switches to another story',async()=>{
    let resolve!:(view:EraView)=>void;vi.spyOn(api.worldBindings,'saveEras').mockImplementation(()=>new Promise(yes=>{resolve=yes;}));
    const view=render(<StoryWorldEras storyId="s" effective={binding} editable/>);fireEvent.click(await screen.findByRole('button',{name:'保存时代标注'}));
    vi.mocked(api.worldBindings.eras).mockResolvedValue({...selection,eraIds:['e2'],revision:8});view.rerender(<StoryWorldEras storyId="other-story" effective={binding} editable/>);
    await waitFor(()=>expect((screen.getByLabelText('回航纪 · 日期未知') as HTMLInputElement).checked).toBe(true));await act(async()=>resolve({...selection,revision:4}));
    expect((screen.getByLabelText('回航纪 · 日期未知') as HTMLInputElement).checked).toBe(true);expect((screen.getByLabelText('迁徙纪 · 日期未知') as HTMLInputElement).checked).toBe(false);expect(screen.queryByText('时代标注已保存')).toBeNull();
  });
  it('retains a missing label by its reviewed name until the author deselects it',async()=>{
    vi.mocked(api.worldBindings.eras).mockResolvedValue({...selection,eras:[],reviewedEras:eras,missingEraIds:['e1']});render(<StoryWorldEras storyId="s" effective={binding} editable/>);
    const save=await screen.findByRole('button',{name:'保存时代标注'});expect(save.hasAttribute('disabled')).toBe(true);const checkbox=screen.getByRole('checkbox',{name:/迁徙纪/});expect((checkbox as HTMLInputElement).checked).toBe(true);fireEvent.click(checkbox);expect(save.hasAttribute('disabled')).toBe(false);
  });
  it('shows another-world overrides as inapplicable and checks missing eras against the actual older version',async()=>{
    const mounted=render(<StoryWorldEras storyId="s" effective={{...binding,worldId:'other'}} editable={false}/>);await screen.findByText(/时代标注不适用/);expect(screen.queryByRole('button',{name:'保存时代标注'})).toBeNull();mounted.unmount();
    vi.mocked(api.worlds.version).mockResolvedValue({content:{entries:[],timeline:{schemaVersion:1,calendarName:'',eras:[],events:[]}}} as unknown as WorldVersion);render(<StoryWorldEras storyId="s" effective={{...binding,versionId:'v0'}} editable={false}/>);await screen.findByText('此时代不在当前实际绑定版本中');expect(api.worlds.version).toHaveBeenLastCalledWith('w','v0');
  });
});
