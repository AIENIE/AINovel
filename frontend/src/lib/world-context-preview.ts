import type { WorldPreview } from './api/domains/world-context';
type Pending = { path:string; request:object; preview:WorldPreview; resolve:(value:WorldPreview)=>void; reject:(error:Error)=>void };
let queue: Pending[] = [];
const listeners = new Set<()=>void>();
const notify=()=>listeners.forEach(fn=>fn());
export const subscribeWorldPreview=(fn:()=>void)=>{listeners.add(fn);return ()=>{listeners.delete(fn);};};
export const pendingWorldPreview=()=>queue[0]??null;
export function reviewWorldPreview(path:string, request:object, preview:WorldPreview) {
  return new Promise<WorldPreview>((resolve,reject)=>{queue=[...queue,{path,request,preview,resolve,reject}];notify();});
}
export function finishWorldPreview(pending:Pending,value?:WorldPreview) {
  queue=queue.filter(item=>item!==pending);if(value)pending.resolve(value);else pending.reject(new Error('WORLD_CONTEXT_CANCELLED'));notify();
}
