import { describe, expect, it, vi } from "vitest";
import { buildSsoCallbackRedirectUrl, createSsoCallbackProcessor } from "@/lib/sso-callback";

describe("sso callback processor", () => {
  it("preserves the exact redirect and nested query/Chinese/fragment through exchange", async () => {
    const next = "/workbench?storyId=a&outlineId=b&title=明日来信#scene-2";
    const encoded = encodeURIComponent(next);
    const search = `?next=${encoded}&code=c&state=s`;
    const redirect = `https://localainovel.testhut.top/sso/callback?next=${encoded}`;
    expect(buildSsoCallbackRedirectUrl("https://localainovel.testhut.top", "/sso/callback", search)).toBe(redirect);
    const onSuccess = vi.fn();
    await createSsoCallbackProcessor({ validateState: () => true, exchangeSsoCode: async () => ({ accessToken: "t" }), acceptToken: async () => {}, onSuccess, onFailure: vi.fn() })({ search, redirect });
    expect(onSuccess).toHaveBeenCalledWith(next);
  });
  it.each(["//evil.test", "/\\evil.test", "https://evil.test"])("rejects offsite next %s", async (next) => {
    const onSuccess = vi.fn();
    await createSsoCallbackProcessor({ validateState: () => true, exchangeSsoCode: async () => ({ accessToken: "t" }), acceptToken: async () => {}, onSuccess, onFailure: vi.fn() })({ search: `?code=c&state=s&next=${encodeURIComponent(next)}`, redirect: "https://localainovel.testhut.top/sso/callback" });
    expect(onSuccess).toHaveBeenCalledWith("/dashboard");
  });
  it("rebuilds the token-exchange redirect without re-encoding the next path slash", () => {
    const redirect = buildSsoCallbackRedirectUrl(
      "https://localainovel.testhut.top",
      "/sso/callback",
      "?next=/workbench&code=code-1&state=state-1",
    );

    expect(redirect).toBe("https://localainovel.testhut.top/sso/callback?next=/workbench");
  });

  it("handles a callback URL only once when React effects re-run", async () => {
    const validateState = vi.fn().mockReturnValueOnce(true).mockReturnValueOnce(false);
    const exchangeSsoCode = vi.fn().mockResolvedValue({ accessToken: "token-1" });
    const acceptToken = vi.fn().mockResolvedValue(undefined);
    const onSuccess = vi.fn();
    const onFailure = vi.fn();
    const processor = createSsoCallbackProcessor({
      validateState,
      exchangeSsoCode,
      acceptToken,
      onSuccess,
      onFailure,
    });

    const callback = {
      search: "?next=/workbench&code=code-1&state=state-1",
      redirect: "https://localainovel.testhut.top/sso/callback?next=/workbench",
    };

    await processor(callback);
    await processor(callback);

    expect(validateState).toHaveBeenCalledTimes(1);
    expect(exchangeSsoCode).toHaveBeenCalledTimes(1);
    expect(exchangeSsoCode).toHaveBeenCalledWith("code-1", callback.redirect);
    expect(acceptToken).toHaveBeenCalledTimes(1);
    expect(acceptToken).toHaveBeenCalledWith("token-1");
    expect(onSuccess).toHaveBeenCalledWith("/workbench");
    expect(onFailure).not.toHaveBeenCalled();
  });

  it("defaults callbacks without an explicit next path to the creation dashboard", async () => {
    const onSuccess = vi.fn();
    const processor = createSsoCallbackProcessor({
      validateState: vi.fn().mockReturnValue(true),
      exchangeSsoCode: vi.fn().mockResolvedValue({ accessToken: "token-1" }),
      acceptToken: vi.fn().mockResolvedValue(undefined),
      onSuccess,
      onFailure: vi.fn(),
    });

    await processor({
      search: "?code=code-1&state=state-1",
      redirect: "https://localainovel.testhut.top/sso/callback",
    });

    expect(onSuccess).toHaveBeenCalledWith("/dashboard");
  });
});
