import { useEffect, useState } from "react";
import { CircleAlert, Plus, RefreshCw, Save, Trash2 } from "lucide-react";
import { api } from "@/lib/api-client";
import type { AiModelSelection, CatalogModel, ModelCatalog, ModelResponsibility } from "@/lib/ai-model-config";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/use-toast";
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel } from "./components/AdminChrome";
import { getErrorMessage } from "./admin-list-utils";

const selectClass = "h-10 w-full min-w-0 rounded-md border border-zinc-700 bg-zinc-950 px-3 text-sm text-zinc-100";
const groups = { creation: "创作", review: "检查", support: "协作与工具" };
const agentControlKey = "role.agent_control";

const errors: Record<string, string> = {
  AI_CONFIGURATION_CHANGED: "配置已变化，请重新载入后核对并保存。",
  AI_MODEL_UNAVAILABLE: "此模型已不可用，请重新选择后保存。",
  AI_NATIVE_BUDGET_UNAVAILABLE: "自主创作协调需要支持原生工具预算的模型。",
  AI_THINKING_UNSUPPORTED: "模型不支持当前思考设置，请按目录能力重新选择。",
  AI_OPTIONS_INVALID: "请填写不同的方案名称。免费职责只保留一个方案。",
  AI_DEFAULT_OPTION_INVALID: "请指定默认方案。",
  AI_PRICE_INVALID: "固定价格须为正整数。Token 单价最多六位小数，输入与输出单价至少一项大于零。",
  AI_OUTPUT_LIMIT_INVALID: "总输出上限须为正整数。",
};

function eligible(role: ModelResponsibility, model: CatalogModel) {
  return model.available && (role.definition.key !== agentControlKey || model.chatCapabilities?.budgetToolsAvailable === true);
}

function defaultLabel(role: ModelResponsibility, models: CatalogModel[]) {
  const option = role.options.find((item) => item.optionId === role.defaultOptionId);
  if (!option) return "待配置";
  const model = models.find((item) => item.modelKey === option.modelKey);
  const thinking = option.thinkingMode === "ON" ? option.thinkingEffort ?? "开启思考"
    : option.thinkingMode === "OFF" ? "关闭思考" : null;
  return [model?.displayName ?? option.label, thinking, role.unavailableOptions.includes(option.optionId) ? "需更换" : null]
    .filter(Boolean).join(" · ");
}

function defaultFirst(role: ModelResponsibility): ModelResponsibility {
  const index = role.options.findIndex((option) => option.optionId === role.defaultOptionId);
  if (index <= 0) return role;
  return { ...role, options: [role.options[index], ...role.options.filter((_, position) => position !== index)] };
}

function newOption(role: ModelResponsibility, model: CatalogModel): AiModelSelection {
  return {
    featureKey: role.definition.key, optionId: crypto.randomUUID(), modelKey: model.modelKey, label: model.displayName,
    thinkingMode: "DEFAULT", thinkingEffort: null, streaming: model.supportsStreaming, billingEnabled: role.billingEnabled,
    pricingMode: "TOKENS", fixedCredits: 1, inputPer1k: 0.01, outputPer1k: 0.01, revision: role.revision,
  };
}

function withOutputLimit(role: ModelResponsibility, optionId: string, limit?: number): ModelResponsibility {
  const outputTokenLimits = { ...role.outputTokenLimits };
  if (limit === undefined) delete outputTokenLimits[optionId];
  else outputTokenLimits[optionId] = limit;
  return { ...role, outputTokenLimits };
}

type EditRole = (update: (role: ModelResponsibility) => ModelResponsibility) => void;

function RoleEditor({ role, models, update, save, saving }: {
  role: ModelResponsibility; models: CatalogModel[]; update: EditRole;
  save: (role: ModelResponsibility) => Promise<void>; saving: boolean;
}) {
  const [error, setError] = useState("");
  const firstModel = models.find((model) => eligible(role, model));

  const changeOption = (id: string, patch: Partial<AiModelSelection>) => (value: ModelResponsibility) => ({
    ...value, options: value.options.map((option) => option.optionId === id ? { ...option, ...patch } : option),
  });

  const submit = async () => {
    setError("");
    try { await save(role); }
    catch (failure) {
      setError(failure instanceof Error && errors[failure.message] || getErrorMessage(failure, "保存配置失败"));
    }
  };

  return <AdminPanel title={role.definition.zh} className="min-w-0" actions={
    <Button onClick={() => void submit()} disabled={saving || !role.options.length}>
      <Save className="mr-2 h-4 w-4" />{saving ? "正在保存" : "保存配置"}
    </Button>
  }>
    <fieldset disabled={saving} className="min-w-0 space-y-4">
      <label className="flex items-center gap-2 text-sm text-zinc-200">
        <input type="checkbox" checked={role.billingEnabled} onChange={(event) => {
          const billingEnabled = event.target.checked;
          update((value) => {
            if (billingEnabled) return { ...value, billingEnabled };
            const options = value.options.filter((option) => option.optionId === value.defaultOptionId);
            const limit = value.defaultOptionId ? value.outputTokenLimits[value.defaultOptionId] : undefined;
            return { ...value, billingEnabled, options, outputTokenLimits: limit && value.defaultOptionId ? { [value.defaultOptionId]: limit } : {} };
          });
        }} />向用户计费
      </label>
      {role.options.map((option, index) => {
        const model = models.find((item) => item.modelKey === option.modelKey);
        const name = `方案 ${index + 1}`;
        const change = (patch: Partial<AiModelSelection>) => update(changeOption(option.optionId, patch));
        const modeAvailable = option.thinkingMode === "DEFAULT" || Boolean(model?.thinking.declared &&
          (option.thinkingMode === "ON" ? model.thinking.supportsEnable : model.thinking.supportsDisable));
        return <section key={option.optionId} className="space-y-4 rounded-lg border border-zinc-700/80 bg-zinc-950/45 p-4">
          <div className="flex min-h-6 items-center justify-between gap-2">
            <label className="flex items-center gap-2 text-sm text-zinc-200">
              <input type="radio" name={`default-${role.definition.key}`} checked={option.optionId === role.defaultOptionId}
                onChange={() => update((value) => ({ ...value, defaultOptionId: option.optionId }))} />
              {option.optionId === role.defaultOptionId ? "默认方案" : "设为默认"}
            </label>
            {role.billingEnabled && role.options.length > 1 ? <Button aria-label="移除方案" variant="ghost" size="icon"
              onClick={() => update((value) => {
                const options = value.options.filter((item) => item.optionId !== option.optionId);
                const defaultOptionId = value.defaultOptionId === option.optionId ? options[0].optionId : value.defaultOptionId;
                return withOutputLimit({ ...value, options, defaultOptionId }, option.optionId);
              })}><Trash2 className="h-4 w-4" /></Button> : null}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1.5 text-xs text-zinc-400"><span>模型</span>
              <select aria-label={`${name} 模型`} className={selectClass} value={option.modelKey} onChange={(event) => {
                const selected = models.find((item) => item.modelKey === event.target.value);
                if (!selected) return;
                const patch = { modelKey: selected.modelKey, thinkingMode: "DEFAULT" as const, thinkingEffort: null, streaming: selected.supportsStreaming };
                update((value) => withOutputLimit(changeOption(option.optionId, patch)(value), option.optionId));
              }}>
                {models.map((item) => <option key={item.modelKey} value={item.modelKey} disabled={!eligible(role, item)}>
                  {item.displayName}{!eligible(role, item) ? "（不可用）" : ""}
                </option>)}
              </select>
            </label>
            <label className="space-y-1.5 text-xs text-zinc-400"><span>用户展示名</span>
              <Input aria-label={`${name} 展示名`} value={option.label} maxLength={200} className="border-zinc-700 bg-zinc-950 text-zinc-100"
                onChange={(event) => change({ label: event.target.value })} />
            </label>
            <label className="space-y-1.5 text-xs text-zinc-400"><span>思考设置</span>
              <select aria-label={`${name} 思考设置`} className={selectClass} value={option.thinkingMode}
                onChange={(event) => change({ thinkingMode: event.target.value as AiModelSelection["thinkingMode"], thinkingEffort: null })}>
                {!modeAvailable ? <option value={option.thinkingMode} disabled>原思考设置已失效，请重新选择</option> : null}
                <option value="DEFAULT">跟随服务默认</option>
                {model?.thinking.declared && model.thinking.supportsDisable ? <option value="OFF">关闭</option> : null}
                {model?.thinking.declared && model.thinking.supportsEnable ? <option value="ON">开启</option> : null}
              </select>
            </label>
            {option.thinkingMode === "ON" ? <label className="space-y-1.5 text-xs text-zinc-400"><span>思考强度</span>
              <select aria-label={`${name} 思考强度`} className={selectClass} value={option.thinkingEffort ?? ""}
                onChange={(event) => change({ thinkingEffort: event.target.value || null })}>
                <option value="">使用模型默认强度</option>
                {model?.thinking.options.map((item) => <option key={item.effort} value={item.effort}>{item.label || item.effort}</option>)}
                {option.thinkingEffort && !model?.thinking.options.some((item) => item.effort === option.thinkingEffort)
                  ? <option value={option.thinkingEffort} disabled>原强度已失效（{option.thinkingEffort}）</option> : null}
              </select>
            </label> : null}
            <label className="space-y-1.5 text-xs text-zinc-400"><span>总输出上限（含思考 token）</span>
              <Input aria-label={`${name} 总输出上限`} type="number" min={1} step={1} placeholder="按操作默认"
                className="border-zinc-700 bg-zinc-950 text-zinc-100" value={role.outputTokenLimits[option.optionId] ?? ""}
                onChange={(event) => {
                  const limit = event.target.value === "" ? undefined : Number(event.target.value);
                  update((value) => withOutputLimit(value, option.optionId, limit));
                }} />
            </label>
          </div>
          {role.unavailableOptions.includes(option.optionId) ? <p className="flex items-center gap-2 text-xs text-amber-300">
            <CircleAlert className="h-4 w-4 shrink-0" />已保存的模型或思考设置需要更新。
          </p> : null}
          {role.billingEnabled ? <OptionPricing option={option} name={name} change={change} /> : null}
        </section>;
      })}
      {role.billingEnabled || !role.options.length ? <Button variant="outline" className="border-zinc-700 bg-zinc-900"
        disabled={!firstModel} onClick={() => {
          if (!firstModel) return;
          update((value) => {
            const option = newOption(value, firstModel);
            return { ...value, options: [...value.options, option], defaultOptionId: value.defaultOptionId ?? option.optionId };
          });
        }}><Plus className="mr-2 h-4 w-4" />添加模型方案</Button> : null}
      {!firstModel ? <p className="text-sm text-amber-300">{role.definition.key === agentControlKey
        ? "目录中没有可用的原生工具预算模型。请在 AIService 登记能力后同步目录。" : "请先同步可用模型。"}</p> : null}
      {error ? <p role="alert" className="text-sm text-rose-300">{error}</p> : null}
    </fieldset>
  </AdminPanel>;
}

function OptionPricing({ option, name, change }: {
  option: AiModelSelection; name: string; change: (patch: Partial<AiModelSelection>) => void;
}) {
  return <div className="grid gap-3 border-t border-zinc-800 pt-4 sm:grid-cols-2 xl:grid-cols-3">
    <label className="space-y-1.5 text-xs text-zinc-400"><span>计价方式</span>
      <select className={selectClass} aria-label={`${name} 计价方式`} value={option.pricingMode}
        onChange={(event) => change({ pricingMode: event.target.value as AiModelSelection["pricingMode"] })}>
        <option value="TOKENS">按 token 计价</option><option value="FIXED">每次固定积分</option>
      </select>
    </label>
    {option.pricingMode === "FIXED" ? <label className="space-y-1.5 text-xs text-zinc-400"><span>每次积分</span>
      <Input aria-label={`${name} 固定积分`} className="border-zinc-700 bg-zinc-950 text-zinc-100" type="number" min={1} step={1}
        value={option.fixedCredits ?? 1} onChange={(event) => change({ fixedCredits: Number(event.target.value) })} />
    </label> : (["inputPer1k", "outputPer1k"] as const).map((field) => {
      const tokenType = field === "inputPer1k" ? "输入" : "输出";
      return <label key={field} className="space-y-1.5 text-xs text-zinc-400"><span>每千{tokenType} token 积分</span>
        <Input aria-label={`${name} ${tokenType}单价`} className="border-zinc-700 bg-zinc-950 text-zinc-100" type="number" min={0} step="0.000001"
          value={option[field] ?? 0} onChange={(event) => change({ [field]: Number(event.target.value) })} />
      </label>;
    })}
  </div>;
}

function CatalogView({ catalog }: { catalog: ModelCatalog }) {
  return <div className="space-y-5">
    <AdminPanel title="调用方可用文本模型">
      {catalog.models.length ? <ul className="divide-y divide-zinc-800">{catalog.models.map((model) => {
        const caps = model.chatCapabilities;
        const thinking = model.thinking.declared ? model.thinking.options.map((item) => item.label || item.effort).join("、")
          || (model.thinking.supportsEnable ? "支持开启" : "仅可关闭") : "跟随服务默认";
        return <li key={model.modelKey} className="grid gap-2 py-3.5 md:grid-cols-[minmax(0,1fr)_minmax(0,1.25fr)]">
          <div className="min-w-0">
            <p className="break-words font-medium">{model.displayName}{!model.available ? <span className="ml-2 text-xs text-amber-300">不可用</span> : null}</p>
            <p className="mt-1 break-words font-mono text-xs text-zinc-400">{model.modelKey} · {model.provider}</p>
          </div>
          <div className="space-y-1.5 text-xs text-zinc-300">
            <p>思考 {thinking}{model.thinking.defaultEffort ? ` · 默认 ${model.thinking.defaultEffort}` : ""}</p>
            <p>{model.supportsStreaming ? "支持流式" : "一元调用"} · 一元金额预算 {caps?.budgetAvailable ? "可用" : "未就绪"} · 原生工具预算 {caps?.budgetToolsAvailable ? "可用" : "未就绪"}</p>
            {caps?.budgetAvailable ? <p className="text-zinc-400">预算上限 {caps.maxPromptTokens.toLocaleString("zh-CN")} 输入／{caps.maxOutputTokens.toLocaleString("zh-CN")} 输出 token</p> : null}
          </div>
        </li>;
      })}</ul> : <AdminEmptyState title="目录暂无文本模型" />}
    </AdminPanel>
    <AdminPanel title="专用链路（只读）"><dl className="space-y-3 text-sm">
      {catalog.specialized.map((item) => <div key={`${item.capability}-${item.model}`}>
        <dt className="break-words text-zinc-200">{item.capability} · {item.model}</dt>
        <dd className="mt-1 text-xs text-zinc-400">{item.source}</dd>
      </div>)}
    </dl></AdminPanel>
  </div>;
}

export default function ModelConfiguration() {
  const [catalog, setCatalog] = useState<ModelCatalog | null>(null);
  const [roles, setRoles] = useState<ModelResponsibility[]>([]);
  const [drafts, setDrafts] = useState<Record<string, ModelResponsibility>>({});
  const [active, setActive] = useState("");
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const { toast } = useToast();

  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const [directory, routing] = await Promise.all([api.modelConfig.catalog(), api.modelConfig.routing()]);
      setCatalog(directory);
      setRoles(routing.map(defaultFirst));
      setDrafts({});
    } catch (failure) { setError(getErrorMessage(failure, "模型配置加载失败")); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const sync = async () => {
    setSyncing(true);
    setError("");
    try {
      setCatalog(await api.modelConfig.sync());
      setRoles((await api.modelConfig.routing()).map(defaultFirst));
      toast({ title: "模型目录已同步" });
    } catch { setError("同步失败，上次成功目录和职责配置已保留。"); }
    finally { setSyncing(false); }
  };

  const save = async (draft: ModelResponsibility) => {
    setSaving(true);
    try {
      const updated = await api.modelConfig.save(draft);
      setRoles((current) => current.map((role) => role.definition.key === updated.definition.key ? defaultFirst(updated) : role));
      setDrafts((current) => {
        const next = { ...current };
        delete next[draft.definition.key];
        return next;
      });
      toast({ title: "职责配置已保存" });
    } finally { setSaving(false); }
  };

  const selected = roles.find((role) => role.definition.key === active) ?? roles[0];

  return <div className="space-y-5">
    <AdminPageHeader title="模型配置" actions={
      <Button disabled={syncing || loading || saving} variant="outline" className="border-zinc-700 bg-zinc-900" onClick={() => void sync()}>
        <RefreshCw className={`mr-2 h-4 w-4 ${syncing ? "animate-spin" : ""}`} />{syncing ? "正在同步" : "手动同步模型"}
      </Button>
    } />
    {error ? <AdminErrorState message={error} onRetry={() => void (catalog ? sync() : load())} /> : null}
    {loading ? <AdminLoadingState /> : null}
    {catalog && !loading ? <Tabs defaultValue="roles" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <TabsList className="bg-zinc-900"><TabsTrigger value="roles">职责配置</TabsTrigger><TabsTrigger value="catalog">模型目录</TabsTrigger></TabsList>
        <p className="text-xs text-zinc-400">{catalog.last_success_at ? `上次同步 ${new Date(catalog.last_success_at).toLocaleString("zh-CN")}` : "目录尚未同步"}</p>
      </div>
      <TabsContent value="roles" className="grid items-start gap-5 lg:grid-cols-[250px_minmax(0,1fr)]">
        <div className="min-w-0 space-y-3">
          {selected ? <label className="block space-y-1.5 text-xs text-zinc-400 lg:hidden"><span>职责</span>
            <select aria-label="选择职责" className={selectClass} value={selected.definition.key} disabled={saving}
              onChange={(event) => setActive(event.target.value)}>
              {roles.map((role) => <option key={role.definition.key} value={role.definition.key}>{role.definition.zh}</option>)}
            </select>
            <span className="block break-words">{defaultLabel(selected, catalog.models)}</span>
          </label> : null}
          <nav className="hidden space-y-4 lg:block" aria-label="模型职责">
            {Object.entries(groups).map(([group, title]) => {
              const entries = roles.filter((role) => role.definition.group === group);
              if (!entries.length) return null;
              return <div key={group}>
                <h2 className="mb-1.5 px-2 text-xs font-medium text-zinc-400">{title}</h2>
                {entries.map((role) => <button key={role.definition.key} type="button" onClick={() => setActive(role.definition.key)} disabled={saving}
                  aria-current={selected?.definition.key === role.definition.key ? "true" : undefined}
                  className={`block w-full min-w-0 border-l-2 px-3 py-2 text-left hover:bg-zinc-900 ${selected?.definition.key === role.definition.key ? "border-red-400 bg-zinc-900 text-zinc-50" : "border-transparent text-zinc-300"}`}>
                  <span className="flex items-center justify-between gap-2 text-sm">{role.definition.zh}
                    {drafts[role.definition.key] ? <span className="shrink-0 text-xs text-amber-300">待保存</span> : null}</span>
                  <span className="mt-0.5 block break-words text-xs text-zinc-400">{defaultLabel(role, catalog.models)}</span>
                </button>)}
              </div>;
            })}
          </nav>
        </div>
        {selected ? <div className="min-w-0 space-y-2">
          {drafts[selected.definition.key] ? <p role="status" className="text-xs text-amber-300">这项职责有未保存的修改。</p> : null}
          <RoleEditor key={selected.definition.key} role={drafts[selected.definition.key] ?? selected} models={catalog.models}
            update={(change) => setDrafts((current) => ({ ...current, [selected.definition.key]: change(current[selected.definition.key] ?? selected) }))}
            save={save} saving={saving || syncing} />
        </div> : <AdminEmptyState title="没有匹配的职责" />}
      </TabsContent>
      <TabsContent value="catalog"><CatalogView catalog={catalog} /></TabsContent>
    </Tabs> : null}
  </div>;
}
