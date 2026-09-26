import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { KnowledgeViewEditor } from './KnowledgeViewEditor';
import { StableAttributeEditor } from './StableAttributeEditor';
afterEach(cleanup);
it('edits optional attribution without changing belief type or other reviewed fields',()=>{
  const change=vi.fn();
  const value={content:'相信票已作废',kind:'BELIEF' as const,certainty:'BELIEVED' as const,eventActor:'未明确',acquisitionBasis:'听闻，未经核实'};
  render(<KnowledgeViewEditor value={value} kind="BELIEF" onChange={change}/>);
  fireEvent.change(screen.getByLabelText('事件实施者'),{target:{value:''}});
  expect(change).toHaveBeenLastCalledWith({...value,eventActor:null});
  fireEvent.change(screen.getByLabelText('获知依据'),{target:{value:'自己的猜测'}});
  expect(change).toHaveBeenLastCalledWith({...value,acquisitionBasis:'自己的猜测'});
  expect(screen.getByText(/留空表示尚未提供/)).toBeTruthy();
});
it('requires explicit blank supplementation instead of copying author information',()=>{
  const change=vi.fn();render(<KnowledgeViewEditor kind="BELIEF" onChange={change}/>);
  expect(screen.getByText(/不会进入严格人物输入/)).toBeTruthy();
  fireEvent.click(screen.getByText('补充人物可用表述'));
  expect(change).toHaveBeenCalledWith({content:'',kind:'BELIEF',certainty:'BELIEVED'});
});
it('keeps stable attributes scoped to the selected character and current scene',()=>{
  const add=vi.fn();render(<StableAttributeEditor characters={[{id:'a',name:'林青'},{id:'b',name:'杜宁'}]} sceneId="s2" onAdd={add}/>);
  fireEvent.change(screen.getByLabelText('属性所属人物'),{target:{value:'a'}});
  fireEvent.change(screen.getByLabelText('代词'),{target:{value:'她'}});
  fireEvent.click(screen.getByText('将属性加入配置草稿'));
  expect(add).toHaveBeenCalledWith(expect.objectContaining({kind:'BACKGROUND',fromSceneId:'s2',characterIds:['a'],narratorVisible:false}));
  expect(add.mock.calls[0][0].text).toContain('代词：她');
  expect(add.mock.calls[0][0].text).not.toContain('杜宁');
});
