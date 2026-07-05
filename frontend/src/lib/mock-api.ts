import {
  AdminDashboardStats,
  CreditConversionRecord,
  CreditLedgerItem,
  FileImportJob,
  Material,
  MaterialCitation,
  MaterialDuplicateCandidate,
  MaterialSearchResult,
  Manuscript,
  ModelConfig,
  Outline,
  PlotPlanning,
  PlotQualityRun,
  PlotQualityTrend,
  PromptMetadata,
  PromptTemplates,
  SlopDriftRun,
  SlopQualityRun,
  Story,
  UserSummary,
  User,
  V2AnalysisJob,
  V2AnalysisReport,
  V2AnalysisTriggerRequest,
  V2ContinuityIssue,
  V2ContinuityIssueUpdateRequest,
  World,
  WorldDetail,
  WorldModuleDefinition,
  WorldPromptMetadata,
  WorldPromptTemplates,
} from "@/types";

const API_BASE = "/api";

const USER_TOKEN_KEY = "token";
const ADMIN_TOKEN_KEY = "admin_token";

const getToken = () => localStorage.getItem(USER_TOKEN_KEY);
const getAdminToken = () => localStorage.getItem(ADMIN_TOKEN_KEY);

function requireAdminToken(): string {
  const token = getAdminToken();
  if (!token) {
    throw new Error("管理员未登录");
  }
  return token;
}

export const adminSession = {
  tokenKey: ADMIN_TOKEN_KEY,
  getToken: getAdminToken,
  setToken: (token: string) => localStorage.setItem(ADMIN_TOKEN_KEY, token),
  clearToken: () => localStorage.removeItem(ADMIN_TOKEN_KEY),
};

const REQUIRED_AI_MODEL_KEY = "deepseek-v4-flash";

const FALLBACK_AI_MODELS: ModelConfig[] = [
  {
    id: REQUIRED_AI_MODEL_KEY,
    name: REQUIRED_AI_MODEL_KEY,
    displayName: "DeepSeek V4 Flash",
    modelType: "text",
    inputMultiplier: 1,
    outputMultiplier: 1,
    poolId: "DeepSeek",
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

function queryString(params: Record<string, unknown>): string {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    qs.set(key, String(value));
  });
  const text = qs.toString();
  return text ? `?${text}` : "";
}

function normalizeModel(model: any, index: number): ModelConfig {
  const fallbackId = `model-${index + 1}`;
  const preferredKey = model?.modelKey ?? model?.name ?? model?.id ?? fallbackId;
  const id = String(preferredKey);
  const name = String(model?.modelKey ?? model?.name ?? model?.id ?? id);
  const displayName = String(model?.displayName ?? model?.modelKey ?? model?.name ?? id);
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

function normalizeList(value: any): string[] {
  return Array.isArray(value)
    ? value.map((item) => String(item ?? "").trim()).filter(Boolean)
    : [];
}

function toPlotPlanning(dto: any): PlotPlanning | undefined {
  if (!dto || typeof dto !== "object") return undefined;
  const twistOptions = Array.isArray(dto.twistOptions)
    ? dto.twistOptions
        .map((item: any, index: number) => ({
          id: String(item?.id ?? `twist-${index + 1}`),
          label: String(item?.label ?? (index === 0 ? "灵感版" : "结构版")),
          track: String(item?.track ?? (index === 0 ? "instinct" : "structure")) === "structure" ? "structure" : "instinct",
          hook: String(item?.hook ?? item?.summary ?? ""),
          hiddenTruth: String(item?.hiddenTruth ?? dto?.hiddenTruth ?? ""),
          setup: normalizeList(item?.setup ?? item?.setupPoints),
          misdirection: normalizeList(item?.misdirection ?? item?.misdirectionPoints),
          revealBeat: String(item?.revealBeat ?? ""),
          revealTiming: String(item?.revealTiming ?? ""),
          payoff: String(item?.payoff ?? ""),
          risk: String(item?.risk ?? item?.earlyRevealRisk ?? ""),
        }))
        .filter((item) => item.hook || item.hiddenTruth || item.setup.length || item.misdirection.length)
    : [];
  const rawForeshadows = Array.isArray(dto.foreshadowPlans) ? dto.foreshadowPlans : dto.foreshadowSeeds;
  const foreshadowPlans = Array.isArray(rawForeshadows)
    ? rawForeshadows
        .map((item: any, index: number) => ({
          id: String(item?.id ?? item?.entryKey ?? `foreshadow-${index + 1}`),
          clue: String(item?.clue ?? item?.setup ?? ""),
          disguise: String(item?.disguise ?? item?.coverLayer ?? item?.misdirectionLayer ?? ""),
          payoff: String(item?.payoff ?? ""),
          revealTiming: String(item?.revealTiming ?? ""),
        }))
        .filter((item) => item.clue || item.payoff)
    : [];
  const beats = Array.isArray(dto.beats)
    ? dto.beats
        .map((item: any, index: number) => ({
          id: String(item?.id ?? `beat-${index + 1}`),
          label: String(item?.label ?? `Beat ${index + 1}`),
          summary: String(item?.summary ?? ""),
        }))
        .filter((item) => item.summary)
    : [];
  const planning: PlotPlanning = {
    corePromise: String(dto.corePromise ?? ""),
    centralQuestion: String(dto.centralQuestion ?? ""),
    hiddenTruth: String(dto.hiddenTruth ?? ""),
    readerMisdirect: String(dto.readerMisdirect ?? dto.readerExpectation ?? ""),
    stakes: String(dto.stakes ?? ""),
    beats,
    twistOptions,
    foreshadowPlans,
    memeStrategy:
      dto.memeStrategy && typeof dto.memeStrategy === "object"
        ? {
            reference: String(dto.memeStrategy.reference ?? dto.memeStrategy.sourceDomain ?? ""),
            purpose: String(dto.memeStrategy.purpose ?? dto.memeStrategy.useCase ?? ""),
            usage: String(dto.memeStrategy.usage ?? dto.memeStrategy.naturalVersion ?? ""),
            caution: String(dto.memeStrategy.caution ?? dto.memeStrategy.immersionRisk ?? dto.memeStrategy.conservativeVersion ?? ""),
          }
        : undefined,
    selectedTwistId: dto.selectedTwistId == null ? undefined : String(dto.selectedTwistId),
    lorebookSeeds: Array.isArray(dto.lorebookSeeds) ? dto.lorebookSeeds : [],
    graphSeeds: Array.isArray(dto.graphSeeds) ? dto.graphSeeds : [],
    confidence: dto.confidence == null ? undefined : Number(dto.confidence),
    warnings: normalizeList(dto.warnings),
  };
  return planning.corePromise ||
    planning.centralQuestion ||
    planning.hiddenTruth ||
    planning.beats.length ||
    planning.twistOptions.length ||
    planning.foreshadowPlans.length ||
    planning.memeStrategy
    ? planning
    : undefined;
}

function toOutline(dto: any): Outline {
  const planning = toPlotPlanning(dto?.planning);
  return {
    id: dto.id,
    storyId: dto.storyId,
    title: dto.title || "新大纲",
    chapters: (dto.chapters || []).map((c: any) => ({
      id: c.id,
      title: c.title || "",
      summary: c.summary || "",
      planning: c?.planning
        ? {
            purpose: c.planning.purpose || c.planning.tensionShift || "",
            informationRelease: c.planning.informationRelease || c.planning.revealFocus || "",
            twistRole: c.planning.twistRole || "",
            selectedTwistId: c.planning.selectedTwistId || "",
            revealFocus: c.planning.revealFocus || c.planning.informationRelease || "",
            tensionShift: c.planning.tensionShift || c.planning.purpose || "",
          }
        : undefined,
      scenes: (c.scenes || []).map((s: any) => ({
        id: s.id,
        title: s.title || "",
        summary: s.summary || "",
        content: s.content || undefined,
        planning: s?.planning
          ? {
              goal: s.planning.goal || s.planning.foreshadowHint || "",
              conflict: s.planning.conflict || s.planning.misdirectionAction || "",
              infoRelease: s.planning.infoRelease || s.planning.revealTrigger || "",
              foreshadowId: s.planning.foreshadowId || "",
              revealFor: s.planning.revealFor || "",
              foreshadowHint: s.planning.foreshadowHint || "",
              misdirectionAction: s.planning.misdirectionAction || "",
              revealTrigger: s.planning.revealTrigger || "",
              payoffPlan: s.planning.payoffPlan || "",
              memeUsage: s.planning.memeUsage || "",
            }
          : undefined,
      })),
    })),
    updatedAt: dto.updatedAt || new Date().toISOString(),
    planning,
    activeTwistId: dto.activeTwistId || planning?.selectedTwistId || planning?.twistOptions[0]?.id,
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

function toMaterialSearchResult(dto: any): MaterialSearchResult {
  return {
    materialId: String(dto.materialId ?? dto.id ?? ""),
    chunkId: String(dto.chunkId ?? dto.materialId ?? dto.id ?? ""),
    title: String(dto.title ?? "素材"),
    snippet: String(dto.snippet ?? dto.content ?? ""),
    score: Number(dto.score ?? 0),
    chunkSeq: dto.chunkSeq == null ? undefined : Number(dto.chunkSeq),
    source: String(dto.source ?? "keyword"),
    matchReasons: Array.isArray(dto.matchReasons) ? dto.matchReasons.map((item: any) => String(item)) : [],
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

function toSlopQualityRun(dto: any): SlopQualityRun {
  return {
    id: String(dto.id),
    storyId: String(dto.storyId),
    manuscriptId: String(dto.manuscriptId),
    sceneId: String(dto.sceneId),
    status: String(dto.status || "ACCEPTED"),
    maxSeverity: String(dto.maxSeverity || "LOW"),
    overallRiskScore: Number(dto.overallRiskScore || 0),
    revised: Boolean(dto.revised),
    revisionCount: Number(dto.revisionCount || 0),
    summary: dto.summary || undefined,
    analysisMode: dto.analysisMode || undefined,
    riskLabel: dto.riskLabel || undefined,
    evidenceLevel: dto.evidenceLevel || undefined,
    safeClaim: dto.safeClaim || undefined,
    moduleScores: parseJsonObject(dto.moduleScoresJson ?? dto.moduleScores),
    alternativeExplanations: parseStringArray(dto.alternativeExplanationsJson ?? dto.alternativeExplanations),
    revisionPriorities: parseJsonList(dto.revisionPrioritiesJson ?? dto.revisionPriorities),
    rewriteTasks: parseJsonList(dto.rewriteTasksJson ?? dto.rewriteTasks),
    createdAt: dto.createdAt || undefined,
    issues: Array.isArray(dto.issues)
      ? dto.issues.map((issue: any) => ({
          id: String(issue.id),
          dimension: String(issue.dimension || ""),
          severity: String(issue.severity || "LOW"),
          riskScore: Number(issue.riskScore || 0),
          evidence: issue.evidence || undefined,
          whyItMatters: issue.whyItMatters || undefined,
          minimalFix: issue.minimalFix || undefined,
          charStart: issue.charStart === null || issue.charStart === undefined ? undefined : Number(issue.charStart),
          charEnd: issue.charEnd === null || issue.charEnd === undefined ? undefined : Number(issue.charEnd),
          quote: issue.quote || undefined,
          module: issue.module || undefined,
          patternId: issue.patternId || undefined,
          issueType: issue.issueType || undefined,
          evidenceLevel: issue.evidenceLevel || undefined,
          alternativeExplanations: parseStringArray(issue.alternativeExplanationsJson ?? issue.alternativeExplanations),
          repairHint: issue.repairHint || undefined,
        }))
      : [],
  };
}

function toSlopDriftRun(dto: any): SlopDriftRun {
  return {
    id: String(dto.id),
    storyId: String(dto.storyId),
    manuscriptId: String(dto.manuscriptId),
    status: String(dto.status || "COMPLETED"),
    overallRiskScore: Number(dto.overallRiskScore || 0),
    riskLabel: dto.riskLabel || undefined,
    safeClaim: dto.safeClaim || undefined,
    summary: dto.summary || undefined,
    totalCharacters: Number(dto.totalCharacters || 0),
    windowCount: Number(dto.windowCount || 0),
    sourceTextHash: dto.sourceTextHash || undefined,
    windowSummaries: parseJsonList(dto.windowSummariesJson ?? dto.windowSummaries),
    metricCurves: parseJsonObject(dto.metricCurvesJson ?? dto.metricCurves),
    driftPoints: parseJsonList(dto.driftPointsJson ?? dto.driftPoints),
    evidenceItems: parseJsonList(dto.evidenceItemsJson ?? dto.evidenceItems),
    alternativeExplanations: parseStringArray(dto.alternativeExplanationsJson ?? dto.alternativeExplanations),
    rewriteTasks: parseJsonList(dto.rewriteTasksJson ?? dto.rewriteTasks),
    createdAt: dto.createdAt || undefined,
  };
}

function parseJsonValue(value: any): unknown {
  if (typeof value !== "string") return value;
  if (!value.trim()) return undefined;
  try {
    return JSON.parse(value);
  } catch {
    return undefined;
  }
}

function parseJsonObject(value: any): Record<string, unknown> {
  const parsed = parseJsonValue(value);
  return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : {};
}

function parseJsonList(value: any): any[] {
  const parsed = parseJsonValue(value);
  return Array.isArray(parsed) ? parsed : [];
}

function parseStringArray(value: any): string[] {
  return parseJsonList(value).map((item) => String(item));
}

function parseJsonArray(value: any): string[] {
  if (Array.isArray(value)) return value.map((item) => String(item));
  if (typeof value !== "string" || !value.trim()) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
  } catch {
    return [];
  }
}

function toPlotQualityRun(dto: any): PlotQualityRun {
  return {
    id: String(dto.id),
    storyId: String(dto.storyId),
    manuscriptId: String(dto.manuscriptId),
    sceneId: String(dto.sceneId),
    chapterTitle: dto.chapterTitle || undefined,
    sceneTitle: dto.sceneTitle || undefined,
    chapterOrder: Number(dto.chapterOrder || 0),
    sceneOrder: Number(dto.sceneOrder || 0),
    status: String(dto.status || "ACCEPTED"),
    maxSeverity: String(dto.maxSeverity || "LOW"),
    overallRiskScore: Number(dto.overallRiskScore || 0),
    summary: dto.summary || undefined,
    rewritePlan: parseJsonArray(dto.rewritePlanJson ?? dto.rewritePlan),
    surgicalFixes: parseJsonArray(dto.surgicalFixesJson ?? dto.surgicalFixes),
    revisionCandidateText: dto.revisionCandidateText || undefined,
    revisionApplied: Boolean(dto.revisionApplied),
    revisionAppliedAt: dto.revisionAppliedAt || undefined,
    createdAt: dto.createdAt || undefined,
    issues: Array.isArray(dto.issues)
      ? dto.issues.map((issue: any) => ({
          id: String(issue.id),
          dimension: String(issue.dimension || ""),
          severity: String(issue.severity || "LOW"),
          riskScore: Number(issue.riskScore || 0),
          evidence: issue.evidence || undefined,
          whyItMatters: issue.whyItMatters || undefined,
          minimalFix: issue.minimalFix || undefined,
        }))
      : [],
  };
}

function toPlotQualityTrend(dto: any): PlotQualityTrend {
  return {
    manuscriptId: String(dto.manuscriptId || ""),
    averageRisk: Number(dto.averageRisk || 0),
    highRiskScenes: Number(dto.highRiskScenes || 0),
    dimensionCounts: Object.fromEntries(
      Object.entries(dto.dimensionCounts || {}).map(([key, value]) => [key, Number(value || 0)])
    ),
    points: Array.isArray(dto.points)
      ? dto.points.map((point: any) => ({
          runId: String(point.runId || ""),
          sceneId: String(point.sceneId || ""),
          chapterTitle: point.chapterTitle || undefined,
          sceneTitle: point.sceneTitle || undefined,
          chapterOrder: Number(point.chapterOrder || 0),
          sceneOrder: Number(point.sceneOrder || 0),
          riskScore: Number(point.riskScore || 0),
          maxSeverity: point.maxSeverity || undefined,
          status: point.status || undefined,
        }))
      : [],
  };
}

export const api = {
  adminAuth: {
    login: async (username: string, password: string): Promise<{ token: string; username: string; loggedInAt: string }> => {
      return await requestJson<{ token: string; username: string; loggedInAt: string }>(
        "/v1/admin-auth/login",
        { method: "POST", body: JSON.stringify({ username, password }) },
        "",
      );
    },
    me: async (): Promise<{ username: string }> => {
      return await requestJson<{ username: string }>("/v1/admin-auth/me", { method: "GET" }, requireAdminToken());
    },
    logout: async (): Promise<void> => {
      await requestVoid("/v1/admin-auth/logout", { method: "POST", body: "{}" }, requireAdminToken());
    },
  },

  user: {
    getProfile: async () => {
      const profile = await requestJson<any>("/v1/user/profile", { method: "GET" });
      return toUser(profile);
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
        projectBefore: number;
        projectAfter: number;
        publicBefore: number;
        publicAfter: number;
        totalCredits: number;
      }>("/v1/user/credits/convert", {
        method: "POST",
        body: JSON.stringify({ amount, idempotencyKey }),
      });
    },
    listLedger: async (): Promise<CreditLedgerItem[]> => {
      return await requestJson<CreditLedgerItem[]>("/v1/user/credits/ledger?page=0&size=50", { method: "GET" });
    },
    listConversionHistory: async (): Promise<CreditConversionRecord[]> => {
      return await requestJson<CreditConversionRecord[]>("/v1/user/credits/conversions?page=0&size=50", { method: "GET" });
    },
    summary: async (): Promise<UserSummary> => {
      return await requestJson<UserSummary>("/v1/user/summary", { method: "GET" });
    },
  },

  ai: {
    chat: async (messages: any[], modelId: string, context: any) => {
      const payload = {
        modelId: REQUIRED_AI_MODEL_KEY,
        context,
        messages: (messages || []).map((m) => ({ role: m.role, content: m.content })),
      };
      return await requestJson<any>("/v1/ai/chat", { method: "POST", body: JSON.stringify(payload) });
    },
    refine: async (text: string, instruction: string, modelId: string) => {
      return await requestJson<any>("/v1/ai/refine", {
        method: "POST",
        body: JSON.stringify({ text, instruction, modelId: REQUIRED_AI_MODEL_KEY }),
      });
    },
    getModels: async (): Promise<ModelConfig[]> => {
      try {
        const models = await requestJson<any[]>("/v1/ai/models", { method: "GET" });
        const normalized = (models || []).map((model, index) => normalizeModel(model, index));
        const required = normalized.filter((model) => model.name === REQUIRED_AI_MODEL_KEY || model.id === REQUIRED_AI_MODEL_KEY);
        if (required.length) return required;
      } catch {
        // fallback to the required ai-service model descriptor
      }
      return FALLBACK_AI_MODELS;
    },
  },

  admin: {
    getDashboardStats: async (): Promise<AdminDashboardStats> => {
      return await requestJson<AdminDashboardStats>("/v1/admin/dashboard", { method: "GET" }, requireAdminToken());
    },
    getUsers: async (search?: string): Promise<User[]> => {
      const keyword = search?.trim();
      const path = keyword ? `/v1/admin/users?search=${encodeURIComponent(keyword)}` : "/v1/admin/users";
      const users = await requestJson<any[]>(path, { method: "GET" }, requireAdminToken());
      return users.map((u) => ({
        id: String(u.id),
        username: u.username,
        email: u.email,
        role: u.role === "admin" ? "admin" : "user",
        credits: Number(u.projectCredits ?? 0),
        projectCredits: Number(u.projectCredits ?? 0),
        publicCredits: Number(u.publicCredits ?? 0),
        totalCredits: Number(u.totalCredits ?? ((u.projectCredits ?? 0) + (u.publicCredits ?? 0))),
        isBanned: Boolean(u.isBanned ?? false),
        storyCount: Number(u.storyCount ?? 0),
        worldCount: Number(u.worldCount ?? 0),
      }));
    },
    getSystemConfig: async () => {
      return await requestJson<any>("/v1/admin/system-config", { method: "GET" }, requireAdminToken());
    },
    updateSystemConfig: async (payload: any) => {
      return await requestJson<any>("/v1/admin/system-config", { method: "PUT", body: JSON.stringify(payload) }, requireAdminToken());
    },
    listRedeemCodes: async () => {
      return await requestJson<any[]>("/v1/admin/redeem-codes", { method: "GET" }, requireAdminToken());
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
      return await requestJson<any>("/v1/admin/redeem-codes", { method: "POST", body: JSON.stringify(payload) }, requireAdminToken());
    },
    grantProjectCredits: async (payload: { userId: string; amount: number; reason?: string }) => {
      return await requestJson<any>("/v1/admin/credits/grant", { method: "POST", body: JSON.stringify(payload) }, requireAdminToken());
    },
    listConversionOrders: async (page = 0, size = 50) => {
      return await requestJson<any[]>(`/v1/admin/credits/conversions?page=${page}&size=${size}`, { method: "GET" }, requireAdminToken());
    },
    listCreditLedger: async (page = 0, size = 50) => {
      return await requestJson<any[]>(`/v1/admin/credits/ledger?page=${page}&size=${size}`, { method: "GET" }, requireAdminToken());
    },
    getAssetSummary: async () => {
      return await requestJson<any>("/v1/admin/assets/summary", { method: "GET" }, requireAdminToken());
    },
    listPendingMaterials: async (): Promise<Material[]> => {
      const data = await requestJson<any[]>("/v1/admin/materials/pending", { method: "GET" }, requireAdminToken());
      return data.map(toMaterial);
    },
    approveMaterial: async (id: string, payload: any = {}) => {
      return toMaterial(await requestJson<any>(`/v1/admin/materials/${id}/approve`, { method: "POST", body: JSON.stringify(payload) }, requireAdminToken()));
    },
    rejectMaterial: async (id: string, payload: any = {}) => {
      return toMaterial(await requestJson<any>(`/v1/admin/materials/${id}/reject`, { method: "POST", body: JSON.stringify(payload) }, requireAdminToken()));
    },
    findMaterialDuplicates: async () => {
      return await requestJson<any[]>("/v1/admin/materials/duplicates", { method: "POST", body: "{}" }, requireAdminToken());
    },
    mergeMaterials: async (payload: any) => {
      return toMaterial(await requestJson<any>("/v1/admin/materials/merge", { method: "POST", body: JSON.stringify(payload) }, requireAdminToken()));
    },
    listAssets: async (kind: "stories" | "worlds" | "manuscripts") => {
      return await requestJson<any[]>(`/v1/admin/assets/${kind}`, { method: "GET" }, requireAdminToken());
    },
    listQualityRuns: async () => {
      return await requestJson<any[]>("/v1/admin/quality/runs", { method: "GET" }, requireAdminToken());
    },
    getOpsSummary: async () => {
      return await requestJson<any>("/v1/admin/ops/summary", { method: "GET" }, requireAdminToken());
    },
    listDependencies: async () => {
      return await requestJson<any[]>("/v1/admin/ops/dependencies", { method: "GET" }, requireAdminToken());
    },
    listOpsEvents: async (params: { severity?: string; category?: string; from?: string; to?: string; page?: number; size?: number } = {}) => {
      return await requestJson<any>(`/v1/admin/ops/events${queryString(params)}`, { method: "GET" }, requireAdminToken());
    },
    listAuditRecords: async (params: { action?: string; actor?: string; targetType?: string; from?: string; to?: string; page?: number; size?: number } = {}) => {
      return await requestJson<any>(`/v1/admin/ops/audit${queryString(params)}`, { method: "GET" }, requireAdminToken());
    },
    listOpsAlerts: async () => {
      return await requestJson<any[]>("/v1/admin/ops/alerts", { method: "GET" }, requireAdminToken());
    },
    getOpsDiagnostics: async () => {
      return await requestJson<any>("/v1/admin/ops/diagnostics", { method: "GET" }, requireAdminToken());
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
      const dto = await requestJson<any>("/v1/conception", { method: "POST", body: JSON.stringify(data) });
      const plotPlanning =
        toPlotPlanning(dto?.plotPlanning) ||
        toPlotPlanning({
          ...dto?.generated?.skeleton,
          twistOptions: dto?.generated?.twistOptions,
          foreshadowSeeds: dto?.generated?.foreshadowSeeds,
          memeStrategy: dto?.generated?.memeStrategy,
          lorebookSeeds: dto?.generated?.lorebookSeeds,
          graphSeeds: dto?.generated?.graphSeeds,
          selectedTwistId: dto?.generated?.outlineSuggestion?.planning?.selectedTwistId,
        });
      const outlineSeed = dto?.outlineSeed
        ? {
            title: String(dto.outlineSeed.title ?? "剧情骨架方案"),
            chapters: Array.isArray(dto.outlineSeed.chapters) ? dto.outlineSeed.chapters : [],
          }
        : dto?.generated?.outlineSuggestion
          ? {
              title: String(dto.generated.outlineSuggestion.title ?? "剧情骨架方案"),
              chapters: Array.isArray(dto.generated.outlineSuggestion.chapters) ? dto.generated.outlineSuggestion.chapters : [],
            }
          : undefined;
      return {
        ...dto,
        storyCard: dto?.storyCard ? toStory(dto.storyCard) : undefined,
        plotPlanning,
        outlineSeed,
        generated: dto?.generated
          ? {
              ...dto.generated,
              skeleton: {
                corePromise: String(dto.generated.skeleton?.corePromise ?? ""),
                centralQuestion: String(dto.generated.skeleton?.centralQuestion ?? ""),
                hiddenTruth: String(dto.generated.skeleton?.hiddenTruth ?? ""),
                readerExpectation: String(dto.generated.skeleton?.readerExpectation ?? ""),
              },
              plotPlanning,
            }
          : undefined,
      };
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
    create: async (storyId: string, data: { title?: string; worldId?: string; planning?: any } = {}) => {
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
        planning: outline.planning,
        chapters: (outline.chapters || []).map((c: any, ci: number) => ({
          id: c.id,
          title: c.title,
          summary: c.summary,
          order: c.order ?? ci + 1,
          planning: c.planning,
          scenes: (c.scenes || []).map((s: any, si: number) => ({
            id: s.id,
            title: s.title,
            summary: s.summary,
            content: s.content,
            order: s.order ?? si + 1,
            planning: s.planning,
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
    search: async (query: string): Promise<MaterialSearchResult[]> => {
      const results = await requestJson<any[]>("/v1/materials/search", { method: "POST", body: JSON.stringify({ query, limit: 10 }) });
      return results.map(toMaterialSearchResult).filter((item) => item.materialId && item.chunkId);
    },
    findDuplicates: async (): Promise<MaterialDuplicateCandidate[]> => {
      return await requestJson<MaterialDuplicateCandidate[]>("/v1/materials/find-duplicates", { method: "POST", body: "{}" });
    },
    getCitations: async (id: string): Promise<MaterialCitation[]> => {
      return await requestJson<MaterialCitation[]>(`/v1/materials/${id}/citations`, { method: "GET" });
    },
  },

  prompts: {
    getWorkspace: async (): Promise<PromptTemplates> => {
      return await requestJson<PromptTemplates>("/v1/prompt-templates", { method: "GET" });
    },
    updateWorkspace: async (data: Partial<PromptTemplates>): Promise<PromptTemplates> => {
      return await requestJson<PromptTemplates>("/v1/prompt-templates", { method: "PUT", body: JSON.stringify(data) });
    },
    resetWorkspace: async (): Promise<PromptTemplates> => {
      return await requestJson<PromptTemplates>("/v1/prompt-templates/reset", { method: "POST", body: "{}" });
    },
    getWorkspaceMetadata: async (): Promise<PromptMetadata> => {
      return await requestJson<PromptMetadata>("/v1/prompt-templates/metadata", { method: "GET" });
    },
    getWorld: async (): Promise<WorldPromptTemplates> => {
      return await requestJson<WorldPromptTemplates>("/v1/world-prompts", { method: "GET" });
    },
    updateWorld: async (data: Partial<WorldPromptTemplates>): Promise<WorldPromptTemplates> => {
      return await requestJson<WorldPromptTemplates>("/v1/world-prompts", { method: "PUT", body: JSON.stringify(data) });
    },
    resetWorld: async (): Promise<WorldPromptTemplates> => {
      return await requestJson<WorldPromptTemplates>("/v1/world-prompts/reset", { method: "POST", body: "{}" });
    },
    getWorldMetadata: async (): Promise<WorldPromptMetadata> => {
      return await requestJson<WorldPromptMetadata>("/v1/world-prompts/metadata", { method: "GET" });
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
      triggerBetaReader: async (storyId: string, payload: V2AnalysisTriggerRequest = {}): Promise<V2AnalysisJob> =>
        requestJson<V2AnalysisJob>(`/v2/stories/${storyId}/analysis/beta-reader`, { method: "POST", body: JSON.stringify(payload) }),
      triggerContinuity: async (storyId: string, payload: V2AnalysisTriggerRequest = {}): Promise<V2AnalysisJob> =>
        requestJson<V2AnalysisJob>(`/v2/stories/${storyId}/analysis/continuity-check`, { method: "POST", body: JSON.stringify(payload) }),
      listJobs: async (storyId: string): Promise<V2AnalysisJob[]> =>
        requestJson<V2AnalysisJob[]>(`/v2/stories/${storyId}/analysis/jobs`, { method: "GET" }),
      getJob: async (storyId: string, jobId: string): Promise<V2AnalysisJob> =>
        requestJson<V2AnalysisJob>(`/v2/stories/${storyId}/analysis/jobs/${jobId}`, { method: "GET" }),
      listReports: async (storyId: string): Promise<V2AnalysisReport[]> =>
        requestJson<V2AnalysisReport[]>(`/v2/stories/${storyId}/analysis/reports`, { method: "GET" }),
      getReport: async (storyId: string, reportId: string): Promise<V2AnalysisReport> =>
        requestJson<V2AnalysisReport>(`/v2/stories/${storyId}/analysis/reports/${reportId}`, { method: "GET" }),
      listIssues: async (storyId: string): Promise<V2ContinuityIssue[]> =>
        requestJson<V2ContinuityIssue[]>(`/v2/stories/${storyId}/analysis/continuity-issues`, { method: "GET" }),
      updateIssue: async (storyId: string, issueId: string, payload: V2ContinuityIssueUpdateRequest): Promise<V2ContinuityIssue> =>
        requestJson<V2ContinuityIssue>(`/v2/stories/${storyId}/analysis/continuity-issues/${issueId}`, {
          method: "PUT",
          body: JSON.stringify(payload),
        }),
    },

    quality: {
      listRuns: async (manuscriptId: string, sceneId?: string): Promise<SlopQualityRun[]> => {
        const query = sceneId ? `?sceneId=${encodeURIComponent(sceneId)}` : "";
        const list = await requestJson<any[]>(`/v2/manuscripts/${manuscriptId}/quality-runs${query}`, { method: "GET" });
        return list.map(toSlopQualityRun);
      },
      analyzeScene: async (manuscriptId: string, sceneId: string): Promise<SlopQualityRun> => {
        const run = await requestJson<any>(`/v2/manuscripts/${manuscriptId}/scenes/${sceneId}/quality-runs`, {
          method: "POST",
          body: "{}",
        });
        return toSlopQualityRun(run);
      },
    },

    slopDrift: {
      listRuns: async (manuscriptId: string): Promise<SlopDriftRun[]> => {
        const list = await requestJson<any[]>(`/v2/manuscripts/${manuscriptId}/slop-drift-runs`, { method: "GET" });
        return list.map(toSlopDriftRun);
      },
      analyze: async (manuscriptId: string): Promise<SlopDriftRun> => {
        const run = await requestJson<any>(`/v2/manuscripts/${manuscriptId}/slop-drift-runs`, {
          method: "POST",
          body: "{}",
        });
        return toSlopDriftRun(run);
      },
    },

    plotQuality: {
      listRuns: async (manuscriptId: string, sceneId?: string): Promise<PlotQualityRun[]> => {
        const query = sceneId ? `?sceneId=${encodeURIComponent(sceneId)}` : "";
        const list = await requestJson<any[]>(`/v2/manuscripts/${manuscriptId}/plot-quality-runs${query}`, { method: "GET" });
        return list.map(toPlotQualityRun);
      },
      analyzeScene: async (manuscriptId: string, sceneId: string): Promise<PlotQualityRun> => {
        const run = await requestJson<any>(`/v2/manuscripts/${manuscriptId}/scenes/${sceneId}/plot-quality-runs`, {
          method: "POST",
          body: "{}",
        });
        return toPlotQualityRun(run);
      },
      getTrend: async (manuscriptId: string): Promise<PlotQualityTrend> => {
        const trend = await requestJson<any>(`/v2/manuscripts/${manuscriptId}/plot-quality-trends`, { method: "GET" });
        return toPlotQualityTrend(trend);
      },
      generateRevisionCandidate: async (manuscriptId: string, runId: string): Promise<PlotQualityRun> => {
        const run = await requestJson<any>(`/v2/manuscripts/${manuscriptId}/plot-quality-runs/${runId}/revision-candidate`, {
          method: "POST",
          body: "{}",
        });
        return toPlotQualityRun(run);
      },
      applyRevision: async (manuscriptId: string, runId: string): Promise<PlotQualityRun> => {
        const run = await requestJson<any>(`/v2/manuscripts/${manuscriptId}/plot-quality-runs/${runId}/apply-revision`, {
          method: "POST",
          body: "{}",
        });
        return toPlotQualityRun(run);
      },
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
