import { afterEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api-client";
import i18n from "@/i18n";
import { narrativeErrorMessage } from "./narrative-errors";

afterEach(() => { void i18n.changeLanguage("zh-CN"); });
describe("narrative recovery messages", () => {
  it.each(["zh-CN", "zh-TW", "en"])("gives an actionable quote correction in %s", async locale => {
    await i18n.changeLanguage(locale);
    const message = narrativeErrorMessage(new ApiError(422, "NARRATIVE_EVIDENCE_AMBIGUOUS", "request-123"));
    expect(message).toContain("request-123");
    expect(message).not.toContain("NARRATIVE_");
    expect(message).toMatch(/引文|quote/);
    expect(message).toMatch(/唯一|one occurrence/);
  });
  it("explains stale sources separately from a transient version conflict", () => {
    expect(narrativeErrorMessage(new ApiError(409, "NARRATIVE_SOURCE_STALE"))).toContain("重新确认正文");
    expect(narrativeErrorMessage(new ApiError(409, "NARRATIVE_VERSION_CHANGED"))).toContain("核对最新版本");
  });
  it.each(["NARRATIVE_EVIDENCE_AMBIGUOUS SELECT password FROM users", "constructor", "__proto__", "SQL password=secret"])("never exposes unknown backend content: %s", raw => {
    const message = narrativeErrorMessage(new ApiError(422, raw, "<script>secret</script>"));
    expect(message).not.toContain(raw);
    expect(message).not.toContain("secret");
  });
  it("does not misclassify an unavailable service using a domain code", () => {
    expect(narrativeErrorMessage(new ApiError(503, "NARRATIVE_EVIDENCE_AMBIGUOUS"))).toContain("服务暂时不可用");
  });
});
