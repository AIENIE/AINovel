import { describe, expect, it } from "vitest";
import { matchesAdminSearch } from "../admin-list-utils";

describe("admin list utilities", () => {
  it("matches nested values with trimmed case-insensitive search", () => {
    const item = {
      title: "雾港旧报",
      owner: "GoodBoy95",
      status: "PENDING_REVIEW",
      meta: { id: "asset-001" },
    };

    expect(matchesAdminSearch(item, " goodboy ")).toBe(true);
    expect(matchesAdminSearch(item, "pending")).toBe(true);
    expect(matchesAdminSearch(item, "asset-001")).toBe(true);
    expect(matchesAdminSearch(item, "不存在")).toBe(false);
  });

});
