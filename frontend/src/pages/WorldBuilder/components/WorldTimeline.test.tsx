import {useState} from 'react';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,describe,it,expect,vi} from 'vitest';
import type {WorldDetail,WorldDateRange} from '@/types';
import {WorldDateEditor,WorldTimeline,WorldTimelineSnapshot} from './WorldTimeline';

afterEach(()=>{cleanup();vi.restoreAllMocks();});
describe('timeline editor',()=>{
  it('keeps the same event selected when editing its date or author order reorders the directory',()=>{
    const event=(id:string,year:number,sortOrder:number)=>({id,title:id,summary:'',content:'',aliases:[],tags:[],date:{start:{year},end:{year}},entryIds:[],eraIds:[],sortOrder});
    const draft={id:'w',entries:[],timeline:{schemaVersion:1,calendarName:'',eras:[],events:[event('甲事件',1,0),event('乙事件',2,1)]}} as unknown as WorldDetail;
    function Harness(){const[value,setValue]=useState(draft);return <WorldTimeline draft={value} edit={update=>setValue(update)}/>;}
    render(<Harness/>);fireEvent.change(screen.getByLabelText('日期 开始 年'),{target:{value:'3'}});
    expect((screen.getByLabelText('标题') as HTMLInputElement).value).toBe('甲事件');
    fireEvent.change(screen.getByLabelText('显示顺序'),{target:{value:'author'}});fireEvent.click(screen.getByRole('button',{name:'下移'}));
    expect((screen.getByLabelText('标题') as HTMLInputElement).value).toBe('甲事件');
    fireEvent.change(screen.getByLabelText('正文'),{target:{value:'仍属于甲事件'}});
    expect((screen.getByLabelText('正文') as HTMLTextAreaElement).value).toBe('仍属于甲事件');
  });
  it('keeps fractional input visible and explains that dates require integers',()=>{
    const changed=vi.fn();function Harness(){const[value,setValue]=useState<WorldDateRange|null>({start:{year:1},end:{year:1}});return <WorldDateEditor value={value} onChange={next=>{setValue(next);changed(next);}}/>;}
    render(<Harness/>);fireEvent.change(screen.getByLabelText('日期 开始 年'),{target:{value:'1.5'}});
    expect((screen.getByLabelText('日期 开始 年') as HTMLInputElement).value).toBe('1.5');expect(changed).toHaveBeenLastCalledWith({start:{year:1.5},end:{year:1.5}});expect(screen.getByRole('alert').textContent).toContain('整数年份');
  });
  it('separates unknown starts in date view for eras and events, but preserves author order without grouping',()=>{
    const dated={start:{year:0},end:{year:0}};
    const draft={id:'w',entries:[],timeline:{schemaVersion:1,calendarName:'潮历',eras:[{id:'eu',name:'失落纪',description:'',date:{start:null,end:{year:3}},sortOrder:0},{id:'ek',name:'创世纪',description:'',date:dated,sortOrder:1}],events:[{id:'unknown',title:'未知事件',summary:'',content:'',aliases:[],tags:[],date:null,entryIds:[],eraIds:[],sortOrder:0},{id:'known',title:'已知事件',summary:'',content:'',aliases:[],tags:[],date:dated,entryIds:[],eraIds:[],sortOrder:1}]}} as unknown as WorldDetail;
    render(<WorldTimeline draft={draft} edit={()=>{}}/>);expect(screen.getAllByRole('heading',{name:'起点未知'})).toHaveLength(2);
    fireEvent.change(screen.getByLabelText('显示顺序'),{target:{value:'author'}});expect(screen.queryByRole('heading',{name:'起点未知'})).toBeNull();
  });
  it('updates the date form for external replacements while preserving an author-selected empty range',()=>{
    const changed=vi.fn();const view=render(<WorldDateEditor value={null} onChange={changed}/>);
    view.rerender(<WorldDateEditor value={{start:{year:4},end:{year:4}}} onChange={changed}/>);expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('single');expect((screen.getByLabelText('日期 开始 年') as HTMLInputElement).value).toBe('4');
    view.rerender(<WorldDateEditor value={{start:{year:4},end:{year:9,month:2}}} onChange={changed}/>);expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('range');expect((screen.getByLabelText('日期 结束 年') as HTMLInputElement).value).toBe('9');view.unmount();
    function Harness(){const[value,setValue]=useState<WorldDateRange|null>(null);return <WorldDateEditor value={value} onChange={setValue}/>;}
    render(<Harness/>);fireEvent.change(screen.getByRole('combobox'),{target:{value:'range'}});expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('range');expect(screen.getAllByRole('checkbox')).toHaveLength(2);
  });
  it('leaves unknown dates null and lets the author enter year zero or an open range',()=>{
    const changed=vi.fn();function Harness(){const[value,setValue]=useState<WorldDateRange|null>(null);return <WorldDateEditor value={value} onChange={next=>{setValue(next);changed(next);}}/>;}
    render(<Harness/>);expect(screen.queryByRole('spinbutton')).toBeNull();
    fireEvent.change(screen.getByRole('combobox'),{target:{value:'single'}});
    expect(changed).toHaveBeenLastCalledWith({start:{year:null},end:{year:null}});
    fireEvent.change(screen.getByLabelText('日期 开始 年'),{target:{value:'0'}});
    expect(changed).toHaveBeenLastCalledWith({start:{year:0},end:{year:0}});
    fireEvent.change(screen.getByRole('combobox'),{target:{value:'range'}});
    fireEvent.click(screen.getAllByRole('checkbox')[1]);
    expect(changed).toHaveBeenLastCalledWith({start:{year:0},end:null});
    fireEvent.change(screen.getByRole('combobox'),{target:{value:'unknown'}});
    expect(changed).toHaveBeenLastCalledWith(null);
  });
  it('deletes an era only after describing dependent events, preserving the event and entry references',()=>{
    const era={id:'era',name:'迁徙纪',description:'迁徙',date:null,sortOrder:0};
    const event={id:'event',title:'启航',summary:'',content:'船队启航',aliases:[],tags:[],date:null,eraIds:['era'],entryIds:['port'],sortOrder:0};
    const draft={id:'w',entries:[{id:'port',title:'港口'}],timeline:{schemaVersion:1,calendarName:'潮历',eras:[era],events:[event]}} as unknown as WorldDetail;
    let result=draft;const confirm=vi.spyOn(window,'confirm').mockReturnValue(true);
    function Harness(){const[value,setValue]=useState(draft);return <WorldTimeline draft={value} edit={update=>setValue(old=>{result=update(old);return result;})}/>;}
    render(<Harness/>);fireEvent.click(screen.getByText('迁徙纪 · 日期未知'));fireEvent.click(screen.getByRole('button',{name:'删除时代'}));
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('启航'));
    expect(result.timeline?.eras).toEqual([]);expect(result.timeline?.events[0]).toEqual({...event,eraIds:[]});expect(draft.timeline?.eras).toEqual([era]);
  });
  it('renders selected-era history from snapshot text without other eras or live data',()=>{
    const make=(id:string)=>({id,name:id,description:'',date:null,sortOrder:0});
    render(<WorldTimelineSnapshot timeline={{schemaVersion:1,calendarName:'潮历',eras:[make('古代'),make('近代')],events:[{id:'e',title:'旧港启航',summary:'摘要',content:'历史正文',aliases:['启航日'],tags:['航海'],date:{start:{year:-2},end:null},eraIds:['古代'],entryIds:['p'],sortOrder:0}]}} eraIds={['古代']} entryTitles={{p:'旧港'}}/>);
    expect(screen.getByText('历史正文')).toBeTruthy();expect(screen.getByText(/旧港启航/)).toBeTruthy();expect(screen.queryByText(/近代/)).toBeNull();expect(screen.getByText(/关联条目 · 旧港/)).toBeTruthy();
  });
});
