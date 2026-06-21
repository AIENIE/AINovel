# Material List Filter Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add usable filtering and sorting controls to the material list page and close the documented "列表筛选/排序" backlog item.

**Architecture:** Keep this frontend-only because `GET /api/v1/materials` already returns the complete list needed by the current table. Extract filtering and sorting into a small pure utility so behavior is covered by Vitest while `MaterialList` remains focused on UI state and rendering.

**Tech Stack:** React 18, TypeScript, Vite, shadcn/ui Select/Input/Table, lucide-react, Vitest.

---

### Task 1: Filtering And Sorting Utility

**Files:**
- Create: `frontend/src/pages/Material/material-list-utils.ts`
- Create: `frontend/src/pages/Material/__tests__/material-list-utils.test.ts`

- [x] **Step 1: Write failing utility tests**
  - Verify combined filtering by search text, status, material type, and tag keyword.
  - Verify created-time sorting puts missing dates last.
  - Verify title sorting uses locale-aware comparison.

- [x] **Step 2: Run focused test and confirm RED**
  - Run: `npm test -- material-list-utils.test.ts`
  - Expected: module import failure because `material-list-utils.ts` does not exist yet.

- [x] **Step 3: Implement utility**
  - Export `MaterialListFilters`, filter/sort option types, and `filterAndSortMaterials`.
  - Match search against title, summary, content, and tags.
  - Sort by `createdAt` or `title`; keep missing/invalid dates at the end.

- [x] **Step 4: Run focused test and confirm GREEN**
  - Run: `npm test -- material-list-utils.test.ts`
  - Expected: all material list utility tests pass.

### Task 2: Material List Controls

**Files:**
- Modify: `frontend/src/pages/Material/tabs/MaterialList.tsx`

- [x] **Step 1: Add filter and sort state**
  - Add state for free-text search, status, type, tag keyword, sort field, and sort direction.

- [x] **Step 2: Render controls**
  - Add search input, status/type selects, tag keyword input, sort field select, and sort direction select.
  - Use existing shadcn/ui and lucide-react components.

- [x] **Step 3: Apply utility output to table**
  - Render `filterAndSortMaterials(materials, filters)` instead of direct title/tag filtering.
  - Add empty state text for no matching material.

### Task 3: Documentation And Verification

**Files:**
- Modify: `frontend/doc/page-materials.md`
- Create: `doc/test/2026-06-21-material-list-filter-sort.md`

- [x] **Step 1: Update docs**
  - Document list filters and sorting controls.
  - Remove completed "列表筛选/排序" pending item.

- [x] **Step 2: Run verification**
  - Run: `npm test -- material-list-utils.test.ts`
  - Run: `npm test`
  - Run: `npm run build`
  - Run: `mvn -q test`
  - Run: `printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh` when `SUDO_PASSWORD` is set, otherwise `sudo ./build.sh`.

- [x] **Step 3: Runtime smoke**
  - Open `https://ainovel.localhut.com/materials` after deploy.
  - Verify the material list controls show "全部状态", "全部类型", "标签关键词", "创建时间", and "降序".
  - Confirm browser console errors are empty.

- [ ] **Step 4: Commit and push**
  - Commit message: `feat: add material list filters`
  - Push branch: `feat/material-list-filter-sort`
