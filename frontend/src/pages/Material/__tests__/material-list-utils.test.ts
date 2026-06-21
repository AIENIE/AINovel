import { describe, expect, it } from "vitest";
import { filterAndSortMaterials, type MaterialListFilters } from "../material-list-utils";
import type { Material } from "@/types";

describe("material list utilities", () => {
  it("filters by search text, status, type, and tag keyword together", () => {
    const filters: MaterialListFilters = {
      search: "码头",
      status: "approved",
      type: "text",
      tag: "雨夜",
      sortBy: "createdAt",
      sortDirection: "desc",
    };

    const result = filterAndSortMaterials(
      [
        material({ id: "1", title: "陆家码头旧报", tags: ["雨夜", "旧案"], status: "approved", type: "text", createdAt: "2026-06-20T10:00:00Z" }),
        material({ id: "2", title: "陆家码头草稿", tags: ["雨夜"], status: "pending", type: "text", createdAt: "2026-06-21T10:00:00Z" }),
        material({ id: "3", title: "港口图像", tags: ["雨夜"], status: "approved", type: "image", createdAt: "2026-06-19T10:00:00Z" }),
        material({ id: "4", title: "陆家码头白天记录", tags: ["白天"], status: "approved", type: "text", createdAt: "2026-06-18T10:00:00Z" }),
      ],
      filters,
    );

    expect(result.map((item) => item.id)).toEqual(["1"]);
  });

  it("sorts by created time with missing dates placed last", () => {
    const result = filterAndSortMaterials(
      [
        material({ id: "old", title: "旧报", createdAt: "2026-06-18T10:00:00Z" }),
        material({ id: "missing", title: "无日期", createdAt: undefined }),
        material({ id: "new", title: "新报", createdAt: "2026-06-20T10:00:00Z" }),
      ],
      { search: "", status: "all", type: "all", tag: "", sortBy: "createdAt", sortDirection: "desc" },
    );

    expect(result.map((item) => item.id)).toEqual(["new", "old", "missing"]);
  });

  it("sorts by title using locale comparison", () => {
    const result = filterAndSortMaterials(
      [
        material({ id: "b", title: "雨夜码头" }),
        material({ id: "a", title: "白天码头" }),
      ],
      { search: "", status: "all", type: "all", tag: "", sortBy: "title", sortDirection: "asc" },
    );

    expect(result.map((item) => item.id)).toEqual(["a", "b"]);
  });
});

function material(overrides: Partial<Material>): Material {
  return {
    id: "m",
    title: "素材",
    type: "text",
    content: "正文",
    tags: [],
    status: "approved",
    ...overrides,
  };
}
