import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import AnalysisDashboard from "./AnalysisDashboard";

vi.mock("recharts", () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  return Object.fromEntries(["Radar", "RadarChart", "PolarGrid", "PolarAngleAxis", "ResponsiveContainer", "Line", "LineChart", "XAxis", "YAxis", "CartesianGrid", "Tooltip"].map(name => [name, Container]));
});
afterEach(() => vi.restoreAllMocks());
it("keeps historical reports readable without retired controls or invented missing scores", async () => {
  vi.spyOn(api.stories, "list").mockResolvedValue([{ id: "story", title: "历史作品" }] as never);
  vi.spyOn(api.outlines, "listByStory").mockResolvedValue([]);
  vi.spyOn(api.v2.analysis, "listJobs").mockResolvedValue([]);
  vi.spyOn(api.v2.analysis, "listIssues").mockResolvedValue([]);
  vi.spyOn(api.v2.analysis, "listReports").mockResolvedValue([{ id: "report", scope: "full", summary: "历史报告原文", scoreOverall: null, tokenCost: null }] as never);
  render(<AnalysisDashboard />);
  await waitFor(() => expect(screen.getByText("历史报告原文")).toBeTruthy());
  expect(screen.queryByText(/尚未实现/)).toBeNull();
  expect(screen.queryByRole("textbox")).toBeNull();
  expect(screen.getAllByText(/—/)).toHaveLength(2);
});
