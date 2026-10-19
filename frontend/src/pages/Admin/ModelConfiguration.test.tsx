import { beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import ModelConfiguration from "./ModelConfiguration";
import { api } from "@/lib/api-client";
import { catalogFixture, featureFixture } from "@/test/ai-model-fixtures";
vi.mock("@/lib/api-client", () => ({ api: { modelConfig: { catalog: vi.fn(), routing: vi.fn(), save: vi.fn(), sync: vi.fn() } } }));
vi.mock("@/components/ui/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
describe("Admin model configuration", () => {
  beforeEach(() => {
    cleanup(); vi.clearAllMocks();
    vi.mocked(api.modelConfig.catalog).mockResolvedValue(catalogFixture());
    const feature = featureFixture();feature.options = [feature.options[0]];
    vi.mocked(api.modelConfig.routing).mockResolvedValue([feature]);
    vi.mocked(api.modelConfig.save).mockImplementation(async (value) => ({ ...value, revision: value.revision + 1 }));
  });
  it("unknown thinking capabilities expose only the service default", async () => {
    render(<ModelConfiguration />);
    const thinking = await screen.findByLabelText("方案 1 思考设置");
    expect(Array.from(thinking.querySelectorAll("option")).map((option) => option.value)).toEqual(["DEFAULT"]);
  });
  it("turning off billing keeps only the default model option and hides pricing", async () => {
    render(<ModelConfiguration />);await screen.findByLabelText("方案 1 思考设置");
    fireEvent.click(screen.getByRole("button", { name: "添加模型方案" }));
    expect(screen.getByLabelText("方案 2 模型")).toBeTruthy();
    fireEvent.click(screen.getByRole("checkbox"));
    expect(screen.queryByLabelText("方案 2 模型")).toBeNull();
    expect(screen.queryByLabelText("方案 1 计价方式")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "保存配置" }));
    await waitFor(() => expect(api.modelConfig.save).toHaveBeenCalledWith(expect.objectContaining({ billingEnabled: false, options: [expect.any(Object)] })));
  });
  it("failed sync preserves the existing directory and explains that no configuration was replaced", async () => {
    vi.mocked(api.modelConfig.sync).mockRejectedValue(new Error("unavailable"));render(<ModelConfiguration />);
    await screen.findByLabelText("方案 1 思考设置");fireEvent.click(screen.getByRole("button", { name: "手动同步模型" }));
    await screen.findByText(/上次成功目录和功能配置已保留/);
    expect((screen.getByLabelText("方案 1 模型") as HTMLSelectElement).value).toBe("model-a");
  });
  it("retains an unsaved option name while switching between feature configurations", async () => {
    const first = featureFixture(); const second = featureFixture();
    second.definition = { ...second.definition, key: "generic.refine", zh: "通用润色" };
    vi.mocked(api.modelConfig.routing).mockResolvedValue([first, second]);
    render(<ModelConfiguration />);const name = await screen.findByLabelText("方案 1 展示名");
    fireEvent.change(name, { target: { value: "My unsaved name" } });
    fireEvent.click(screen.getByRole("button", { name: /通用润色/ }));
    fireEvent.click(screen.getByRole("button", { name: /通用对话/ }));
    expect((screen.getByLabelText("方案 1 展示名") as HTMLInputElement).value).toBe("My unsaved name");
  });

  it("keeps an unsupported saved thinking mode visible until the administrator explicitly replaces it", async () => {
    const feature = featureFixture(); feature.options = [feature.options[1]]; feature.defaultOptionId = feature.options[0].optionId; feature.unavailableOptions = [feature.defaultOptionId];
    vi.mocked(api.modelConfig.routing).mockResolvedValue([feature]); render(<ModelConfiguration />);
    const thinking = await screen.findByLabelText("方案 1 思考设置");
    expect((thinking as HTMLSelectElement).value).toBe("ON"); expect(thinking.querySelector('option[value="ON"]')?.hasAttribute("disabled")).toBe(true);
    fireEvent.change(thinking, { target: { value: "DEFAULT" } });fireEvent.click(screen.getByRole("button", { name: "保存配置" }));
    await waitFor(() => expect(api.modelConfig.save).toHaveBeenCalledWith(expect.objectContaining({ options: [expect.objectContaining({ thinkingMode: "DEFAULT", thinkingEffort: null })] })));
  });

});
