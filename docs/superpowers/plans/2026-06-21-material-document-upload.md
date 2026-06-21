# Material Document Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend material upload from TXT-only to TXT, Markdown, PDF, DOC, and DOCX with file type and size protections.

**Architecture:** Add a focused backend parser component that validates upload extension/size, extracts plain text, and returns a normalized parsed file for the existing material import flow. Keep the current upload job and pending-review behavior unchanged, and update the React upload tab plus docs to reflect the supported formats.

**Tech Stack:** Java 25, Spring Boot multipart upload, Apache Tika parser stack, JUnit 5, React 18, TypeScript, Vitest.

---

### Task 1: Backend Parser Contract

**Files:**
- Create: `backend/src/test/java/com/ainovel/app/material/MaterialFileParserTest.java`
- Create: `backend/src/main/java/com/ainovel/app/material/MaterialFileParser.java`
- Modify: `backend/pom.xml`

- [ ] **Step 1: Write failing tests**
  - Verify `.txt/.md/.pdf/.doc/.docx` are treated as supported upload names.
  - Verify Markdown upload bytes are decoded as UTF-8 text.
  - Verify DOCX upload content is extracted to plain text.
  - Verify unsupported extensions and over-limit files are rejected before import.

- [ ] **Step 2: Run backend parser test and confirm RED**
  - Run: `mvn -q -Dtest=MaterialFileParserTest test`
  - Expected: compile failure because `MaterialFileParser` does not exist yet.

- [ ] **Step 3: Implement parser**
  - Add Apache Tika standard parser package to `backend/pom.xml`.
  - Implement `MaterialFileParser` with `supportsFileName`, `parse`, and `ParsedMaterialFile`.
  - Default max upload size: `app.material.upload.max-file-size-bytes=10485760`.

- [ ] **Step 4: Run backend parser test and confirm GREEN**
  - Run: `mvn -q -Dtest=MaterialFileParserTest test`
  - Expected: all parser tests pass.

### Task 2: Upload Endpoint Integration

**Files:**
- Modify: `backend/src/main/java/com/ainovel/app/material/MaterialController.java`

- [ ] **Step 1: Wire parser into upload endpoint**
  - Replace direct UTF-8 byte decoding with `MaterialFileParser.parse(file)`.
  - Pass parsed filename and text content into `MaterialService.createUploadJob`.

- [ ] **Step 2: Run material backend tests**
  - Run: `mvn -q -Dtest=MaterialFileParserTest,MaterialServiceClosureTest test`
  - Expected: parser and existing material closure tests pass.

### Task 3: Frontend Upload Guard

**Files:**
- Create: `frontend/src/pages/Material/material-upload-options.ts`
- Create: `frontend/src/pages/Material/__tests__/material-upload-options.test.ts`
- Modify: `frontend/src/pages/Material/tabs/MaterialUpload.tsx`

- [ ] **Step 1: Write failing frontend utility tests**
  - Verify accept string is `.txt,.md,.pdf,.doc,.docx`.
  - Verify extension-based support accepts uppercase variants.
  - Verify unsupported files are rejected.

- [ ] **Step 2: Run frontend test and confirm RED**
  - Run: `npm test -- material-upload-options`
  - Expected: module import failure because utility does not exist yet.

- [ ] **Step 3: Implement utility and update component**
  - Use extension-based validation instead of browser MIME type.
  - Update visible copy and toast message to list supported formats.

- [ ] **Step 4: Run frontend test and confirm GREEN**
  - Run: `npm test -- material-upload-options`
  - Expected: utility tests pass.

### Task 4: Documentation And Verification

**Files:**
- Modify: `frontend/doc/general.md`
- Modify: `frontend/doc/page-materials.md`
- Modify: `doc/api/material.md`
- Modify: `doc/modules/module-analysis.md`
- Modify: `backend/Dockerfile`
- Create: `doc/test/2026-06-21-material-document-upload.md`

- [ ] **Step 1: Update docs**
  - Replace TXT-only wording with supported document formats.
  - Document file size protection and pending-review flow.
  - Remove the completed PDF/Doc upload item from general pending work.

- [ ] **Step 2: Run full verification**
  - Run: `mvn -q test`
  - Run: `npm test`
  - Run: `npm run build`
  - Run: `printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh` when `SUDO_PASSWORD` is set, otherwise `sudo ./build.sh`.

- [ ] **Step 3: Runtime smoke**
  - Confirm `https://ainovel.localhut.com/` returns HTTP 200.
  - Log in with admin credentials from `env.txt`.
  - Open `/materials`, verify upload copy includes TXT/Markdown/PDF/DOC/DOCX and no console errors.

- [ ] **Step 4: Commit and push**
  - Commit message: `feat: support material document uploads`
  - Push branch: `feat/material-document-upload`
