import { describe, expect, it, vi } from "vitest";
import { buildSsoCallbackRedirectUrl, createSsoCallbackProcessor } from "@/lib/sso-callback";

describe("sso callback processor", () => {
  it("rebuilds the token-exchange redirect without re-encoding the next path slash", () => {
    const redirect = buildSsoCallbackRedirectUrl(
      "https://ainovel.localhut.com",
      "/sso/callback",
      "?next=/workbench&code=code-1&state=state-1",
    );

    expect(redirect).toBe("https://ainovel.localhut.com/sso/callback?next=/workbench");
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
      redirect: "https://ainovel.localhut.com/sso/callback?next=/workbench",
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
});
