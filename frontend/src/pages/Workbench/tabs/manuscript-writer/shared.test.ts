import { expect, it } from "vitest";
import { versionWordCount } from "./shared";
it("distinguishes a missing historical count from corrupt content and a real empty version", () => {
  expect(versionWordCount({})).toEqual({ value: null, invalid: false });
  expect(versionWordCount({ sectionsJson: "{broken" })).toEqual({ value: null, invalid: true });
  expect(versionWordCount({ sectionsJson: "{}" })).toEqual({ value: 0, invalid: false });
  expect(versionWordCount({ sectionsJson: '{"scene":"<p>正文</p>"}' })).toEqual({ value: 2, invalid: false });
  expect(versionWordCount({ sectionsJson: '{"scene":12}' }).invalid).toBe(true);
});
