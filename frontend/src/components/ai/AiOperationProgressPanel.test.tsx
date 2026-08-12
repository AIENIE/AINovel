import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AiOperationProgress } from "@/types";
import { AiOperationProgressPanel } from "./AiOperationProgressPanel";

const tracked = vi.hoisted(() => ({
  operation: null as AiOperationProgress | null,
  resume: vi.fn(),
  retry: vi.fn(),
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ isAuthenticated: true }),
}));

vi.mock("@/lib/ai-operation-store", () => ({
  resumeTrackedAiOperation: tracked.resume,
  retryTrackedAiOperation: tracked.retry,
  useTrackedAiOperation: () => tracked.operation,
}));

const operation = (overrides: Partial<AiOperationProgress> = {}): AiOperationProgress => ({
  id: "operation-1",
  operationType: "CORE_AI",
  status: "STREAMING",
  currentStep: "正在构建章节",
  totalSteps: 5,
  completedSteps: 2,
  remainingSteps: 3,
  currentStepOutputTokens: 1024,
  outputTokensEstimated: true,
  attemptCount: 1,
  ...overrides,
});

describe("AiOperationProgressPanel", () => {
  beforeEach(() => {
    tracked.operation = null;
    tracked.resume.mockReset();
    tracked.retry.mockReset();
  });

  it("shows the current step, completed and remaining steps, and streamed token count", async () => {
    tracked.operation = operation();

    render(<AiOperationProgressPanel />);

    expect(screen.getByText("正在构建章节")).toBeTruthy();
    expect(screen.getByText("已完成 2 步 · 剩余 3 步")).toBeTruthy();
    expect(screen.getByText((content: string) => content.includes("1,024") && content.includes("token"))).toBeTruthy();
    expect(screen.getByText((content: string) => content.includes("（估算）"))).toBeTruthy();
    expect(screen.getByText("2/5")).toBeTruthy();
    await waitFor(() => expect(tracked.resume).toHaveBeenCalledTimes(1));
  });

  it("keeps failed operations visible with a localized generic failure text and retries the tracked operation", () => {
    // 后端原始 errorMessage 不直接展示，映射为本地化通用文案
    tracked.operation = operation({ status: "FAILED", errorMessage: "AI 服务暂时不可用" });

    render(<AiOperationProgressPanel />);

    expect(screen.getByText("操作失败，请稍后重试")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(tracked.retry).toHaveBeenCalledWith("operation-1");
  });
});
