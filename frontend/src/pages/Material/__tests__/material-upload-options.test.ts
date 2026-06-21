import { describe, expect, it } from "vitest";
import {
  MATERIAL_UPLOAD_ACCEPT,
  SUPPORTED_MATERIAL_UPLOAD_LABEL,
  isSupportedMaterialUploadFile,
} from "../material-upload-options";

describe("material upload options", () => {
  it("exposes accepted extensions for the file picker", () => {
    expect(MATERIAL_UPLOAD_ACCEPT).toBe(".txt,.md,.pdf,.doc,.docx");
    expect(SUPPORTED_MATERIAL_UPLOAD_LABEL).toBe("TXT / Markdown / PDF / DOC / DOCX");
  });

  it("accepts supported upload extensions case-insensitively", () => {
    expect(isSupportedMaterialUploadFile({ name: "notes.TXT" })).toBe(true);
    expect(isSupportedMaterialUploadFile({ name: "outline.md" })).toBe(true);
    expect(isSupportedMaterialUploadFile({ name: "clue.PDF" })).toBe(true);
    expect(isSupportedMaterialUploadFile({ name: "archive.doc" })).toBe(true);
    expect(isSupportedMaterialUploadFile({ name: "chapter.DOCX" })).toBe(true);
  });

  it("rejects files without a supported extension", () => {
    expect(isSupportedMaterialUploadFile({ name: "payload.exe" })).toBe(false);
    expect(isSupportedMaterialUploadFile({ name: "untitled" })).toBe(false);
  });
});
