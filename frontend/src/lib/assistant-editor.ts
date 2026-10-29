import type { Editor } from "@tiptap/core";
import type { Node as ProseMirrorNode } from "@tiptap/pm/model";
import type { AssistantSelection } from "./api/domains/writing-assistant";

export type AssistantEditorTarget = { html: string; selection: AssistantSelection | null; text: string; supported: boolean };
export class AssistantInteractionError extends Error {}
function editableParagraph(node: ProseMirrorNode) {
  if (node.type.name !== "paragraph") return false;
  let supported = true;
  node.descendants(child => { if (!child.isText && child.type.name !== "hardBreak") supported = false; });
  return supported;
}
/** The wire offsets count UTF-16 text units, not ProseMirror node boundaries. */
export function captureAssistantTarget(editor: Editor, insertion = false): AssistantEditorTarget {
  const doc = editor.state.doc;
  const from = insertion ? editor.state.selection.to : editor.state.selection.from;
  const to = editor.state.selection.to;
  if (!insertion && from === 0 && to === doc.content.size) {
    let supported = false;
    doc.forEach(node => { if (editableParagraph(node)) supported = true; });
    return { html: editor.getHTML(), selection: null, text: doc.textBetween(0, doc.content.size, "\n", "\n"), supported };
  }
  const point = (pos: number, end: boolean) => {
    let found = { block: 0, offset: 0 };
    doc.forEach((node, offset, index) => {
      const start = offset + 1, last = offset + node.nodeSize - 1;
      if (pos >= start && pos <= last) found = { block: index, offset: node.textBetween(0, Math.max(0, Math.min(node.content.size, pos - start)), "", "\n").length };
      else if (pos === offset && !end) found = { block: index, offset: 0 };
      else if (pos === doc.content.size && index === doc.childCount - 1) found = { block: index, offset: node.textBetween(0, node.content.size, "", "\n").length };
    });
    return found;
  };
  const start = point(from, false), end = point(to, true);
  const selection = { startBlockIndex: start.block, endBlockIndex: end.block, startOffset: start.offset, endOffset: end.offset };
  let supported = false;
  doc.forEach((node, _offset, index) => { if (index >= start.block && index <= end.block && editableParagraph(node)) supported = true; });
  // A partial boundary inside a fixed block cannot be faithfully projected.
  if (!editableParagraph(doc.child(start.block)) || !editableParagraph(doc.child(end.block))) supported = false;
  const splitsSurrogate = ({ block, offset }: { block: number; offset: number }) => {
    const text = doc.child(block).textBetween(0, doc.child(block).content.size, "", "\n");
    return offset > 0 && /[\uD800-\uDBFF]/.test(text[offset - 1]) && /[\uDC00-\uDFFF]/.test(text[offset] || "");
  };
  if (splitsSurrogate(start) || splitsSurrogate(end)) supported = false;
  return { html: editor.getHTML(), selection, text: doc.textBetween(from, to, "\n", "\n"), supported };
}

export function assistantPlainText(html: string): string {
  const root = new DOMParser().parseFromString(html, "text/html").body;
  root.querySelectorAll("br").forEach(node => node.replaceWith("\n"));
  return Array.from(root.childNodes).map(node => node.textContent || "").join("\n\n");
}

/** Document equality ignores only browser serialization, never whitespace, text or marks. */
export function sameAssistantDocument(left: string, right: string): boolean {
  const parse = (html: string) => new DOMParser().parseFromString(html, "text/html").body;
  const a = parse(left), b = parse(right);
  // TipTap adds one empty paragraph to an empty scene. Whitespace text is not empty.
  const empty = (root: HTMLElement) => root.childNodes.length === 0 || (root.childNodes.length === 1 && root.firstChild instanceof HTMLParagraphElement && root.firstChild.attributes.length === 0 && root.firstChild.childNodes.length === 0);
  return (empty(a) && empty(b)) || a.isEqualNode(b);
}

export type AssistantDiffPart = { kind: "equal" | "removed" | "added"; text: string };
/** Linear-space Hirschberg LCS avoids a scene-size quadratic memory allocation. */
export function assistantTextDiff(before: string, after: string): AssistantDiffPart[] {
  const allA = Array.from(before), allB = Array.from(after);
  let prefix = 0, suffix = 0;
  while (prefix < Math.min(allA.length, allB.length) && allA[prefix] === allB[prefix]) prefix++;
  while (suffix < Math.min(allA.length, allB.length) - prefix && allA[allA.length - suffix - 1] === allB[allB.length - suffix - 1]) suffix++;
  const a = allA.slice(prefix, allA.length - suffix), b = allB.slice(prefix, allB.length - suffix);
  const row = (x: string[], y: string[]) => {
    let previous = new Uint32Array(y.length + 1);
    for (const char of x) { const next = new Uint32Array(y.length + 1); for (let j = 1; j <= y.length; j++) next[j] = char === y[j - 1] ? previous[j - 1] + 1 : Math.max(previous[j], next[j - 1]); previous = next; }
    return previous;
  };
  const common = (x: string[], y: string[]): string[] => {
    if (!x.length || !y.length) return [];
    if (x.length === 1) return y.includes(x[0]) ? x : [];
    const middle = Math.floor(x.length / 2), left = row(x.slice(0, middle), y), right = row(x.slice(middle).reverse(), [...y].reverse());
    let split = 0;
    for (let j = 1; j <= y.length; j++) if (left[j] + right[y.length - j] > left[split] + right[y.length - split]) split = j;
    return [...common(x.slice(0, middle), y.slice(0, split)), ...common(x.slice(middle), y.slice(split))];
  };
  const result: AssistantDiffPart[] = [];
  const add = (kind: AssistantDiffPart["kind"], text: string) => { if (!text) return; const last = result.at(-1); if (last?.kind === kind) last.text += text; else result.push({ kind, text }); };
  add("equal", allA.slice(0, prefix).join(""));
  let i = 0, j = 0;
  // Whole-scene rewrites remain complete and readable without quadratic UI work.
  const shared = a.length * b.length <= 4_000_000 ? common(a, b) : [];
  for (const char of shared) { while (a[i] !== char) add("removed", a[i++]); while (b[j] !== char) add("added", b[j++]); add("equal", char); i++; j++; }
  add("removed", a.slice(i).join("")); add("added", b.slice(j).join(""));
  if (suffix) add("equal", allA.slice(allA.length - suffix).join(""));
  return result;
}
