export type MaterialTextChange = { kind: "equal" | "removed" | "added"; text: string; start: number; end: number };

/** Conservative exact character ranges. A changed middle may contain unchanged fragments. */
export function materialTextDiff(before: string, after: string): MaterialTextChange[] {
  const a = Array.from(before), b = Array.from(after);
  let prefix = 0, suffix = 0;
  while (prefix < a.length && prefix < b.length && a[prefix] === b[prefix]) prefix += 1;
  while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - suffix - 1] === b[b.length - suffix - 1]) suffix += 1;
  const changes: MaterialTextChange[] = [];
  const add = (kind: MaterialTextChange["kind"], chars: string[], start: number, end: number) => { if (end > start) changes.push({ kind, text: chars.slice(start, end).join(""), start, end }); };
  add("equal", a, 0, prefix); add("removed", a, prefix, a.length - suffix); add("added", b, prefix, b.length - suffix); add("equal", b, b.length - suffix, b.length);
  return changes;
}
