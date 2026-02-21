import {
  AdminDashboardStats,
  FileImportJob,
  Material,
  Manuscript,
  ModelConfig,
  Outline,
  PromptTemplates,
  Story,
  UserSummary,
  User,
  World,
  WorldDetail,
  WorldModuleDefinition,
  WorldPromptTemplates,
} from "@/types";

const API_BASE = "/api";

const getToken = () => localStorage.getItem("token");

const FALLBACK_AI_MODELS: ModelConfig[] = [
  {
    id: "gpt-4o",
    name: "gpt-4o",
    displayName: "GPT-4o",
    modelType: "text",
    inputMultiplier: 1,
    outputMultiplier: 1,
    poolId: "default",
    isEnabled: true,
  },
  {
    id: "deepseek-chat",
    name: "deepseek-chat",
    displayName: "DeepSeek Chat",
    modelType: "text",
    inputMultiplier: 1,
    outputMultiplier: 1,
    poolId: "default",
    isEnabled: true,
  },
  {
    id: "claude-sonnet",
    name: "claude-sonnet",
    displayName: "Claude Sonnet",
    modelType: "text",
    inputMultiplier: 1,
    outputMultiplier: 1,
    poolId: "default",
    isEnabled: true,
  },
];

async function requestJson<T>(path: string, init: RequestInit = {}, tokenOverride?: string): Promise<T> {
  const headers = new Headers(init.headers || {});
  headers.set("Content-Type", "application/json");

  const token = tokenOverride ?? getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const resp = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!resp.ok) {
    const msg = await safeErrorMessage(resp);
    throw new Error(msg || `Request failed: ${resp.status}`);
  }
  return (await resp.json()) as T;
}

async function requestForm<T>(path: string, form: FormData, tokenOverride?: string): Promise<T> {
  const headers = new Headers();
  const token = tokenOverride ?? getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const resp = await fetch(`${API_BASE}${path}`, { method: "POST", headers, body: form });
  if (!resp.ok) {
    const msg = await safeErrorMessage(resp);
    throw new Error(msg || `Request failed: ${resp.status}`);
  }
  return (await resp.json()) as T;
}

async function requestVoid(path: string, init: RequestInit = {}, tokenOverride?: string): Promise<void> {
  const headers = new Headers(init.headers || {});
  headers.set("Content-Type", "application/json");

  const token = tokenOverride ?? getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const resp = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!resp.ok) {
    const msg = await safeErrorMessage(resp);
    throw new Error(msg || `Request failed: ${resp.status}`);
  }
}

async function safeErrorMessage(resp: Response): Promise<string> {
  try {
    const ct = resp.headers.get("content-type") || "";
    if (ct.includes("application/json")) {
      const data: any = await resp.json();
      return data?.message || data?.error || data?.msg || "";
    }
    return await resp.text();
  } catch {
    return "";
  }
}

function normalizeModel(model: any, index: number): ModelConfig {
  const fallbackId = `model-${index + 1}`;
  const id = String(model?.id ?? model?.modelKey ?? model?.name ?? fallbackId);
  const name = String(model?.name ?? model?.modelKey ?? id);
  const displayName = String(model?.displayName ?? model?.name ?? model?.modelKey ?? id);
  return {
    id,
    name,
    displayName,
    modelType: String(model?.modelType ?? model?.type ?? "unspecified"),
    inputMultiplier: Number(model?.inputMultiplier ?? 1),
    outputMultiplier: Number(model?.outputMultiplier ?? 1),
    poolId: String(model?.poolId ?? model?.provider ?? "default"),
    isEnabled: Boolean(model?.isEnabled ?? model?.enabled ?? model?.isAvailable ?? true),
  };
}

function toUser(profile: any): User {
  const projectCredits = Number(profile.projectCredits ?? profile.credits ?? 0);
  const publicCredits = Number(profile.publicCredits ?? 0);
  const totalCredits = Number(profile.totalCredits ?? (projectCredits + publicCredits));
  return {
    id: profile.id,
    username: profile.username,
    email: profile.email,
    avatar: profile.avatar || undefined,
    role: profile.role === "admin" ? "admin" : "user",
    credits: projectCredits,
    projectCredits,
    publicCredits,
    totalCredits,
    isBanned: Boolean(profile.isBanned ?? false),
    lastCheckIn: profile.lastCheckIn || undefined,
  };
}

function toStory(dto: any): Story {
  return {
    id: dto.id,
    title: dto.title,
    synopsis: dto.synopsis || "",
    genre: dto.genre || "",
    tone: dto.tone || "",
    status: dto.status || "draft",
    updatedAt: dto.updatedAt || new Date().toISOString(),
  };
}

function toOutline(dto: any): Outline {
  return {
    id: dto.id,
    storyId: dto.storyId,
    title: dto.title || "新大纲",
    chapters: (dto.chapters || []).map((c: any) => ({
      id: c.id,
      title: c.title || "",
      summary: c.summary || "",
      scenes: (c.scenes || []).map((s: any) => ({
        id: s.id,
        title: s.title || "",
        summary: s.summary || "",
        content: s.content || undefined,
      })),
    })),
    updatedAt: dto.updatedAt || new Date().toISOString(),
  };
}

function toWorld(dto: any): World {
  return {
    id: dto.id,
    name: dto.name,
    tagline: dto.tagline || "",
    status: dto.status || "draft",
    version: dto.version || "0.1.0",
    updatedAt: dto.updatedAt || new Date().toISOString(),
  };
}

function toWorldDetail(dto: any): WorldDetail {
  const modulesMap: Record<string, Record<string, string>> = dto.modules || {};
  return {
    id: dto.id,
    name: dto.name,
    tagline: dto.tagline || "",
    status: dto.status || "draft",
    version: dto.version || "0.1.0",
    updatedAt: dto.updatedAt || new Date().toISOString(),
    themes: dto.themes || [],
    creativeIntent: dto.creativeIntent || "",
    notes: dto.notes || "",
    modules: Object.entries(modulesMap).map(([key, fields]) => ({ key, fields: fields || {} })),
  };
}

function toMaterial(dto: any): Material {
  return {
    id: dto.id,
    title: dto.title,
    type: dto.type,
    content: dto.content || "",
    summary: dto.summary || undefined,
    tags: dto.tags || [],
    status: dto.status || "pending",
    createdAt: dto.createdAt || undefined,
  };
}

function toManuscript(dto: any): Manuscript {
  return {
    id: dto.id,
    outlineId: dto.outlineId,
    title: dto.title,
    worldId: dto.worldId || undefined,
    sections: dto.sections || {},
    updatedAt: dto.updatedAt || new Date().toISOString(),
  };
}

export const api = {
  user: {
    getProfile: async () => {
      const profile = await requestJson<any>("/v1/user/profile", { method: "GET" });
      return toUser(profile);
    },
    checkIn: async () => {
      return await requestJson<{
        success: boolean;
        points: number;
        newTotal: number;
        projectCredits: number;
        publicCredits: number;
        totalCredits: number;
        message?: string;
      }>("/v1/user/check-in", { method: "POST", body: "{}" });
    },
    redeem: async (code: string) => {
      return await requestJson<{
        success: boolean;
        points: number;
        newTotal: number;
        projectCredits: number;
        publicCredits: number;
        totalCredits: number;
        message?: string;
      }>("/v1/user/redeem", {
        method: "POST",
        body: JSON.stringify({ code }),
      });
    },
    convertPublicToProject: async (amount: number, idempotencyKey: string) => {
      return await requestJson<{
        orderNo: string;
        amount: number;
        projectCredits: number;
        publicCredits: number;
        totalCredits: number;
      }>("/v1/user/credits/convert", {
        method: "POST",
        body: JSON.stringify({ amount, idempotencyKey }),
      });
    },
    summary: async (): Promise<UserSummary> => {
      return await requestJson<UserSummary>("/v1/user/summary", { method: "GET" });
    },
  },

  ai: {
    chat: async (messages: any[], modelId: string, context: any) => {
      const payload = {
        modelId,
        context,
        messages: (messages || []).map((m) => ({ role: m.role, content: m.content })),
      };
      return await requestJson<any>("/v1/ai/chat", { method: "POST", body: JSON.stringify(payload) });
    },
    refine: async (text: string, instruction: string, modelId: string) => {
      return await requestJson<any>("/v1/ai/refine", {
        method: "POST",
        body: JSON.stringify({ text, instruction, modelId }),
      });
    },
    getModels: async (): Promise<ModelConfig[]> => {
      const candidateEndpoints = ["/v2/models", "/v1/ai/models"];
      for (const endpoint of candidateEndpoints) {
        try {
          const models = await requestJson<any[]>(endpoint, { method: "GET" });
          const normalized = (models || []).map((model, index) => normalizeModel(model, index));
          if (normalized.length) return normalized;
        } catch {
          // fallback to the next endpoint
        }
      }
      return FALLBACK_AI_MODELS;
    },
  },

  admin: {
    getDashboardStats: async (): Promise<AdminDashboardStats> => {
      return await requestJson<AdminDashboardStats>("/v1/admin/dashboard", { method: "GET" });
    },
    getUsers: async (): Promise<User[]> => {
      const users = await requestJson<any[]>("/v1/admin/users", { method: "GET" });
      return users.map((u) => ({
        id: String(u.id),
        username: u.username,
        email: u.email,
        role: u.role === "admin" ? "admin" : "user",
        credits: Number(u.credits ?? 0),
        projectCredits: Number(u.projectCredits ?? u.credits ?? 0),
        publicCredits: Number(u.publicCredits ?? 0),
        totalCredits: Number(u.totalCredits ?? ((u.projectCredits ?? u.credits ?? 0) + (u.publicCredits ?? 0))),
        isBanned: Boolean(u.isBanned ?? false),
        lastCheckIn: u.lastCheckIn || undefined,
      }));
    },
    banUser: async (userId: string) => {
      return await requestJson<boolean>(`/v1/admin/users/${userId}/ban`, { method: "POST" });
    },
    unbanUser: async (userId: string) => {
      return await requestJson<boolean>(`/v1/admin/users/${userId}/unban`, { method: "POST" });
    },
    getSystemConfig: async () => {
      return await requestJson<any>("/v1/admin/system-config", { method: "GET" });
    },
    updateSystemConfig: async (payload: any) => {
      return await requestJson<any>("/v1/admin/system-config", { method: "PUT", body: JSON.stringify(payload) });
    },
    listRedeemCodes: async () => {
      return await requestJson<any[]>("/v1/admin/redeem-codes", { method: "GET" });
    },
    createRedeemCode: async (payload: {
      code: string;
      grantAmount: number;
      maxUses?: number | null;
      startsAt?: string | null;
      expiresAt?: string | null;
      enabled?: boolean;
      stackable?: boolean;
      description?: string;
    }) => {
      return await requestJson<any>("/v1/admin/redeem-codes", { method: "POST", body: JSON.stringify(payload) });
    },
    grantProjectCredits: async (payload: { userId: string; amount: number; reason?: string }) => {
      return await requestJson<any>("/v1/admin/credits/grant", { method: "POST", body: JSON.stringify(payload) });
    },
  },

  stories: {
    list: async (): Promise<Story[]> => {
      const data = await requestJson<any[]>("/v1/story-cards", { method: "GET" });
      return data.map(toStory);
    },
    create: async (data: any) => {
      const dto = await requestJson<any>("/v1/stories", { method: "POST", body: JSON.stringify(data) });
      return toStory(dto);
    },
    conception: async (data: any) => {
      return await requestJson<any>("/v1/conception", { method: "POST", body: JSON.stringify(data) });
    },
    get: async (id: string) => {
      const dto = await requestJson<any>(`/v1/story-cards/${id}`, { method: "GET" });
      return toStory(dto);
    },
    listCharacters: async (storyId: string) => {
      return await requestJson<any[]>(`/v1/story-cards/${storyId}/character-cards`, { method: "GET" });
    },
    addCharacter: async (storyId: string, payload: { name: string; synopsis?: string; details?: string; relationships?: string }) => {
      return await requestJson<any>(`/v1/story-cards/${storyId}/characters`, { method: "POST", body: JSON.stringify(payload) });
    },
    updateCharacter: async (id: string, payload: any) => {
      return await requestJson<any>(`/v1/character-cards/${id}`, { method: "PUT", body: JSON.stringify(payload) });
    },
    deleteCharacter: async (id: string) => {
      await requestJson<any>(`/v1/character-cards/${id}`, { method: "DELETE" });
      return true;
    },
    update: async (id: string, data: any) => {
      const dto = await requestJson<any>(`/v1/story-cards/${id}`, { method: "PUT", body: JSON.stringify(data) });
      return toStory(dto);
    },
    delete: async (id: string) => {
      await requestJson<any>(`/v1/stories/${id}`, { method: "DELETE" });
      return true;
    },
  },

  outlines: {
    listByStory: async (storyId: string): Promise<Outline[]> => {
      const data = await requestJson<any[]>(`/v1/story-cards/${storyId}/outlines`, { method: "GET" });
      return data.map(toOutline);
    },
    create: async (storyId: string, data: { title?: string; worldId?: string } = {}) => {
      const dto = await requestJson<any>(`/v1/story-cards/${storyId}/outlines`, { method: "POST", body: JSON.stringify(data) });
      return toOutline(dto);
    },
    get: async (outlineId: string) => {
      const dto = await requestJson<any>(`/v1/outlines/${outlineId}`, { method: "GET" });
      return toOutline(dto);
    },
    save: async (outlineId: string, outline: Outline & { worldId?: string }) => {
      const payload = {
        title: outline.title,
        worldId: (outline as any).worldId,
        chapters: (outline.chapters || []).map((c: any, ci: number) => ({
          id: c.id,
          title: c.title,
          summary: c.summary,
          order: c.order ?? ci + 1,
          scenes: (c.scenes || []).map((s: any, si: number) => ({
            id: s.id,
            title: s.title,
            summary: s.summary,
            content: s.content,
            order: s.order ?? si + 1,
          })),
        })),
      };
      const dto = await requestJson<any>(`/v1/outlines/${outlineId}`, { method: "PUT", body: JSON.stringify(payload) });
      return toOutline(dto);
    },
    delete: async (outlineId: string) => {
      await requestJson<any>(`/v1/outlines/${outlineId}`, { method: "DELETE" });
      return true;
    },
  },

  manuscripts: {
    listByOutline: async (outlineId: string): Promise<Manuscript[]> => {
      const list = await requestJson<any[]>(`/v1/outlines/${outlineId}/manuscripts`, { method: "GET" });
      return list.map(toManuscript);
    },
    create: async (outlineId: string, payload: { title: string; worldId?: string }): Promise<Manuscript> => {
      const dto = await requestJson<any>(`/v1/outlines/${outlineId}/manuscripts`, { method: "POST", body: JSON.stringify(payload) });
      return toManuscript(dto);
    },
    get: async (id: string): Promise<Manuscript> => {
      const dto = await requestJson<any>(`/v1/manuscripts/${id}`, { method: "GET" });
      return toManuscript(dto);
    },
    delete: async (id: string) => {
      await requestJson<any>(`/v1/manuscripts/${id}`, { method: "DELETE" });
      return true;
    },
    generateScene: async (manuscriptId: string, sceneId: string): Promise<Manuscript> => {
      const dto = await requestJson<any>(`/v1/manuscripts/${manuscriptId}/scenes/${sceneId}/generate`, { method: "POST", body: "{}" });
      return toManuscript(dto);
    },
    saveSection: async (manuscriptId: string, sceneId: string, content: string): Promise<Manuscript> => {
      const dto = await requestJson<any>(`/v1/manuscripts/${manuscriptId}/sections/${sceneId}`, { method: "PUT", body: JSON.stringify({ content }) });
      return toManuscript(dto);
    },
  },

  worlds: {
    list: async (): Promise<World[]> => {
      const data = await requestJson<any[]>("/v1/worlds", { method: "GET" });
      return data.map(toWorld);
    },
    getDefinitions: async (): Promise<WorldModuleDefinition[]> => {
      return await requestJson<WorldModuleDefinition[]>("/v1/world-building/definitions", { method: "GET" });
    },
    getDetail: async (id: string): Promise<WorldDetail> => {
      const dto = await requestJson<any>(`/v1/worlds/${id}`, { method: "GET" });
      return toWorldDetail(dto);
    },
    create: async (data: any): Promise<World> => {
      const dto = await requestJson<any>("/v1/worlds", { method: "POST", body: JSON.stringify(data) });
      return toWorldDetail(dto);
    },
    update: async (id: string, detail: WorldDetail) => {
      await requestJson<any>(`/v1/worlds/${id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: detail.name,
          tagline: detail.tagline,
          themes: detail.themes,
          creativeIntent: detail.creativeIntent,
          notes: detail.notes,
        }),
      });
      const modules: Record<string, Record<string, string>> = {};
      (detail.modules || []).forEach((m) => (modules[m.key] = m.fields || {}));
      await requestJson<any>(`/v1/worlds/${id}/modules`, { method: "PUT", body: JSON.stringify({ modules }) });
      return true;
    },
    delete: async (id: string) => {
      await requestJson<any>(`/v1/worlds/${id}`, { method: "DELETE" });
      return true;
    },
    refineField: async (worldId: string, moduleKey: string, fieldKey: string, text: string, instruction?: string) => {
      return await requestJson<any>(`/v1/worlds/${worldId}/modules/${moduleKey}/fields/${fieldKey}/refine`, {
        method: "POST",
        body: JSON.stringify({ text, instruction: instruction || "" }),
      });
    },
    publishPreview: async (worldId: string) => {
      return await requestJson<any>(`/v1/worlds/${worldId}/publish/preview`, { method: "GET" });
    },
    publish: async (worldId: string) => {
      return await requestJson<any>(`/v1/worlds/${worldId}/publish`, { method: "POST", body: "{}" });
    },
    generationStatus: async (worldId: string) => {
      return await requestJson<any>(`/v1/worlds/${worldId}/generation`, { method: "GET" });
    },
    generateModule: async (worldId: string, moduleKey: string) => {
      return await requestJson<any>(`/v1/worlds/${worldId}/generation/${moduleKey}`, { method: "POST", body: "{}" });
    },
    retryModule: async (worldId: string, moduleKey: string) => {
      return await requestJson<any>(`/v1/worlds/${worldId}/generation/${moduleKey}/retry`, { method: "POST", body: "{}" });
    },
  },

  materials: {
    list: async (): Promise<Material[]> => {
      const data = await requestJson<any[]>("/v1/materials", { method: "GET" });
      return data.map(toMaterial);
    },
    create: async (payload: { title: string; type: any; content: string; tags?: string[] }) => {
      const dto = await requestJson<any>("/v1/materials", { method: "POST", body: JSON.stringify(payload) });
      return toMaterial(dto);
    },
    upload: async (file: File): Promise<FileImportJob> => {
      const form = new FormData();
      form.append("file", file);
      return await requestForm<FileImportJob>("/v1/materials/upload", form);
    },
    getUploadStatus: async (jobId: string): Promise<FileImportJob> => {
      return await requestJson<FileImportJob>(`/v1/materials/upload/${jobId}`, { method: "GET" });
    },
    getPending: async (): Promise<Material[]> => {
      const data = await requestJson<any[]>("/v1/materials/review/pending", { method: "POST", body: "{}" });
      return data.map(toMaterial);
    },
    review: async (id: string, action: "approve" | "reject") => {
      const path = action === "approve" ? "approve" : "reject";
      await requestJson<any>(`/v1/materials/${id}/review/${path}`, { method: "POST", body: JSON.stringify({}) });
      return true;
    },
    search: async (query: string): Promise<Material[]> => {
      const results = await requestJson<any[]>("/v1/materials/search", { method: "POST", body: JSON.stringify({ query, limit: 10 }) });
      const materials: Material[] = [];
      for (const r of results) {
        const id = r.materialId || r.id;
        if (!id) continue;
        try {
          const dto = await requestJson<any>(`/v1/materials/${id}`, { method: "GET" });
          materials.push(toMaterial(dto));
        } catch {
          materials.push({
            id,
            title: r.title || "素材",
            type: "text",
            content: r.snippet || "",
            tags: [],
            status: "approved",
          });
        }
      }
      return materials;
    },
  },

  prompts: {
    getWorkspace: async (): Promise<PromptTemplates> => {
      return await requestJson<PromptTemplates>("/v1/prompt-templates", { method: "GET" });
    },
    updateWorkspace: async (data: Partial<PromptTemplates>): Promise<PromptTemplates> => {
      return await requestJson<PromptTemplates>("/v1/prompt-templates", { method: "PUT", body: JSON.stringify(data) });
    },
    getWorld: async (): Promise<WorldPromptTemplates> => {
      return await requestJson<WorldPromptTemplates>("/v1/world-prompts", { method: "GET" });
    },
    updateWorld: async (data: Partial<WorldPromptTemplates>): Promise<WorldPromptTemplates> => {
      return await requestJson<WorldPromptTemplates>("/v1/world-prompts", { method: "PUT", body: JSON.stringify(data) });
    },
  },

  v2: {
    context: {
      listLorebook: async (storyId: string) => requestJson<any[]>(`/v2/stories/${storyId}/lorebook`, { method: "GET" }),
      createLorebook: async (storyId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/lorebook`, { method: "POST", body: JSON.stringify(payload) }),
      updateLorebook: async (storyId: string, entryId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/lorebook/${entryId}`, { method: "PUT", body: JSON.stringify(payload) }),
      deleteLorebook: async (storyId: string, entryId: string) => {
        await requestVoid(`/v2/stories/${storyId}/lorebook/${entryId}`, { method: "DELETE" });
      },
      importLorebook: async (storyId: string, entries: any[]) =>
        requestJson<any>(`/v2/stories/${storyId}/lorebook/import`, { method: "POST", body: JSON.stringify({ entries }) }),
      getGraph: async (storyId: string) => requestJson<any>(`/v2/stories/${storyId}/graph`, { method: "GET" }),
      queryGraph: async (storyId: string, keyword: string) =>
        requestJson<any>(`/v2/stories/${storyId}/graph/query?keyword=${encodeURIComponent(keyword)}`, { method: "GET" }),
      createRelationship: async (storyId: string, payload: { source: string; target: string; relationType: string }) =>
        requestJson<any>(`/v2/stories/${storyId}/graph/relationships`, { method: "POST", body: JSON.stringify(payload) }),
      deleteRelationship: async (storyId: string, relationshipId: string) => {
        await requestVoid(`/v2/stories/${storyId}/graph/relationships/${relationshipId}`, { method: "DELETE" });
      },
      syncGraph: async (storyId: string) => requestJson<any>(`/v2/stories/${storyId}/graph/sync`, { method: "POST", body: "{}" }),
      extractEntities: async (storyId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/extract-entities`, { method: "POST", body: JSON.stringify(payload) }),
      listExtractions: async (storyId: string) => requestJson<any[]>(`/v2/stories/${storyId}/extractions`, { method: "GET" }),
      reviewExtraction: async (storyId: string, id: string, reviewAction: string) =>
        requestJson<any>(`/v2/stories/${storyId}/extractions/${id}/review`, {
          method: "PUT",
          body: JSON.stringify({ reviewAction }),
        }),
      previewContext: async (storyId: string, tokenBudget = 3500) =>
        requestJson<any>(`/v2/stories/${storyId}/context/preview?tokenBudget=${tokenBudget}`, { method: "GET" }),
    },

    style: {
      listProfiles: async (storyId: string) => requestJson<any[]>(`/v2/stories/${storyId}/style-profiles`, { method: "GET" }),
      createProfile: async (storyId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/style-profiles`, { method: "POST", body: JSON.stringify(payload) }),
      updateProfile: async (storyId: string, profileId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/style-profiles/${profileId}`, { method: "PUT", body: JSON.stringify(payload) }),
      deleteProfile: async (storyId: string, profileId: string) => {
        await requestVoid(`/v2/stories/${storyId}/style-profiles/${profileId}`, { method: "DELETE" });
      },
      activateProfile: async (storyId: string, profileId: string) =>
        requestJson<any>(`/v2/stories/${storyId}/style-profiles/${profileId}/activate`, { method: "POST", body: "{}" }),
      analyze: async (payload: any) => requestJson<any>("/v2/style-analysis", { method: "POST", body: JSON.stringify(payload) }),
      listVoices: async (storyId: string) => requestJson<any[]>(`/v2/stories/${storyId}/character-voices`, { method: "GET" }),
      createVoice: async (storyId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/character-voices`, { method: "POST", body: JSON.stringify(payload) }),
      updateVoice: async (storyId: string, voiceId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/character-voices/${voiceId}`, { method: "PUT", body: JSON.stringify(payload) }),
      deleteVoice: async (storyId: string, voiceId: string) => {
        await requestVoid(`/v2/stories/${storyId}/character-voices/${voiceId}`, { method: "DELETE" });
      },
      generateVoice: async (storyId: string, voiceId: string, payload: any = {}) =>
        requestJson<any>(`/v2/stories/${storyId}/character-voices/${voiceId}/generate`, { method: "POST", body: JSON.stringify(payload) }),
    },

    analysis: {
      triggerBetaReader: async (storyId: string, payload: any = {}) =>
        requestJson<any>(`/v2/stories/${storyId}/analysis/beta-reader`, { method: "POST", body: JSON.stringify(payload) }),
      triggerContinuity: async (storyId: string, payload: any = {}) =>
        requestJson<any>(`/v2/stories/${storyId}/analysis/continuity-check`, { method: "POST", body: JSON.stringify(payload) }),
      listJobs: async (storyId: string) => requestJson<any[]>(`/v2/stories/${storyId}/analysis/jobs`, { method: "GET" }),
      getJob: async (storyId: string, jobId: string) =>
        requestJson<any>(`/v2/stories/${storyId}/analysis/jobs/${jobId}`, { method: "GET" }),
      listReports: async (storyId: string) => requestJson<any[]>(`/v2/stories/${storyId}/analysis/reports`, { method: "GET" }),
      getReport: async (storyId: string, reportId: string) =>
        requestJson<any>(`/v2/stories/${storyId}/analysis/reports/${reportId}`, { method: "GET" }),
      listIssues: async (storyId: string) =>
        requestJson<any[]>(`/v2/stories/${storyId}/analysis/continuity-issues`, { method: "GET" }),
      updateIssue: async (storyId: string, issueId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/analysis/continuity-issues/${issueId}`, {
          method: "PUT",
          body: JSON.stringify(payload),
        }),
    },

    version: {
      listVersions: async (manuscriptId: string) => requestJson<any[]>(`/v2/manuscripts/${manuscriptId}/versions`, { method: "GET" }),
      createVersion: async (manuscriptId: string, payload: any = {}) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/versions`, { method: "POST", body: JSON.stringify(payload) }),
      getVersion: async (manuscriptId: string, versionId: string) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/versions/${versionId}`, { method: "GET" }),
      getDiff: async (manuscriptId: string, fromVersionId: string, toVersionId: string) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/versions/diff?fromVersionId=${fromVersionId}&toVersionId=${toVersionId}`, { method: "GET" }),
      rollback: async (manuscriptId: string, versionId: string) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/versions/${versionId}/rollback`, { method: "POST", body: "{}" }),
      listBranches: async (manuscriptId: string) =>
        requestJson<any[]>(`/v2/manuscripts/${manuscriptId}/branches`, { method: "GET" }),
      createBranch: async (manuscriptId: string, payload: any) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/branches`, { method: "POST", body: JSON.stringify(payload) }),
      updateBranch: async (manuscriptId: string, branchId: string, payload: any) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/branches/${branchId}`, { method: "PUT", body: JSON.stringify(payload) }),
      checkoutBranch: async (manuscriptId: string, branchId: string) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/branches/${branchId}/checkout`, { method: "POST", body: "{}" }),
      mergeBranch: async (manuscriptId: string, branchId: string, payload: any = {}) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/branches/${branchId}/merge`, {
          method: "POST",
          body: JSON.stringify(payload),
        }),
      abandonBranch: async (manuscriptId: string, branchId: string) => {
        await requestVoid(`/v2/manuscripts/${manuscriptId}/branches/${branchId}`, { method: "DELETE" });
      },
      getAutoSave: async () => requestJson<any>("/v2/users/me/auto-save-config", { method: "GET" }),
      updateAutoSave: async (payload: any) =>
        requestJson<any>("/v2/users/me/auto-save-config", { method: "PUT", body: JSON.stringify(payload) }),
    },

    export: {
      createJob: async (manuscriptId: string, payload: any) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/export`, { method: "POST", body: JSON.stringify(payload) }),
      listJobs: async (manuscriptId: string) =>
        requestJson<any[]>(`/v2/manuscripts/${manuscriptId}/export/jobs`, { method: "GET" }),
      getJob: async (manuscriptId: string, jobId: string) =>
        requestJson<any>(`/v2/manuscripts/${manuscriptId}/export/jobs/${jobId}`, { method: "GET" }),
      listTemplates: async () => requestJson<any[]>("/v2/export-templates", { method: "GET" }),
      createTemplate: async (payload: any) =>
        requestJson<any>("/v2/export-templates", { method: "POST", body: JSON.stringify(payload) }),
      updateTemplate: async (templateId: string, payload: any) =>
        requestJson<any>(`/v2/export-templates/${templateId}`, { method: "PUT", body: JSON.stringify(payload) }),
      deleteTemplate: async (templateId: string) => {
        await requestVoid(`/v2/export-templates/${templateId}`, { method: "DELETE" });
      },
      downloadUrl: (manuscriptId: string, jobId: string) => `${API_BASE}/v2/manuscripts/${manuscriptId}/export/jobs/${jobId}/download`,
    },

    models: {
      list: async () => requestJson<any[]>("/v2/models", { method: "GET" }),
      listRouting: async () => requestJson<any[]>("/v2/admin/model-routing", { method: "GET" }),
      updateRouting: async (taskType: string, payload: any) =>
        requestJson<any>(`/v2/admin/model-routing/${taskType}`, { method: "PUT", body: JSON.stringify(payload) }),
      listPreferences: async () => requestJson<any[]>("/v2/users/me/model-preferences", { method: "GET" }),
      setPreference: async (taskType: string, preferredModelId: string | null) =>
        requestJson<any>(`/v2/users/me/model-preferences/${taskType}`, {
          method: "PUT",
          body: JSON.stringify({ preferredModelId }),
        }),
      resetPreference: async (taskType: string) => {
        await requestVoid(`/v2/users/me/model-preferences/${taskType}`, { method: "DELETE" });
      },
      usageSummary: async () => requestJson<any>("/v2/users/me/model-usage", { method: "GET" }),
      usageDetails: async () => requestJson<any[]>("/v2/users/me/model-usage/details", { method: "GET" }),
      compare: async (storyId: string, payload: any) =>
        requestJson<any>(`/v2/stories/${storyId}/compare-models`, { method: "POST", body: JSON.stringify(payload) }),
    },

    workspace: {
      listLayouts: async () => requestJson<any[]>("/v2/users/me/workspace-layouts", { method: "GET" }),
      createLayout: async (payload: any) =>
        requestJson<any>("/v2/users/me/workspace-layouts", { method: "POST", body: JSON.stringify(payload) }),
      updateLayout: async (layoutId: string, payload: any) =>
        requestJson<any>(`/v2/users/me/workspace-layouts/${layoutId}`, { method: "PUT", body: JSON.stringify(payload) }),
      deleteLayout: async (layoutId: string) => {
        await requestVoid(`/v2/users/me/workspace-layouts/${layoutId}`, { method: "DELETE" });
      },
      activateLayout: async (layoutId: string) =>
        requestJson<any>(`/v2/users/me/workspace-layouts/${layoutId}/activate`, { method: "POST", body: "{}" }),
      startSession: async (payload: any) => requestJson<any>("/v2/writing-sessions/start", { method: "POST", body: JSON.stringify(payload) }),
      heartbeatSession: async (sessionId: string, payload: any) =>
        requestJson<any>(`/v2/writing-sessions/${sessionId}/heartbeat`, { method: "PUT", body: JSON.stringify(payload) }),
      endSession: async (sessionId: string, payload: any = {}) =>
        requestJson<any>(`/v2/writing-sessions/${sessionId}/end`, { method: "POST", body: JSON.stringify(payload) }),
      getStats: async () => requestJson<any>("/v2/writing-sessions/stats", { method: "GET" }),
      listGoals: async () => requestJson<any[]>("/v2/users/me/writing-goals", { method: "GET" }),
      createGoal: async (payload: any) =>
        requestJson<any>("/v2/users/me/writing-goals", { method: "POST", body: JSON.stringify(payload) }),
      updateGoal: async (goalId: string, payload: any) =>
        requestJson<any>(`/v2/users/me/writing-goals/${goalId}`, { method: "PUT", body: JSON.stringify(payload) }),
      deleteGoal: async (goalId: string) => {
        await requestVoid(`/v2/users/me/writing-goals/${goalId}`, { method: "DELETE" });
      },
      listShortcuts: async () => requestJson<any[]>("/v2/users/me/shortcuts", { method: "GET" }),
      updateShortcuts: async (shortcuts: Array<{ action: string; shortcut: string }>) =>
        requestJson<any[]>("/v2/users/me/shortcuts", { method: "PUT", body: JSON.stringify({ shortcuts }) }),
    },
  },
};
