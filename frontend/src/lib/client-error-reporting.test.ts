import { describe, expect, it } from "vitest";
import { summarizeClientError } from "@/lib/client-error-reporting";

describe("client error reporting", () => {
  it("never includes the exception message", () => {
    const jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature123";
    const summary = summarizeClientError(
      new Error(`Bearer top-secret ${jwt} admin@example.com https://example.test/callback?token=secret-value`),
      "window.unhandledrejection",
    );

    expect(summary).toEqual({
      source: "window.unhandledrejection",
      errorType: "Error",
      count: 1,
    });
    expect(JSON.stringify(summary)).not.toContain("top-secret");
    expect(JSON.stringify(summary)).not.toContain("secret-value");
  });

  it("does not serialize arbitrary rejection objects", () => {
    const summary = summarizeClientError({ token: "do-not-log" }, "window.unhandledrejection");

    expect(summary.errorType).toBe("NonErrorRejection");
    expect(JSON.stringify(summary)).not.toContain("do-not-log");
  });

  it("rejects dynamic source and error type text", () => {
    const error = new Error("ignored");
    error.name = "Secret Error: password=hunter2";

    expect(summarizeClientError(error, "/private/path?token=secret")).toEqual({
      source: "unknown",
      errorType: "Error",
      count: 1,
    });
  });
});
