import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { EvidenceReportView } from "./EvidenceReportView";
import type { EvidenceTask } from "@/lib/api/domains/material-evidence";
afterEach(cleanup);
describe("EvidenceReportView", () => {
  it("shows conditions and original evidence without exposing raw JSON", () => {
    const task: EvidenceTask = { id: "task", kind: "CHECK", status: "COMPLETED", errorCode: null, stale: false, externalLimit: 24, callsReserved: 1, result: {
      report: { coverage: "仅检查已打开资料", findings: [{ kind: "FACT", claim: "通行时间有矛盾", decision: "CONTRADICTED", condition: "日落后", evidence: [{ sourceId: "source", quote: "日落后才通行", start: 0, end: 6 }] }] },
      opened: [{ id: "source", kind: "REFERENCE", text: "日落后才通行", revisionId: "revision", bodyVersion: null, sceneId: null, start: 9, conversionVersion: "raw-codepoint-v1" }], exclusions: ["未命中不证明不存在"],
    } };
    const view = render(<EvidenceReportView task={task} />);
    expect(screen.getByText(/世界事实 · 存在矛盾/)).toBeTruthy(); expect(screen.getByText("通行时间有矛盾")).toBeTruthy(); expect(screen.getAllByText("日落后才通行").length).toBeGreaterThan(0);
    expect(view.container.querySelector("pre")).toBeNull(); expect(view.container.textContent).toContain("9"); expect(view.container.textContent).toContain("15");
  });
  it("fails visibly when report structure is incomplete", () => {
    render(<EvidenceReportView task={{ id: "task", kind: "CHECK", status: "COMPLETED", errorCode: null, stale: false, externalLimit: 24, callsReserved: 1, result: { confirmed: true } }} />);
    expect(screen.getByRole("alert").textContent).toContain("报告格式不完整");
  });
});
