import type { WorldDetail, WorldModuleDefinition, WorldEntryType } from "@/types";
import { emptyTimeline, canonicalTimeline } from './world-timeline';

const metadataKeys = ["name", "tagline", "themes", "creativeIntent", "notes", "overview"] as const;
const same = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right);
const fields = (world: WorldDetail) => Object.fromEntries(world.modules.map(module => [module.key, module.fields]));
const orderedFields = (world: WorldDetail) => Object.fromEntries(Object.entries(fields(world)).sort(([a], [b]) => a.localeCompare(b)).map(([key, values]) => [key, Object.fromEntries(Object.entries(values).sort(([a], [b]) => a.localeCompare(b)))]));

export function worldContent(world: WorldDetail) {
  return JSON.stringify({ ...Object.fromEntries(metadataKeys.map(key => [key, world[key]])),
    timeline: canonicalTimeline(world.timeline),
    ...(world.entries ? {entries: ordered(world.entries), relations: ordered(world.relations || [])} : {modules: orderedFields(world)}) });
}
const ordered = <T extends {id: string}>(rows: T[]) => [...rows].sort((a,b) => a.id.localeCompare(b.id));

function mergeRows<T extends {id: string}>(base: T[], local: T[], server: T[]): T[] {
  const prior = new Map(base.map(row => [row.id, row]));
  const next = new Map(server.map(row => [row.id, row]));
  const localIds = new Set(local.map(row => row.id));
  for (const original of base) if (!localIds.has(original.id)) next.delete(original.id);
  for (const row of local) {
    const before = prior.get(row.id), remote = next.get(row.id);
    if (same(before, row)) continue;
    if (!before || !remote) { next.set(row.id, row); continue; }
    const merged = { ...remote };
    for (const key of Object.keys(row) as (keyof T)[]) if (!same(before[key], row[key])) merged[key] = row[key];
    next.set(row.id, merged);
  }
  return ordered([...next.values()]);
}

/** Called only after the author reviews the current server copy and chooses to keep local edits. */
export function mergeLocalWorldChanges(base: WorldDetail, local: WorldDetail, server: WorldDetail): WorldDetail {
  const metadata = Object.fromEntries(metadataKeys.map(key => [key, same(base[key], local[key]) ? server[key] : local[key]]));
  const bt=base.timeline||emptyTimeline(),lt=local.timeline||emptyTimeline(),st=server.timeline||emptyTimeline();
  const eras=mergeRows(bt.eras,lt.eras,st.eras),eraIds=new Set(eras.map(era=>era.id));
  const timeline={schemaVersion:1,calendarName:bt.calendarName===lt.calendarName?st.calendarName:lt.calendarName,eras,
    events:mergeRows(bt.events,lt.events,st.events).map(event=>({...event,eraIds:event.eraIds.filter(id=>eraIds.has(id))}))};
  if (local.entries) {
    const entries = mergeRows(base.entries || [], local.entries, server.entries || []);
    const ids = new Set(entries.map(entry => entry.id));
    const relations = mergeRows(base.relations || [], local.relations || [], server.relations || []).filter(r => ids.has(r.sourceId) && ids.has(r.targetId));
    return {...server, ...metadata, entries, relations,timeline:{...timeline,events:timeline.events.map(event=>({...event,entryIds:event.entryIds.filter(id=>ids.has(id))}))}};
  }
  const original = fields(base), changed = fields(local), merged = structuredClone(fields(server));
  for (const [module, values] of Object.entries(changed)) {
    for (const [field, value] of Object.entries(values)) {
      if (value !== (original[module]?.[field] ?? "")) {
        merged[module] ??= {};
        merged[module][field] = value;
      }
    }
  }
  return { ...server, ...metadata,timeline, modules: Object.entries(merged).map(([key, values]) => ({ key, fields: values })) };
}

export function worldCoverage(world: WorldDetail, definitions: WorldModuleDefinition[]) {
  if (world.entries) {
    const total=world.entries.length, filled=world.entries.filter(e=>e.content?.trim()).length;
    return {filled,total,percent:total?Math.round(filled/total*100):null};
  }
  const values = fields(world);
  const total = definitions.reduce((count, module) => count + module.fields.length, 0);
  const filled = definitions.reduce((count, module) => count + module.fields.filter(field => values[module.key]?.[field.key]?.trim()).length, 0);
  return { filled, total, percent: total ? Math.round(filled / total * 100) : null };
}

export const WORLD_ENTRY_TYPES: WorldEntryType[] = ['GEOGRAPHY','LOCATION','PERSON','ORGANIZATION','SPECIES','SOCIETY','RESOURCE','SYSTEM','RULE','HISTORY','CUSTOM'];
