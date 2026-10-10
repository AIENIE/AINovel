import { expect, it } from "vitest";
import { materialTextDiff } from "./material-text-diff";
it.each([
  ["😀桥开放。", "😀桥关闭。"], ["原文", "原文"], ["", "新文"], ["旧文", ""], ["甲\n乙\n丙", "甲\n新增\n丙"], ["甲甲甲", "甲甲"], ["桥开放", "桥只在日落后开放"],
])("reconstructs both originals and uses exact code-point positions: %s -> %s", (before, after) => {
  const diff = materialTextDiff(before,after);
  expect(diff.filter(change => change.kind !== "added").map(change => change.text).join("")).toBe(before);
  expect(diff.filter(change => change.kind !== "removed").map(change => change.text).join("")).toBe(after);
  for (const change of diff) expect(Array.from(change.kind === "removed" ? before : after).slice(change.start,change.end).join("")).toBe(change.text);
});
