import { describe, it, expect, vi, beforeEach } from "vitest";
import { api } from "@/lib/mock-api";

describe("mock api", () => {
  beforeEach(() => {
    const store: Record<string, string> = {};
    vi.stubGlobal("localStorage", {
      getItem: (k: string) => (k in store ? store[k] : null),
      setItem: (k: string, v: string) => {
        store[k] = String(v);
      },
      removeItem: (k: string) => {
        delete store[k];
      },
      clear: () => {
        Object.keys(store).forEach((k) => delete store[k]);
      },
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: any, init?: any) => {
        const u = String(url);
        if (u.endsWith("/api/v1/user/profile")) {
          expect(init?.headers?.get?.("Authorization") || init?.headers?.Authorization).toContain("Bearer t");
          return new Response(
            JSON.stringify({
              id: "u1",
              username: "admin",
              email: "admin@example.com",
              role: "admin",
              credits: 999,
              isBanned: false,
              lastCheckIn: null,
            }),
            { status: 200, headers: { "content-type": "application/json" } }
          );
        }
        return new Response("Not Found", { status: 404 });
      })
    );
  });

  it("reads profile from token in localStorage", async () => {
    localStorage.setItem("token", "t");
    const user = await api.user.getProfile();
    expect(user.role).toBe("admin");
    expect(user.username).toBe("admin");
  });

  it("loads AI models from v1 ai-service endpoint only", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v2/models")) {
        return new Response(
          JSON.stringify([{ id: "legacy-gpt", modelKey: "gpt-4o", displayName: "GPT-4o" }]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      if (u.endsWith("/api/v1/ai/models")) {
        return new Response(
          JSON.stringify([{ id: "deepseek-v4-flash", name: "deepseek-v4-flash", displayName: "DeepSeek V4 Flash", modelType: "text" }]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const models = await api.ai.getModels();

    expect(models).toHaveLength(1);
    expect(models[0].id).toBe("deepseek-v4-flash");
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("/api/v2/models"), expect.anything());
  });

  it("normalizes conception planning payload for plot planner flow", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: unknown) => {
        const u = String(url);
        if (u.endsWith("/api/v1/conception")) {
          return new Response(
            JSON.stringify({
              storyCard: {
                id: "story-1",
                title: "雾港迷局",
                synopsis: "一个失忆调查员发现自己在追查的目标可能就是未来的自己。",
                genre: "mystery",
                tone: "dark",
                status: "draft",
                updatedAt: "2026-04-10T00:00:00Z",
              },
              generatedAt: "2026-04-10T00:00:00Z",
              generated: {
                skeleton: {
                  corePromise: "追查越深入，越发现真相指向主角自己。",
                  centralQuestion: "主角究竟在追谁？",
                  hiddenTruth: "目标其实是另一时间线的自己。",
                  readerExpectation: "读者会先以为幕后黑手是外部组织。",
                },
                twistOptions: [
                  {
                    id: "twist-intuition",
                    label: "保留灵感版",
                    summary: "维持强直觉反转",
                    setupPoints: ["异常口供", "伪造监控"],
                    misdirectionPoints: ["组织阴谋", "可信证人"],
                    revealTiming: "高潮节点",
                    earlyRevealRisk: "线索过于集中会被看穿",
                    payoff: "前文违和感统一回收",
                  },
                ],
                foreshadowSeeds: [
                  {
                    entryKey: "foreshadow-signal",
                    setup: "案发现场的熟悉感",
                    coverLayer: "被当成职业直觉",
                    payoff: "最终证明来自时间错位记忆",
                  },
                ],
                memeStrategy: {
                  sourceDomain: "悬疑黑色幽默梗",
                  useCase: "缓冲压抑氛围",
                  naturalVersion: "只在配角对话里轻描淡写提及",
                  conservativeVersion: "压成彩蛋",
                  immersionRisk: "太频繁会让沉浸感断裂",
                },
                outlineSuggestion: {
                  title: "剧情骨架方案",
                  planning: { selectedTwistId: "twist-intuition" },
                  chapters: [],
                },
              },
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          );
        }
        return new Response("Not Found", { status: 404 });
      }),
    );

    const result = await api.stories.conception({ title: "雾港迷局", synopsis: "核心创意", genre: "mystery", tone: "dark" });
    expect(result.storyCard?.id).toBe("story-1");
    expect(result.plotPlanning?.corePromise).toContain("追查越深入");
    expect(result.plotPlanning?.twistOptions[0]?.setup).toEqual(["异常口供", "伪造监控"]);
    expect(result.plotPlanning?.foreshadowPlans[0]?.clue).toContain("案发现场");
    expect(result.plotPlanning?.selectedTwistId).toBe("twist-intuition");
  });

  it("keeps material search as chunk-level results without detail fetches", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v1/materials/search")) {
        return new Response(
          JSON.stringify([
            {
              materialId: "m1",
              chunkId: "m1-0",
              title: "旧报纸摘录",
              snippet: "陆家码头在雨夜停用，船灯在雾里闪了三次。",
              score: 4.4,
              chunkSeq: 0,
              source: "keyword",
              matchReasons: ["title", "content"],
            },
          ]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const results = await api.materials.search("陆家码头");

    expect(results[0]).toMatchObject({
      materialId: "m1",
      chunkId: "m1-0",
      source: "keyword",
      matchReasons: ["title", "content"],
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("normalizes plot quality runs and trend payloads", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v2/manuscripts/m1/plot-quality-runs?sceneId=s1")) {
        return new Response(
          JSON.stringify([
            {
              id: "run-1",
              storyId: "story-1",
              manuscriptId: "m1",
              sceneId: "s1",
              chapterTitle: "第一章",
              sceneTitle: "雨夜门外",
              chapterOrder: 1,
              sceneOrder: 2,
              status: "ACCEPTED_WITH_ISSUES",
              maxSeverity: "HIGH",
              overallRiskScore: 82,
              summary: "角色动机跳变",
              rewritePlanJson: "[\"补动机\"]",
              surgicalFixesJson: "[\"补一拍权衡\"]",
              revisionCandidateText: "修订候选",
              revisionApplied: false,
              issues: [{ id: "i1", dimension: "AGENCY", severity: "HIGH", riskScore: 82 }],
            },
          ]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      if (u.endsWith("/api/v2/manuscripts/m1/plot-quality-trends")) {
        return new Response(
          JSON.stringify({
            manuscriptId: "m1",
            averageRisk: 62,
            highRiskScenes: 1,
            dimensionCounts: { AGENCY: 1 },
            points: [{ runId: "run-1", sceneId: "s1", riskScore: 82, chapterOrder: 1, sceneOrder: 2 }],
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const runs = await api.v2.plotQuality.listRuns("m1", "s1");
    const trend = await api.v2.plotQuality.getTrend("m1");

    expect(runs[0].issues[0].dimension).toBe("AGENCY");
    expect(runs[0].rewritePlan).toEqual(["补动机"]);
    expect(trend.points[0].riskScore).toBe(82);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("exposes material duplicate and citation API contracts", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v1/materials/find-duplicates")) {
        return new Response(
          JSON.stringify([
            {
              sourceMaterialId: "m1",
              targetMaterialId: "m2",
              sourceTitle: "陆家码头旧报",
              targetTitle: "陆家码头档案",
              score: 0.82,
              reasons: ["title", "tags"],
            },
          ]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      if (u.endsWith("/api/v1/materials/m1/citations")) {
        return new Response(
          JSON.stringify([
            {
              materialId: "m1",
              storyId: "s1",
              storyTitle: "雨港迷案",
              manuscriptId: "ms1",
              sceneId: "scene1",
              chapterTitle: "第一章",
              sceneTitle: "旧码头",
              snippet: "陆家码头的雨夜发现旧报。",
              reason: "tag:陆家码头",
            },
          ]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const duplicates = await api.materials.findDuplicates();
    const citations = await api.materials.getCitations("m1");

    expect(duplicates[0].score).toBe(0.82);
    expect(duplicates[0].reasons).toEqual(["title", "tags"]);
    expect(citations[0].sceneTitle).toBe("旧码头");
    expect(citations[0].snippet).toContain("陆家码头");
  });

  it("exposes prompt reset and metadata API contracts", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v1/prompt-templates/reset")) {
        return new Response(
          JSON.stringify({
            storyCreation: "默认故事",
            outlineChapter: "默认章节",
            manuscriptSection: "默认正文",
            refineWithInstruction: "默认指令润色",
            refineWithoutInstruction: "默认润色",
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      if (u.endsWith("/api/v1/prompt-templates/metadata")) {
        return new Response(
          JSON.stringify({
            syntaxTips: [{ name: "插值", description: "使用 {variable}" }],
            functions: [{ name: "timeline", description: "输出时间线", example: "{{fn:timeline storyId}}" }],
            templates: [{ key: "storyCreation", variables: [{ name: "idea", type: "string", description: "创意" }] }],
            examples: ["示例"],
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      if (u.endsWith("/api/v1/world-prompts/reset")) {
        return new Response(
          JSON.stringify({ modules: { geography: "默认地理" }, finalTemplates: {}, fieldRefine: "默认精修" }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      if (u.endsWith("/api/v1/world-prompts/metadata")) {
        return new Response(
          JSON.stringify({
            variables: [{ name: "worldName", type: "string", description: "世界名称" }],
            functions: [],
            modules: [{ key: "geography", label: "地理环境", fields: [{ key: "terrain", label: "地形", maxLength: 150 }] }],
            examples: ["示例"],
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const resetWorkspace = await api.prompts.resetWorkspace();
    const workspaceMetadata = await api.prompts.getWorkspaceMetadata();
    const resetWorld = await api.prompts.resetWorld();
    const worldMetadata = await api.prompts.getWorldMetadata();

    expect(resetWorkspace.storyCreation).toBe("默认故事");
    expect(workspaceMetadata.templates[0].variables[0].name).toBe("idea");
    expect(resetWorld.modules.geography).toBe("默认地理");
    expect(worldMetadata.modules[0].fields[0].key).toBe("terrain");
  });
});
