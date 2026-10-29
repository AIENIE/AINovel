import { describe, it, expect, vi, beforeEach } from "vitest";
import { ApiError, api, normalizeConceptionResult } from "@/lib/api-client";
import { registerAdminOperationCodePrompt } from "@/lib/admin-operation-proof";
import type { GenerationRunSummary } from "@/types";

describe("api client", () => {
  it.each([null, "old-session"])("ignores a delayed 401 for %s after a new login", async (oldToken) => {
    if (oldToken) localStorage.setItem("token", oldToken);
    let finish!: (response: Response) => void;
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>(resolve => { finish = resolve; })));
    const request = api.user.getProfile().catch(error => error);
    localStorage.setItem("token", "new-session");
    finish(new Response(JSON.stringify({ message: "SESSION_INVALID" }), { status: 401 }));
    expect(await request).toBeInstanceOf(ApiError);
    expect(localStorage.getItem("token")).toBe("new-session");
    expect(globalThis.location.assign).not.toHaveBeenCalled();
  });
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
      vi.fn(async (url: unknown, init?: RequestInit) => {
        const u = String(url);
        if (u.endsWith("/api/v1/user/profile")) {
          const headers = init?.headers as Headers | undefined;
          expect(headers?.get("Authorization")).toContain("Bearer t");
          return new Response(
            JSON.stringify({
              id: "u1",
              username: "admin",
              email: "admin@example.com",
              role: "admin",
              credits: 999,
              isBanned: false,
            }),
            { status: 200, headers: { "content-type": "application/json" } }
          );
        }
        return new Response("Not Found", { status: 404 });
      })
    );
    vi.stubGlobal("location", {
      pathname: "/workbench",
      search: "?tab=quality",
      hash: "",
      assign: vi.fn(),
    });
  });

  it.each([403, 503])("preserves user credentials and location on %s", async (status) => {
    localStorage.setItem("token", "t");
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ message: "unavailable" }), { status })));
    await expect(api.user.getProfile()).rejects.toBeInstanceOf(ApiError);
    expect(localStorage.getItem("token")).toBe("t");
    expect(globalThis.location.assign).not.toHaveBeenCalled();
  });

  it("preserves scene length bounds through outline load and save", async () => {
    const dto = { id: "outline-1", title: "短篇", chapters: [{ id: "chapter-1", title: "第一章", scenes: [{ id: "scene-1", title: "来信", planning: { minHan: 600, maxHan: 900 } }] }] };
    const fetchMock = vi.fn(async (_url: unknown, init?: RequestInit) => {
      if (init?.method === "PUT") {
        expect(JSON.parse(String(init.body)).chapters[0].scenes[0].planning).toMatchObject({ minHan: 600, maxHan: 900 });
        return new Response(JSON.stringify(dto));
      }
      return new Response(JSON.stringify([dto]));
    });
    vi.stubGlobal("fetch", fetchMock);
    const [outline] = await api.outlines.listByStory("story-1");
    expect(outline.chapters[0].scenes[0].planning).toMatchObject({ minHan: 600, maxHan: 900 });
    const saved = await api.outlines.save(outline.id, outline);
    expect(saved.chapters[0].scenes[0].planning).toMatchObject({ minHan: 600, maxHan: 900 });
  });

  it("reads profile from token in localStorage", async () => {
    localStorage.setItem("token", "t");
    const user = await api.user.getProfile();
    expect(user.role).toBe("admin");
    expect(user.username).toBe("admin");
  });

  it("accepts empty 204 responses for every v1 delete endpoint", async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await api.stories.deleteCharacter("character-1");
    await api.stories.delete("story-1");
    await api.outlines.delete("outline-1");
    await api.worlds.delete("world-1");

    expect(fetchMock).toHaveBeenCalledTimes(4);
    for (const [, init] of fetchMock.mock.calls) {
      expect(init?.method).toBe("DELETE");
    }
  });

  it("normalizes raw async conception results before they are cached for the workbench", () => {
    const result = normalizeConceptionResult({
      storyCard: { id: "story-1", title: "雾港迷局" },
      generated: {
        skeleton: {
          corePromise: "追查越深入，越发现真相指向主角自己。",
          centralQuestion: "主角究竟在追谁？",
        },
        twistOptions: [{ summary: "维持强直觉反转", setupPoints: ["异常口供"] }],
        foreshadowSeeds: [{ setup: "案发现场的熟悉感", payoff: "最终证明来自时间错位记忆" }],
        outlineSuggestion: { title: "剧情骨架方案", chapters: [] },
      },
    });

    expect(result.plotPlanning?.twistOptions).toHaveLength(1);
    expect(result.plotPlanning?.foreshadowPlans).toHaveLength(1);
    expect(result.outlineSeed).toMatchObject({ title: "剧情骨架方案", chapters: [] });
  });

  it("throws typed user auth errors and redirects to login on 401", async () => {
    localStorage.setItem("token", "t");
    const assign = vi.fn();
    vi.stubGlobal("location", {
      pathname: "/workbench",
      search: "?tab=quality",
      hash: "",
      assign,
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: unknown) => {
        const u = String(url);
        if (u.endsWith("/api/v1/user/profile")) {
          return new Response(JSON.stringify({ message: "未登录" }), {
            status: 401,
            headers: { "content-type": "application/json" },
          });
        }
        return new Response("Not Found", { status: 404 });
      }),
    );

    const error = await api.user.getProfile().catch((err) => err);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.message).toBe("未登录");
    expect(localStorage.getItem("token")).toBeNull();
    expect(assign).toHaveBeenCalledWith("/login?next=%2Fworkbench%3Ftab%3Dquality");
  });

  it("redirects to admin login on 403 without persisting an admin token", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", {
      pathname: "/admin/dashboard",
      search: "?panel=ops",
      hash: "",
      assign,
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: unknown) => {
        const u = String(url);
        if (u.endsWith("/api/v1/admin/dashboard")) {
          return new Response(JSON.stringify({ message: "无管理员权限" }), {
            status: 403,
            headers: { "content-type": "application/json" },
          });
        }
        return new Response("Not Found", { status: 404 });
      }),
    );

    const error = await api.admin.getDashboardStats().catch((err) => err);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(403);
    expect(localStorage.getItem("admin_token")).toBeNull();
    expect(assign).toHaveBeenCalledWith("/admin/login?next=%2Fadmin%2Fdashboard%3Fpanel%3Dops");
  });

  it("answers an admin 428 challenge and retries exactly once with the scoped proof", async () => {
    const requests: Array<{ url: string; init?: RequestInit }> = [];
    const unregister = registerAdminOperationCodePrompt(async () => "123456");
    vi.stubGlobal("fetch", vi.fn(async (url: unknown, init?: RequestInit) => {
      requests.push({ url: String(url), init });
      if (String(url).endsWith("/api/v1/admin-auth/operation-proofs/verify")) {
        return new Response(JSON.stringify({ proofToken: "one-use-proof" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }
      const headers = init?.headers as Headers;
      if (headers.get("X-Admin-Operation-Proof") === "one-use-proof") {
        return new Response(JSON.stringify({ maintenance: true }), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }
      return new Response(JSON.stringify({ challengeId: "operation-1" }), {
        status: 428,
        headers: { "content-type": "application/json" },
      });
    }));

    try {
      await expect(api.admin.updateSystemConfig({ maintenance: true })).resolves.toEqual({ maintenance: true });
    } finally {
      unregister();
    }

    expect(requests).toHaveLength(3);
    expect(requests[0].init?.credentials).toBe("same-origin");
    expect(JSON.parse(String(requests[1].init?.body))).toEqual({ challengeId: "operation-1", code: "123456" });
    expect((requests[2].init?.headers as Headers).get("X-Admin-Operation-Proof")).toBe("one-use-proof");
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

  it("preserves unassessed scores and an empty trend as null", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: unknown) => new Response(JSON.stringify(
      String(url).includes("trends")
        ? { manuscriptId: "m1", averageRisk: null, highRiskScenes: 0, dimensionCounts: {}, points: [] }
        : [{ id: "failed", storyId: "s1", manuscriptId: "m1", sceneId: "s1", status: "DEGRADED",
            maxSeverity: null, overallRiskScore: null, issues: [] }]
    ), { status: 200 })));
    expect((await api.v2.plotQuality.listRuns("m1"))[0].overallRiskScore).toBeNull();
    expect((await api.v2.plotQuality.listRuns("m1"))[0].maxSeverity).toBeNull();
    expect((await api.v2.plotQuality.getTrend("m1")).averageRisk).toBeNull();
  });

  it.each(["{bad}", "[]", "{} {}"])("does not turn corrupt report objects into empty data: %s", async (json) => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([{
      id: "r1", storyId: "s1", manuscriptId: "m1", sceneId: "s1", status: "ACCEPTED",
      maxSeverity: "LOW", overallRiskScore: 0, moduleScoresJson: json, issues: [],
    }]), { status: 200 })));
    await expect(api.v2.quality.listRuns("m1", "s1")).rejects.toThrow();
  });

  it("normalizes manual slop diagnosis payloads", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v2/manuscripts/m1/quality-runs?sceneId=s1")) {
        return new Response(
          JSON.stringify([{
            id: "slop-run-1",
            storyId: "story-1",
            manuscriptId: "m1",
            sceneId: "s1",
            status: "ACCEPTED_WITH_ISSUES",
            maxSeverity: "HIGH",
            overallRiskScore: 72,
            revised: false,
            revisionCount: 0,
            analysisMode: "manual_scene",
            riskLabel: "high",
            evidenceLevel: "E2",
            safeClaim: "该文本呈现较高模板化风险，但不能证明作者使用 AI。",
            moduleScoresJson: "{\"surface_template\":{\"score\":78}}",
            alternativeExplanationsJson: "[\"传统网文俗套\"]",
            revisionPrioritiesJson: "[\"先修共振片段\"]",
            rewriteTasksJson: "[{\"task_id\":\"R1\",\"problem\":\"模板句密集\",\"repair_goal\":\"换成具体动作\"}]",
            issues: [
              {
                id: "e1",
                dimension: "GENERICITY",
                severity: "HIGH",
                riskScore: 72,
                evidence: "空气仿佛凝固",
                charStart: 4,
                charEnd: 10,
                module: "surface_template",
                patternId: "SURFACE_GENERIC_001",
                issueType: "phrase_pattern",
                evidenceLevel: "E2",
                alternativeExplanationsJson: "[\"传统网文俗套\"]",
                repairHint: "换成具体动作",
              },
            ],
          }]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const [run] = await api.v2.quality.listRuns("m1", "s1");

    expect(run.evidenceLevel).toBe("E2");
    expect(run.moduleScores.surface_template).toEqual({ score: 78 });
    expect(run.rewriteTasks[0].task_id).toBe("R1");
    expect(run.issues[0].charStart).toBe(4);
    expect(run.issues[0].alternativeExplanations).toEqual(["传统网文俗套"]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("normalizes slop drift run payloads", async () => {
    const fetchMock = vi.fn(async (url: unknown) => {
      const u = String(url);
      if (u.endsWith("/api/v2/manuscripts/m1/slop-drift-runs")) {
        return new Response(
          JSON.stringify([{
            id: "drift-run-1",
            storyId: "story-1",
            manuscriptId: "m1",
            status: "COMPLETED",
            overallRiskScore: 76,
            riskLabel: "high",
            safeClaim: "该稿件呈现叙事机制断层风险；这不能证明作者使用 AI。",
            summary: "中后段模板化和事件传送带风险升高。",
            totalCharacters: 25000,
            windowCount: 3,
            sourceTextHash: "abc",
            windowSummariesJson: "[{\"label\":\"opening\",\"summary\":\"开头具体\"}]",
            metricCurvesJson: "{\"template_density\":[{\"window\":\"opening\",\"score\":24}]}",
            driftPointsJson: "[{\"from_window\":\"opening\",\"to_window\":\"latest\"}]",
            evidenceItemsJson: "[{\"window\":\"latest\",\"module\":\"breath_focus_pacing\"}]",
            alternativeExplanationsJson: "[\"赶稿\"]",
            rewriteTasksJson: "[{\"task_id\":\"D1\",\"problem\":\"后段事件传送带\"}]",
            createdAt: "2026-06-17T00:00:00Z",
          }]),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const [run] = await api.v2.slopDrift.listRuns("m1");

    expect(run.riskLabel).toBe("high");
    expect(run.windowSummaries[0]).toEqual({ label: "opening", summary: "开头具体" });
    expect(run.metricCurves.template_density).toEqual([{ window: "opening", score: 24 }]);
    expect(run.driftPoints[0].to_window).toBe("latest");
    expect(run.evidenceItems[0].module).toBe("breath_focus_pacing");
    expect(run.alternativeExplanations).toEqual(["赶稿"]);
    expect(run.rewriteTasks[0].task_id).toBe("D1");
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

  it("passes admin list search and pagination parameters through the API wrapper", async () => {
    const requestedUrls: string[] = [];
    const fetchMock = vi.fn(async (url: unknown, init?: RequestInit) => {
      requestedUrls.push(String(url));
      const headers = init?.headers as Headers;
      expect(headers?.get("Authorization")).toBeNull();
      expect(init?.credentials).toBe("same-origin");
      if (String(url).includes("/api/v1/admin/users")) {
        return new Response(JSON.stringify([]), { status: 200, headers: { "content-type": "application/json" } });
      }
      if (String(url).includes("/api/v1/admin/credits/conversions")) {
        return new Response(JSON.stringify([]), { status: 200, headers: { "content-type": "application/json" } });
      }
      if (String(url).includes("/api/v1/admin/credits/ledger")) {
        return new Response(JSON.stringify([]), { status: 200, headers: { "content-type": "application/json" } });
      }
      return new Response("Not Found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.admin.getUsers(" goodboy95 ");
    await api.admin.listConversionOrders(2, 15);
    await api.admin.listCreditLedger(3, 25);

    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/users?search=goodboy95"))).toBe(true);
    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/credits/conversions?page=2&size=15"))).toBe(true);
    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/credits/ledger?page=3&size=25"))).toBe(true);
  });

  it("passes admin ops observability requests through the API wrapper", async () => {
    const requestedUrls: string[] = [];
    const fetchMock = vi.fn(async (url: unknown, init?: RequestInit) => {
      requestedUrls.push(String(url));
      const headers = init?.headers as Headers;
      expect(headers?.get("Authorization")).toBeNull();
      expect(init?.credentials).toBe("same-origin");
      return new Response(JSON.stringify({ items: [], total: 0 }), { status: 200, headers: { "content-type": "application/json" } });
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.admin.getOpsSummary();
    await api.admin.listDependencies();
    await api.admin.listOpsAlerts();
    await api.admin.getOpsDiagnostics();

    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/ops/summary"))).toBe(true);
    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/ops/dependencies"))).toBe(true);
    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/ops/alerts"))).toBe(true);
    expect(requestedUrls.some((url) => url.endsWith("/api/v1/admin/ops/diagnostics"))).toBe(true);
  });

  it("uses the durable guided creation workflow endpoints", async () => {
    const requests: Array<{ url: string; init?: RequestInit }> = [];
    const workflow = {
      id: "run-1",
      templateKey: "quick-book-v1",
      status: "WAITING_USER",
      currentStep: "PREMISE",
      seedIdea: "停摆城市",
      targetChapterCount: 6,
      autoRun: false,
      steps: {},
      version: 0,
    };
    vi.stubGlobal("fetch", vi.fn(async (url: unknown, init?: RequestInit) => {
      requests.push({ url: String(url), init });
      const body = String(url).endsWith("/generate")
        ? { workflowId: "run-1", jobId: "job-1" }
        : workflow;
      return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
    }));

    await api.creationWorkflows.create({
      seedIdea: "停摆城市", genre: "悬疑", tone: "紧张", targetChapterCount: 6, autoRun: false,
    });
    await api.creationWorkflows.generate("run-1", "PREMISE");
    await api.creationWorkflows.confirm("run-1", "PREMISE", "candidate-1", { candidateId: "candidate-1", title: "停摆" }, 3);
    await api.creationWorkflows.developOutlineDirection(
      "run-1", "direction-a", "rewrite", "加强主动选择", { candidateId: "direction-a" }, 5,
    );
    await api.creationWorkflows.expandOutlineDirection(
      "run-1", "direction-a", { candidateId: "direction-a" }, 6,
    );
    await api.creationWorkflows.skipWorld("run-1", 4);
    await api.creationWorkflows.startAuto("run-1", 8);
    await api.creationWorkflows.retry("run-1");

    expect(requests.map((item) => item.url)).toEqual(expect.arrayContaining([
      "/api/v1/creation-workflows",
      "/api/v1/creation-workflows/run-1/steps/premise/generate",
      "/api/v1/creation-workflows/run-1/steps/premise/confirm",
      "/api/v1/creation-workflows/run-1/steps/outline/directions/direction-a/develop",
      "/api/v1/creation-workflows/run-1/steps/outline/directions/direction-a/expand",
      "/api/v1/creation-workflows/run-1/steps/world/skip?version=4",
      "/api/v1/creation-workflows/run-1/auto-run",
      "/api/v1/creation-workflows/run-1/retry",
    ]));
    const confirm = requests.find((item) => item.url.endsWith("/confirm"));
    expect(JSON.parse(String(confirm?.init?.body))).toMatchObject({ candidateId: "candidate-1", version: 3 });
  });

  it("downloads an authenticated export blob and reads the response filename", async () => {
    localStorage.setItem("token", "download-token");
    const fetchMock = vi.fn(async (_url: unknown, init?: RequestInit) => {
      const headers = init?.headers as Headers;
      expect(headers.get("Authorization")).toBe("Bearer download-token");
      return new Response("novel content", {
        status: 200,
        headers: { "Content-Disposition": "attachment; filename*=UTF-8''%E5%B0%8F%E8%AF%B4.txt" },
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await api.v2.export.download("manuscript-1", "job-1");

    expect(result.fileName).toBe("小说.txt");
    expect(result.blob.size).toBeGreaterThan(0);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v2/manuscripts/manuscript-1/export/jobs/job-1/download",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("normalizes the latest generation reference on manuscripts", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([{
      id: "manuscript-1",
      outlineId: "outline-1",
      title: "正文",
      sections: {},
      version: 0,
      lastGenerationRun: {
        id: "run-9",
        generationVersionId: "version-9",
        status: "COMPLETED",
        createdAt: "2026-08-12T10:00:00Z",
      },
      updatedAt: "2026-08-12T10:00:00Z",
    }]), { status: 200, headers: { "content-type": "application/json" } })));

    const manuscripts = await api.manuscripts.listByOutline("outline-1");

    expect(manuscripts[0].lastGenerationRun).toEqual({
      id: "run-9",
      generationVersionId: "version-9",
      status: "COMPLETED",
      createdAt: "2026-08-12T10:00:00Z",
    });
  });

  it("lists scene generation runs and patches explicit feedback", async () => {
    const generationRun: GenerationRunSummary = {
      id: "run-1",
      manuscriptId: "manuscript-1",
      sceneId: "scene-2",
      createdBy: "user-1",
      mode: "crafted",
      status: "GENERATED",
      modelKey: "text-premium",
      promptVersion: "scene-draft-v2",
      attemptCount: 1,
      contextHash: "ctx-hash",
      contextManifest: {
        promptVersion: "scene-draft-v2",
        tokenBudget: 3500,
        tokenUsed: 840,
        sources: [{
          sourceType: "outline_scene",
          sourceId: "scene-2",
          label: "本场大纲",
          reason: "约束场景目标",
          estimatedTokens: 120,
          truncated: false,
        }],
      },
      generationVersionId: "version-1",
      previousRunId: null,
      firstEditedAt: null,
      lastEditedAt: null,
      addedCharacters: 0,
      deletedCharacters: 0,
      retentionRate: 1,
      recalculationPending: false,
      feedbackTags: [],
      feedbackNote: null,
      preferenceConfirmed: false,
      createdAt: "2026-08-12T10:00:00Z",
      updatedAt: "2026-08-12T10:00:00Z",
    };
    const requests: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", vi.fn(async (url: unknown, init?: RequestInit) => {
      requests.push({ url: String(url), init });
      const responseBody = init?.method === "PATCH"
        ? {
            ...generationRun,
            feedbackTags: ["PLOT_CAUSALITY"],
            feedbackNote: "人物声音可以保留",
            preferenceConfirmed: true,
          }
        : [generationRun];
      return new Response(JSON.stringify(responseBody), { status: 200, headers: { "content-type": "application/json" } });
    }));

    await api.manuscripts.listGenerationRuns("manuscript-1", "scene-2", 3);
    await api.manuscripts.updateGenerationRunFeedback("manuscript-1", "scene-2", "run-1", {
      tags: ["PLOT_CAUSALITY"],
      note: "人物声音可以保留",
      preferenceConfirmed: true,
    });

    expect(requests[0]).toMatchObject({
      url: "/api/v1/manuscripts/manuscript-1/scenes/scene-2/generation-runs?limit=3",
      init: expect.objectContaining({ method: "GET" }),
    });
    expect(requests[1].url).toBe("/api/v1/manuscripts/manuscript-1/scenes/scene-2/generation-runs/run-1/feedback");
    expect(requests[1].init?.method).toBe("PATCH");
    expect(JSON.parse(String(requests[1].init?.body))).toEqual({
      tags: ["PLOT_CAUSALITY"],
      note: "人物声音可以保留",
      preferenceConfirmed: true,
    });
  });

  it("scopes context preview to the active manuscript and scene", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      promptVersion: "scene-draft-v2",
      contextHash: "ctx-hash",
      tokenBudget: 3500,
      tokenUsed: 840,
      sources: [],
    }), { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await api.v2.context.previewContext("story-1", {
      manuscriptId: "manuscript-1",
      sceneId: "scene-2",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v2/stories/story-1/context/preview?manuscriptId=manuscript-1&sceneId=scene-2",
      expect.objectContaining({ method: "GET" }),
    );
  });
});
