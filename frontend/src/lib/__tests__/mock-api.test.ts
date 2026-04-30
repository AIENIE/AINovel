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
});
