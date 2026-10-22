import type { WorldDetail, WorldModuleDefinition } from "@/types";

const metadataKeys = ["name", "tagline", "themes", "creativeIntent", "notes"] as const;
const same = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right);
const fields = (world: WorldDetail) => Object.fromEntries(world.modules.map(module => [module.key, module.fields]));
const orderedFields = (world: WorldDetail) => Object.fromEntries(Object.entries(fields(world)).sort(([a], [b]) => a.localeCompare(b)).map(([key, values]) => [key, Object.fromEntries(Object.entries(values).sort(([a], [b]) => a.localeCompare(b)))]));

export function worldContent(world: WorldDetail) {
  return JSON.stringify({ ...Object.fromEntries(metadataKeys.map(key => [key, world[key]])), modules: orderedFields(world) });
}

/** Called only after the author reviews the current server copy and chooses to keep local edits. */
export function mergeLocalWorldChanges(base: WorldDetail, local: WorldDetail, server: WorldDetail): WorldDetail {
  const metadata = Object.fromEntries(metadataKeys.map(key => [key, same(base[key], local[key]) ? server[key] : local[key]]));
  const original = fields(base), changed = fields(local), merged = structuredClone(fields(server));
  for (const [module, values] of Object.entries(changed)) {
    for (const [field, value] of Object.entries(values)) {
      if (value !== (original[module]?.[field] ?? "")) {
        merged[module] ??= {};
        merged[module][field] = value;
      }
    }
  }
  return { ...server, ...metadata, modules: Object.entries(merged).map(([key, values]) => ({ key, fields: values })) };
}

export function worldCoverage(world: WorldDetail, definitions: WorldModuleDefinition[]) {
  const values = fields(world);
  const total = definitions.reduce((count, module) => count + module.fields.length, 0);
  const filled = definitions.reduce((count, module) => count + module.fields.filter(field => values[module.key]?.[field.key]?.trim()).length, 0);
  return { filled, total, percent: total ? Math.round(filled / total * 100) : null };
}
