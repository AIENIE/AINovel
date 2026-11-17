import { beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import ModelConfiguration from "./ModelConfiguration";
import { api } from "@/lib/api-client";
import type { ModelResponsibility } from "@/lib/ai-model-config";
import { catalogFixture, featureFixture } from "@/test/ai-model-fixtures";
vi.mock("@/lib/api-client", () => ({ api: { modelConfig: { catalog: vi.fn(), routing: vi.fn(), save: vi.fn(), sync: vi.fn() } } }));
vi.mock("@/components/ui/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

function routingFixture(): ModelResponsibility {
  const feature = featureFixture();
  return {
    ...feature, definition: { ...feature.definition, key: "role.discussion", group: "support" },
    operations: [feature.definition], outputTokenLimits: {}, legacyDefaults: [],
  };
}
describe("Admin model configuration", () => {
  beforeEach(() => {
    cleanup(); vi.clearAllMocks();
    vi.mocked(api.modelConfig.catalog).mockResolvedValue(catalogFixture());
    const feature = routingFixture();feature.options = [feature.options[0]];
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
    const first = routingFixture(); const second = routingFixture();
    second.definition = { ...second.definition, key: "role.setting_refine", zh: "通用润色" };
    vi.mocked(api.modelConfig.routing).mockResolvedValue([first, second]);
    render(<ModelConfiguration />);const name = await screen.findByLabelText("方案 1 展示名");
    fireEvent.change(name, { target: { value: "My unsaved name" } });
    fireEvent.click(screen.getByRole("button", { name: /通用润色/ }));
    fireEvent.click(screen.getByRole("button", { name: /通用对话/ }));
    expect((screen.getByLabelText("方案 1 展示名") as HTMLInputElement).value).toBe("My unsaved name");
  });

  it("keeps an unsupported saved thinking mode visible until the administrator explicitly replaces it", async () => {
    const feature = routingFixture(); feature.options = [feature.options[1]]; feature.defaultOptionId = feature.options[0].optionId; feature.unavailableOptions = [feature.defaultOptionId];
    vi.mocked(api.modelConfig.routing).mockResolvedValue([feature]); render(<ModelConfiguration />);
    const thinking = await screen.findByLabelText("方案 1 思考设置");
    expect((thinking as HTMLSelectElement).value).toBe("ON"); expect(thinking.querySelector('option[value="ON"]')?.hasAttribute("disabled")).toBe(true);
    fireEvent.change(thinking, { target: { value: "DEFAULT" } });fireEvent.click(screen.getByRole("button", { name: "保存配置" }));
    await waitFor(() => expect(api.modelConfig.save).toHaveBeenCalledWith(expect.objectContaining({ options: [expect.objectContaining({ thinkingMode: "DEFAULT", thinkingEffort: null })] })));
  });

  it("finds a shared role by its covered operations and saves its configured output cap", async () => {
    const world = routingFixture();
    world.definition = { ...world.definition, key: "role.conception", group: "creation", zh: "构思与设定补全" };
    world.operations = [
      { ...world.definition, key: "world.geography.terrain.generate", zh: "地形地貌生成" },
      { ...world.definition, key: "world.geography.climate.generate", zh: "气候特征生成" },
    ];
    vi.mocked(api.modelConfig.routing).mockResolvedValue([routingFixture(), world]);
    render(<ModelConfiguration />);
    fireEvent.change(await screen.findByRole("textbox", { name: "搜索职责或操作" }), { target: { value: "气候" } });
    expect(screen.getByRole("button", { name: /构思与设定补全/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /气候特征生成/ })).toBeNull();
    fireEvent.change(screen.getByLabelText("方案 1 总输出上限"), { target: { value: "65536" } });
    fireEvent.click(screen.getByRole("button", { name: "保存配置" }));
    await waitFor(() => expect(api.modelConfig.save).toHaveBeenCalledWith(expect.objectContaining({
      definition: expect.objectContaining({ key: "role.conception" }),
      outputTokenLimits: { [world.options[0].optionId]: 65536 },
    })));
  });
});
