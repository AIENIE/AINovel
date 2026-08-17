import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import V2Studio from "./V2Studio";

describe("V2Studio unavailable analysis controls", () => {
  it("labels and disables Beta Reader and continuity triggers", () => {
    render(<V2Studio />);

    const betaReader = screen.getByRole("button", { name: /触发分析.*尚未实现/ });
    const continuity = screen.getByRole("button", { name: /连续性检查.*尚未实现/ });

    expect((betaReader as HTMLButtonElement).disabled).toBe(true);
    expect((continuity as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getAllByText(/尚未实现/).length).toBeGreaterThanOrEqual(3);
  });
});
