import { useEffect, useState } from "react";
import { RefreshCw, Plus, Save, Trash2, Cpu, CircleAlert } from "lucide-react";
import { api } from "@/lib/api-client";
import type { AiFeature, AiModelSelection, CatalogModel, ModelCatalog } from "@/lib/ai-model-config";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AdminErrorState, AdminLoadingState, AdminPageHeader, AdminPanel } from "./components/AdminChrome";
import { useToast } from "@/components/ui/use-toast";
import { getErrorMessage } from "./admin-list-utils";

const selectClass = "h-10 w-full min-w-0 rounded-md border border-zinc-700 bg-zinc-950 px-3 text-sm text-zinc-100";
const groups: Record<string, string> = { creation: "基础创作", world: "世界观", guided: "引导创作", writing: "正文生成", assistant: "创作助手", quality: "诊断与修订", tools: "工具" };
function modelConfigError(failure: unknown) {
  const messages: Record<string, string> = {
    AI_CONFIGURATION_CHANGED: "配置已变化，请重新载入后核对并保存。",
    AI_MODEL_UNAVAILABLE: "此模型已不可用，请重新选择后保存。",
    AI_THINKING_UNSUPPORTED: "模型不支持当前思考设置，请按目录能力重新选择。",
    AI_OPTIONS_INVALID: "请提供有效且不同的方案名称；免费功能只能保留一个方案。",
    AI_DEFAULT_OPTION_INVALID: "请为此功能指定一个默认方案。",
    AI_PRICE_INVALID: "固定价格须为正整数；Token 单价须非负且最多六位小数，输入与输出单价不能同时为零。",
  };
  return failure instanceof Error && messages[failure.message] ? messages[failure.message] : "保存失败，请检查模型、默认项和计费标准。";
}
function thinkingSummary(model: CatalogModel) {
  if (!model.thinking.declared) return "能力未知 · 仅跟随服务默认";
  const values = [model.thinking.supportsEnable ? "可开启" : "不可开启", model.thinking.supportsDisable ? "可关闭" : "不可关闭"];
  return [...values, ...model.thinking.options.map((option) => option.label || option.effort)].join(" / ");
}
function newOption(feature: AiFeature, model: CatalogModel): AiModelSelection {
  return { featureKey: feature.definition.key, optionId: crypto.randomUUID(), modelKey: model.modelKey, label: model.displayName,
    thinkingMode: "DEFAULT", thinkingEffort: null, streaming: model.supportsStreaming, billingEnabled: feature.billingEnabled,
    pricingMode: "TOKENS", fixedCredits: 1, inputPer1k: 0.01, outputPer1k: 0.01, revision: feature.revision };
}
function FeatureEditor({ feature: draft, models, onSave, saving, setDraft }: { feature: AiFeature; models: CatalogModel[]; onSave: (value: AiFeature) => Promise<void>; saving: boolean; setDraft: (change: (value: AiFeature) => AiFeature) => void }) {
  const [error, setError] = useState("");

  const change = (id: string, patch: Partial<AiModelSelection>) => setDraft((value) => ({ ...value, options: value.options.map((option) => option.optionId === id ? { ...option, ...patch } : option) }));
  const save = async () => {
    setError("");
    try { await onSave(draft); } catch (failure) { setError(modelConfigError(failure)); }
  };
  return <AdminPanel title={draft.definition.zh} description={draft.definition.key} actions={<Button onClick={() => void save()} disabled={saving} className="bg-zinc-100 text-zinc-950 hover:bg-white"><Save className="mr-2 h-4 w-4" />保存配置</Button>}>
    <div className="space-y-5">
      <label className="flex items-start gap-3 rounded-md border border-zinc-800 p-3 text-sm"><input type="checkbox" className="mt-1" checked={draft.billingEnabled} onChange={(e) => {
        const billingEnabled = e.target.checked;
        setDraft((current) => ({ ...current, billingEnabled, options: billingEnabled ? current.options : current.options.filter((option) => option.optionId === current.defaultOptionId) }));
      }} /><span>向用户计费<span className="mt-1 block text-xs text-zinc-400">计费功能可提供多个方案；免费功能仅保留默认方案。保存后只影响新提交的操作。</span></span></label>
      {draft.options.map((option, index) => {
        const model = models.find((item) => item.modelKey === option.modelKey);
        const modeAvailable = option.thinkingMode === "DEFAULT" || Boolean(model?.thinking.declared && (option.thinkingMode === "ON" ? model.thinking.supportsEnable : model.thinking.supportsDisable));
        return <section key={option.optionId} className="space-y-4 rounded-lg border border-zinc-700 bg-zinc-950/40 p-3 sm:p-4">
          <div className="flex items-center justify-between gap-3"><label className="flex items-center gap-2 text-sm"><input type="radio" name={`default-${draft.definition.key}`} checked={option.optionId === draft.defaultOptionId} onChange={() => setDraft((value) => ({ ...value, defaultOptionId: option.optionId }))} />{draft.billingEnabled ? `用户方案 ${index + 1}` : "后台指定模型"}<span className="text-xs text-zinc-400">{option.optionId === draft.defaultOptionId ? "默认" : "设为默认"}</span></label>
            {draft.billingEnabled && draft.options.length > 1 ? <Button aria-label="移除方案" variant="ghost" size="icon" onClick={() => setDraft((value) => {
              const options = value.options.filter((item) => item.optionId !== option.optionId);
              return { ...value, options, defaultOptionId: value.defaultOptionId === option.optionId ? options[0].optionId : value.defaultOptionId };
            })}><Trash2 className="h-4 w-4" /></Button> : null}</div>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-2 text-xs text-zinc-400"><span>模型</span><select aria-label={`方案 ${index + 1} 模型`} className={selectClass} value={option.modelKey} onChange={(e) => {
              const selected = models.find((item) => item.modelKey === e.target.value);
              change(option.optionId, { modelKey: e.target.value, thinkingMode: "DEFAULT", thinkingEffort: null, streaming: selected?.supportsStreaming ?? false });
            }}>{models.map((item) => <option key={item.modelKey} value={item.modelKey} disabled={!item.available}>{item.displayName} · {item.modelKey}{item.available ? "" : "（不可用）"}</option>)}</select></label>
            <label className="space-y-2 text-xs text-zinc-400"><span>用户展示名</span><Input aria-label={`方案 ${index + 1} 展示名`} value={option.label} maxLength={200} className="border-zinc-700 bg-zinc-950 text-zinc-100" onChange={(e) => change(option.optionId, { label: e.target.value })} /></label>
            <label className="space-y-2 text-xs text-zinc-400"><span>思考设置</span><select aria-label={`方案 ${index + 1} 思考设置`} className={selectClass} value={option.thinkingMode} onChange={(e) => change(option.optionId, { thinkingMode: e.target.value as AiModelSelection["thinkingMode"], thinkingEffort: null })}>
              {!modeAvailable ? <option value={option.thinkingMode} disabled>原思考设置已失效，请重新选择</option> : null}
              <option value="DEFAULT">跟随服务默认</option>{model?.thinking.declared && model.thinking.supportsDisable ? <option value="OFF">关闭</option> : null}{model?.thinking.declared && model.thinking.supportsEnable ? <option value="ON">开启</option> : null}
            </select></label>
            {option.thinkingMode === "ON" ? <label className="space-y-2 text-xs text-zinc-400"><span>思考强度</span><select aria-label={`方案 ${index + 1} 思考强度`} className={selectClass} value={option.thinkingEffort ?? ""} onChange={(e) => change(option.optionId, { thinkingEffort: e.target.value || null })}>
              <option value="">使用模型 / 服务默认强度</option>{model?.thinking.options.map((item) => <option key={item.effort} value={item.effort}>{item.label || item.effort}</option>)}
              {option.thinkingEffort && !model?.thinking.options.some((item) => item.effort === option.thinkingEffort) ? <option value={option.thinkingEffort} disabled>原强度已失效（{option.thinkingEffort}）</option> : null}
            </select></label> : null}
          </div>
          {draft.unavailableOptions.includes(option.optionId) ? <p className="flex items-center gap-2 text-xs text-amber-400"><CircleAlert className="h-4 w-4" />原配置的模型或思考设置已不可用，请重新选择。</p> : null}
          {draft.billingEnabled ? <div className="grid gap-3 border-t border-zinc-800 pt-4 sm:grid-cols-3">
            <label className="space-y-2 text-xs text-zinc-400"><span>计价方式</span><select className={selectClass} aria-label={`方案 ${index + 1} 计价方式`} value={option.pricingMode} onChange={(e) => change(option.optionId, { pricingMode: e.target.value as AiModelSelection["pricingMode"] })}><option value="TOKENS">按 token 计价</option><option value="FIXED">每次固定积分</option></select></label>
            {option.pricingMode === "FIXED" ? <label className="space-y-2 text-xs text-zinc-400"><span>每次成功调用积分</span><Input aria-label="固定积分" className="border-zinc-700 bg-zinc-950" type="number" min={1} step={1} value={option.fixedCredits ?? 1} onChange={(e) => change(option.optionId, { fixedCredits: Number(e.target.value) })} /></label> : <>
              <label className="space-y-2 text-xs text-zinc-400"><span>每千输入 token 积分</span><Input aria-label="输入单价" className="border-zinc-700 bg-zinc-950" type="number" min={0} step="0.000001" value={option.inputPer1k ?? 0} onChange={(e) => change(option.optionId, { inputPer1k: Number(e.target.value) })} /></label>
              <label className="space-y-2 text-xs text-zinc-400"><span>每千输出 token 积分</span><Input aria-label="输出单价" className="border-zinc-700 bg-zinc-950" type="number" min={0} step="0.000001" value={option.outputPer1k ?? 0} onChange={(e) => change(option.optionId, { outputPer1k: Number(e.target.value) })} /></label>
            </>}
          </div> : null}
        </section>;
      })}
      {draft.billingEnabled ? <Button variant="outline" className="border-zinc-700 bg-zinc-900" disabled={!models.some((model) => model.available)} onClick={() => {
        const model = models.find((item) => item.available); if (model) setDraft((current) => ({ ...current, options: [...current.options, newOption(current, model)] }));
      }}><Plus className="mr-2 h-4 w-4" />添加模型方案</Button> : null}
      <p className="text-xs leading-relaxed text-zinc-400">Token 价格按输入、输出合计计算后向上取整，最低 1 项目积分。上游费率不作为用户售价。重复请求不重复扣费；已提交任务沿用原方案。</p>
      {error ? <p role="alert" className="text-sm text-red-400">{error}</p> : null}
    </div>
  </AdminPanel>;
}
export default function ModelConfiguration() {
  const [catalog, setCatalog] = useState<ModelCatalog | null>(null);
  const [features, setFeatures] = useState<AiFeature[]>([]);
  const [drafts, setDrafts] = useState<Record<string, AiFeature>>({});
  const [active, setActive] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const { toast } = useToast();
  const load = async () => {
    setLoading(true); setError("");
    try { const [next, routes] = await Promise.all([api.modelConfig.catalog(), api.modelConfig.routing()]); setCatalog(next); setFeatures(routes); setDrafts({}); setActive((value) => value || routes[0]?.definition.key || ""); }
    catch (failure) { setError(getErrorMessage(failure, "模型配置加载失败")); } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const sync = async () => {
    setSyncing(true); setError("");
    try { setCatalog(await api.modelConfig.sync()); setFeatures(await api.modelConfig.routing()); toast({ title: "模型目录已同步" }); }
    catch { setError("同步失败，上次成功目录和功能配置已保留。请确认当前环境的 ai-service 支持调用方目录接口。"); }
    finally { setSyncing(false); }
  };
  const save = async (draft: AiFeature) => {
    setSaving(true);
    try { const updated = await api.modelConfig.save(draft); setFeatures((current) => current.map((item) => item.definition.key === updated.definition.key ? updated : item)); setDrafts((current) => { const next = { ...current }; delete next[draft.definition.key]; return next; }); toast({ title: "功能模型与计费配置已保存" }); }
    finally { setSaving(false); }
  };
  const selected = features.find((feature) => feature.definition.key === active);
  return <div className="space-y-6">
    {Object.keys(drafts).length ? <p role="status" className="text-xs text-amber-400">有未保存的功能配置。切换功能会保留编辑内容；重新载入会丢弃未保存内容。</p> : null}
    <AdminPageHeader title="模型配置" description="同步 ai-service 模型能力，按具体功能设置模型方案与项目积分价格。" actions={<Button onClick={() => void sync()} disabled={syncing || loading || saving} className="bg-zinc-100 text-zinc-950 hover:bg-white"><RefreshCw className={`mr-2 h-4 w-4 ${syncing ? "animate-spin" : ""}`} />{syncing ? "正在同步" : "手动同步模型"}</Button>} />
    {error ? <AdminErrorState message={error} onRetry={() => void load()} /> : null}
    {loading ? <AdminLoadingState /> : null}
    {catalog && !loading ? <Tabs defaultValue="features" className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><TabsList className="bg-zinc-900"><TabsTrigger value="features">功能配置 <span className="ml-2 text-xs opacity-60">{features.length}</span></TabsTrigger><TabsTrigger value="catalog">模型目录</TabsTrigger></TabsList><p className="text-xs text-zinc-400">上次成功同步：{catalog.last_success_at ? new Date(catalog.last_success_at).toLocaleString("zh-CN") : "尚未同步"}</p></div>
      <TabsContent value="features"><div className="grid items-start gap-5 xl:grid-cols-[280px_minmax(0,1fr)]">
        <div className="space-y-3"><Input aria-label="搜索功能" placeholder="搜索具体 AI 功能" className="border-zinc-700 bg-zinc-900" value={search} onChange={(e) => setSearch(e.target.value)} /><div className="max-h-[70vh] space-y-4 overflow-y-auto pr-1">{Object.entries(groups).map(([group, title]) => {
          const entries = features.filter((feature) => feature.definition.group === group && `${feature.definition.zh} ${feature.definition.key}`.toLowerCase().includes(search.toLowerCase()));
          return entries.length ? <div key={group}><h2 className="mb-1 px-2 text-xs text-zinc-500">{title}</h2>{entries.map((feature) => <button key={feature.definition.key} onClick={() => { if (!saving) setActive(feature.definition.key); }} className={`flex w-full items-center justify-between gap-2 rounded-md px-3 py-2.5 text-left text-sm ${active === feature.definition.key ? "bg-red-600/10 text-red-300" : "text-zinc-300 hover:bg-zinc-800"}`}><span>{feature.definition.zh}</span><span className="shrink-0 text-xs text-zinc-500">{feature.billingEnabled ? `${feature.options.length} 方案` : "免费"}</span></button>)}</div> : null;
        })}</div></div>
        {selected ? <FeatureEditor key={selected.definition.key} feature={drafts[active] ?? selected} setDraft={(change) => setDrafts((current) => ({ ...current, [active]: change(current[active] ?? selected) }))} models={catalog.models} onSave={save} saving={saving} /> : null}
      </div></TabsContent>
      <TabsContent value="catalog" className="space-y-5"><AdminPanel title="调用方可用文本模型" description="仅同步当前后端环境的目录。未同步的初始模型沿用当前环境配置；目录能力未知时仅允许跟随服务默认。">
        <div className="divide-y divide-zinc-800">{catalog.models.map((model) => <div key={model.modelKey} className="flex flex-col gap-3 py-4 sm:flex-row sm:justify-between">
          <div className="min-w-0"><h3 className="flex flex-wrap items-center gap-2 font-medium"><Cpu className="h-4 w-4 text-zinc-500" />{model.displayName}<Badge variant="outline" className={model.available ? "border-emerald-800 text-emerald-300" : "border-amber-800 text-amber-300"}>{model.available ? "可用" : "不可用"}</Badge></h3><p className="mt-1 break-all font-mono text-xs text-zinc-400">{model.modelKey} · {model.provider}</p><p className="mt-2 text-sm text-zinc-300">{thinkingSummary(model)}</p></div>
          <div className="shrink-0 space-y-1 text-xs text-zinc-400"><p>{model.supportsStreaming ? "支持真实流式" : "一元调用"}</p><p>默认思考：{model.thinking.defaultEnabled === null ? "未配置" : model.thinking.defaultEnabled ? "开启" : "关闭"}</p><p>默认强度：{model.thinking.defaultEffort ?? "未配置"}</p><p>最近同步：{model.syncedAt ? new Date(model.syncedAt).toLocaleString("zh-CN") : "尚未同步"}</p></div>
        </div>)}{!catalog.models.some((model) => model.available) ? <p className="py-8 text-center text-sm text-zinc-400">当前调用方没有可用文本模型。</p> : null}</div>
      </AdminPanel><AdminPanel title="专用链路（只读）" description="本轮目录不覆盖这些链路的完整能力，继续使用现有配置、能力约束与计费。"><div className="space-y-3 text-sm text-zinc-400">{catalog.specialized.map((item) => <div key={`${item.capability}-${item.model}`}><p className="break-all text-zinc-200">{item.capability} · {item.model}</p><p className="mt-1 text-xs">{item.source}</p></div>)}</div></AdminPanel></TabsContent>
    </Tabs> : null}
  </div>;
}
