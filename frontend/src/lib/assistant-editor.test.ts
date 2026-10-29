import { afterEach, describe, expect, it } from "vitest";
import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import { AllSelection } from "@tiptap/pm/state";
import { assistantTextDiff, captureAssistantTarget, sameAssistantDocument } from "./assistant-editor";

const editors: Editor[] = [];
function editor(html: string) { const value = new Editor({ extensions: [StarterKit], content: html }); editors.push(value); return value; }
afterEach(() => editors.splice(0).forEach(value => value.destroy()));
describe("writing assistant editor boundaries", () => {
  it("captures top-level indices and UTF-16 offsets across marks, emoji and hard breaks", () => {
    const value = editor("<p>A<strong>😀B</strong><br>C</p><p>尾巴</p>");
    value.commands.setTextSelection({ from: 2, to: 10 });
    expect(captureAssistantTarget(value)).toMatchObject({ supported: true, selection: { startBlockIndex: 0, startOffset: 1, endBlockIndex: 1, endOffset: 1 }, text: "😀B\nC\n尾" });
    value.commands.setTextSelection(6);
    expect(captureAssistantTarget(value, true).selection).toMatchObject({ startOffset: 5, endOffset: 5 });
  });
  it("preserves a list between selected ordinary paragraph runs and rejects a list boundary", () => {
    const value = editor("<p>甲</p><ul><li><p>不可重排</p></li></ul><p>乙</p>");
    value.commands.setTextSelection({ from: 1, to: value.state.doc.content.size - 1 });
    expect(captureAssistantTarget(value)).toMatchObject({ supported: true, selection: { startBlockIndex: 0, endBlockIndex: 2 } });
    value.commands.setTextSelection({ from: 6, to: 7 });
    expect(captureAssistantTarget(value).supported).toBe(false);
  });
  it("maps select-all to the full scene and never cuts a UTF-16 surrogate", () => {
    const value = editor("<h2>固定标题</h2><p>A😀B</p><p>末段</p>");
    value.view.dispatch(value.state.tr.setSelection(new AllSelection(value.state.doc)));
    expect(captureAssistantTarget(value)).toMatchObject({ selection: null, supported: true, text: "固定标题\nA😀B\n末段" });
    const firstSize = value.state.doc.child(0).nodeSize;
    value.commands.setTextSelection({ from: firstSize + 3, to: firstSize + 4 });
    expect(captureAssistantTarget(value).supported).toBe(false);
  });
  it("retains every unchanged island and paragraph boundary in a fine-grained diff", () => {
    const before = "甲说你好😀。\n\n中间原样。\n\n乙回答好。", after = "甲轻声说你好😀。\n中间原样。\n\n乙回答不了。";
    const parts = assistantTextDiff(before, after);
    expect(parts.filter(part => part.kind !== "added").map(part => part.text).join("")).toBe(before);
    expect(parts.filter(part => part.kind !== "removed").map(part => part.text).join("")).toBe(after);
    expect(parts.some(part => part.kind === "equal" && part.text.includes("中间原样"))).toBe(true);
  });
  it("compares exact document meaning and formatting without ignoring author whitespace", () => {
    expect(sameAssistantDocument("<p>A&amp;B<br/>C</p>", "<p>A&amp;B<br>C</p>")).toBe(true);
    expect(sameAssistantDocument("<p>A B</p>", "<p>AB</p>")).toBe(false);
    expect(sameAssistantDocument("<p><strong>甲</strong></p>", "<p>甲</p>")).toBe(false);
    expect(sameAssistantDocument("", "<p></p>")).toBe(true);
    expect(sameAssistantDocument("", "<p> </p>")).toBe(false);
    expect(sameAssistantDocument("", "<p><br></p>")).toBe(false);
  });
  it("bounds large rewrites while keeping all text and trims large unchanged edges", () => {
    const prefix = "原文😀".repeat(5000), suffix = "结尾".repeat(5000);
    expect(assistantTextDiff(`${prefix}甲${suffix}`, `${prefix}乙${suffix}`)).toEqual([
      { kind: "equal", text: prefix }, { kind: "removed", text: "甲" }, { kind: "added", text: "乙" }, { kind: "equal", text: suffix },
    ]);
    const before = "甲".repeat(10000), after = "乙".repeat(10000);
    expect(assistantTextDiff(before, after)).toEqual([{ kind: "removed", text: before }, { kind: "added", text: after }]);
  });
});
