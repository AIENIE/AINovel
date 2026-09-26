import { z } from "zod";
import { exportJobSchema, exportTemplateSchema, exportRequestSchema, templateRequestSchema } from "../../api-contracts";
import type { DomainTransport } from "./transport";

export function createExportApi(http: DomainTransport) {
  const json = async <T>(schema: z.ZodType<T>, path: string, init?: RequestInit) => schema.parse(await http.json(path, init));
  const body = (payload: unknown, method = "POST") => ({ method, body: JSON.stringify(payload) });
  const base = (id: string) => `/v2/manuscripts/${id}/export`;
  return {
    createJob: (id: string, payload: z.input<typeof exportRequestSchema>) => json(exportJobSchema, base(id), body(exportRequestSchema.parse(payload))),
    listJobs: (id: string) => json(exportJobSchema.array(), `${base(id)}/jobs`),
    getJob: (id: string, job: string) => json(exportJobSchema, `${base(id)}/jobs/${job}`),
    listTemplates: () => json(exportTemplateSchema.array(), "/v2/export-templates"),
    createTemplate: (payload: z.input<typeof templateRequestSchema>) => json(exportTemplateSchema, "/v2/export-templates", body(templateRequestSchema.parse(payload))),
    updateTemplate: (id: string, payload: z.input<typeof templateRequestSchema>) => json(exportTemplateSchema, `/v2/export-templates/${id}`, body(templateRequestSchema.parse(payload), "PUT")),
    deleteTemplate: (id: string) => http.void(`/v2/export-templates/${id}`, { method: "DELETE" }),
    download: (id: string, job: string) => http.blob(`${base(id)}/jobs/${job}/download`),
  };
}
