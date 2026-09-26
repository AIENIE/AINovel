import { z } from "zod";
import { autoSaveSchema, branchSchema, createBranchSchema, updateBranchSchema, mergeBranchSchema, mergeResultSchema, versionSchema, diffSchema } from "../../api-contracts";
import type { DomainTransport } from "./transport";

export function createVersionApi(http: DomainTransport) {
  const json = async <T>(schema: z.ZodType<T>, path: string, init?: RequestInit) => schema.parse(await http.json(path, init));
  const body = (payload: unknown, method = "POST") => ({ method, body: JSON.stringify(payload) });
  const base = (id: string) => `/v2/manuscripts/${id}`;
  return {
    listVersions: (id: string) => json(versionSchema.array(), `${base(id)}/versions`),
    createVersion: (id: string, payload: { snapshotType?: string; label?: string; metadata?: Record<string, unknown> } = {}) => json(versionSchema, `${base(id)}/versions`, body(payload)),
    getVersion: (id: string, version: string) => json(versionSchema, `${base(id)}/versions/${version}`),
    getDiff: (id: string, from: string, to: string) => json(diffSchema, `${base(id)}/versions/diff?${new URLSearchParams({ fromVersionId: from, toVersionId: to })}`),
    rollback: (id: string, version: string) => json(z.object({ manuscriptId: z.string(), rolledBackTo: z.string(), backupVersionId: z.string(), rollbackVersionId: z.string(), status: z.literal("completed") }), `${base(id)}/versions/${version}/rollback`, body({})),
    listBranches: (id: string) => json(branchSchema.array(), `${base(id)}/branches`),
    createBranch: (id: string, payload: z.input<typeof createBranchSchema>) => json(branchSchema, `${base(id)}/branches`, body(createBranchSchema.parse(payload))),
    updateBranch: (id: string, branch: string, payload: z.input<typeof updateBranchSchema>) => json(branchSchema, `${base(id)}/branches/${branch}`, body(updateBranchSchema.parse(payload), "PUT")),
    checkoutBranch: (id: string, branch: string) => json(z.object({ manuscriptId: z.string(), currentBranchId: z.string(), status: z.literal("checked_out") }), `${base(id)}/branches/${branch}/checkout`, body({})),
    mergeBranch: (id: string, branch: string, payload: z.input<typeof mergeBranchSchema> = {}) => json(mergeResultSchema, `${base(id)}/branches/${branch}/merge`, body(mergeBranchSchema.parse(payload))),
    abandonBranch: (id: string, branch: string) => http.void(`${base(id)}/branches/${branch}`, { method: "DELETE" }),
    getAutoSave: () => json(autoSaveSchema, "/v2/users/me/auto-save-config"),
    updateAutoSave: (payload: z.input<typeof autoSaveSchema>) => json(autoSaveSchema, "/v2/users/me/auto-save-config", body(autoSaveSchema.parse(payload), "PUT")),
  };
}
