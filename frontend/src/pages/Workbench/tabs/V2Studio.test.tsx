import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import V2Studio from "./V2Studio";

describe("V2Studio historical analysis", () => {
  it("keeps history access without retired analysis triggers", () => {
    render(<V2Studio />);

    expect(screen.queryByRole("button", { name: /触发分析/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /连续性检查/ })).toBeNull();
    expect(screen.getByRole("button", { name: /分析任务/ })).toBeTruthy();
  });
});
