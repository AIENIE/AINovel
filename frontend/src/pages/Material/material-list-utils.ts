import type { Material } from "@/types";

export type MaterialStatusFilter = "all" | Material["status"];
export type MaterialTypeFilter = "all" | Material["type"];
export type MaterialSortBy = "createdAt" | "title";
export type MaterialSortDirection = "asc" | "desc";

export interface MaterialListFilters {
  search: string;
  status: MaterialStatusFilter;
  type: MaterialTypeFilter;
  tag: string;
  sortBy: MaterialSortBy;
  sortDirection: MaterialSortDirection;
}

export function filterAndSortMaterials(materials: Material[], filters: MaterialListFilters): Material[] {
  const search = normalize(filters.search);
  const tag = normalize(filters.tag);
  return [...materials]
    .filter((material) => matchesSearch(material, search))
    .filter((material) => filters.status === "all" || material.status === filters.status)
    .filter((material) => filters.type === "all" || material.type === filters.type)
    .filter((material) => !tag || material.tags.some((item) => normalize(item).includes(tag)))
    .sort((left, right) => compareMaterials(left, right, filters.sortBy, filters.sortDirection));
}

function matchesSearch(material: Material, search: string): boolean {
  if (!search) {
    return true;
  }
  return normalize(material.title).includes(search)
    || normalize(material.summary).includes(search)
    || normalize(material.content).includes(search)
    || material.tags.some((tag) => normalize(tag).includes(search));
}

function compareMaterials(left: Material, right: Material, sortBy: MaterialSortBy, direction: MaterialSortDirection): number {
  const multiplier = direction === "asc" ? 1 : -1;
  if (sortBy === "title") {
    return left.title.localeCompare(right.title, "zh-CN") * multiplier;
  }
  return compareCreatedAt(left.createdAt, right.createdAt, direction);
}

function compareCreatedAt(left?: string, right?: string, direction: MaterialSortDirection = "desc"): number {
  if (!left && !right) {
    return 0;
  }
  if (!left) {
    return 1;
  }
  if (!right) {
    return -1;
  }
  const leftTime = Date.parse(left);
  const rightTime = Date.parse(right);
  if (Number.isNaN(leftTime) && Number.isNaN(rightTime)) {
    return 0;
  }
  if (Number.isNaN(leftTime)) {
    return 1;
  }
  if (Number.isNaN(rightTime)) {
    return -1;
  }
  return direction === "asc" ? leftTime - rightTime : rightTime - leftTime;
}

function normalize(value?: string): string {
  return (value ?? "").trim().toLowerCase();
}
