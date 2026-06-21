export const MATERIAL_UPLOAD_ACCEPT = ".txt,.md,.pdf,.doc,.docx";
export const SUPPORTED_MATERIAL_UPLOAD_LABEL = "TXT / Markdown / PDF / DOC / DOCX";

const SUPPORTED_MATERIAL_UPLOAD_EXTENSIONS = new Set(["txt", "md", "pdf", "doc", "docx"]);

export function isSupportedMaterialUploadFile(file: Pick<File, "name">): boolean {
  const extension = file.name.split(".").pop()?.toLowerCase();
  return Boolean(extension && extension !== file.name.toLowerCase() && SUPPORTED_MATERIAL_UPLOAD_EXTENSIONS.has(extension));
}
