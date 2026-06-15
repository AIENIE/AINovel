import { describe, expect, it } from "vitest";
import { matchesAdminSearch, paginateItems } from "../admin-list-utils";

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

  it("paginates arrays with safe page and size boundaries", () => {
    const items = Array.from({ length: 12 }, (_, index) => index + 1);

    expect(paginateItems(items, 0, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(paginateItems(items, 2, 5)).toEqual([11, 12]);
    expect(paginateItems(items, -1, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(paginateItems(items, 0, 0)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  });
});
