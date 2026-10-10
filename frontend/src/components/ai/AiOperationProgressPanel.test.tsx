import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AiOperationProgress } from "@/types";
import { AiOperationProgressPanel } from "./AiOperationProgressPanel";
import { aiOperationProgressSchema } from "@/lib/api-contracts";

const tracked = vi.hoisted(() => ({
  operation: null as AiOperationProgress | null,
  resume: vi.fn(),
  retry: vi.fn(),
  visibility: "open",
  show: vi.fn(),
}));

vi.mock("@/contexts/auth-state", () => ({
  useAuth: () => ({ isAuthenticated: true, user: { id: "user-1" } }),
}));

vi.mock("@/lib/ai-operation-store", () => ({
  resumeTrackedAiOperation: tracked.resume,
  retryTrackedAiOperation: tracked.retry,
  useTrackedAiOperation: () => tracked.operation,
  useTrackedAiVisibility: () => tracked.visibility,
  setTrackedAiVisibility: tracked.show,
  setTrackedAiUser: vi.fn(),
  refreshTrackedAiOperation: vi.fn(),
  cancelTrackedAiOperation: vi.fn(),
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
    tracked.visibility = "open";
    tracked.show.mockReset();
    tracked.resume.mockReset();
    tracked.retry.mockReset();
  });

  it("closes only the display and exposes a reopen entry", () => {
    tracked.operation = operation({ status: "FAILED" });
    const { rerender } = render(<AiOperationProgressPanel />);
    fireEvent.click(screen.getByRole("button", { name: "关闭任务面板" }));
    expect(tracked.show).toHaveBeenCalledWith("closed");
    expect(tracked.retry).not.toHaveBeenCalled();
    tracked.visibility = "closed";
    rerender(<AiOperationProgressPanel />);
    expect(screen.queryByRole("complementary")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "查看 AI 任务" }));
    expect(tracked.show).toHaveBeenCalledWith("open");
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
  it("shows a recognized failure reason without exposing arbitrary server messages", () => {
    tracked.operation = aiOperationProgressSchema.parse(operation({ status: "FAILED", errorCode: "AI_VALIDATION_BUDGET_EXHAUSTED", errorMessage: "sensitive diagnostic" }));
    render(<AiOperationProgressPanel />);
    expect(screen.getByText("本轮真实调用额度已用完，未发起新的 AI 请求。")).toBeTruthy();
    expect(screen.queryByText("sensitive diagnostic")).toBeNull();
  });

});
