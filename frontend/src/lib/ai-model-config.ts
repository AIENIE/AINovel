export type AiModelSelection = {
  featureKey: string; optionId: string; modelKey: string; label: string;
  thinkingMode: "DEFAULT" | "OFF" | "ON"; thinkingEffort: string | null;
  streaming: boolean; billingEnabled: boolean; pricingMode: "FIXED" | "TOKENS";
  fixedCredits: number | null; inputPer1k: number | null; outputPer1k: number | null; revision: number;
};
export type AiFeature = {
  definition: { key: string; group: string; zh: string; tw: string; en: string };
  revision: number; billingEnabled: boolean; defaultOptionId: string; preferredOptionId: string | null;
  preferenceInvalid: boolean; options: AiModelSelection[]; unavailableOptions: string[];
};
export type ThinkingCapabilities = {
  declared: boolean; supportsEnable: boolean; supportsDisable: boolean;
  options: Array<{ effort: string; label: string }>; defaultEnabled: boolean | null; defaultEffort: string | null;
};
export type CatalogModel = {
  id: string | null; modelKey: string; displayName: string; provider: string; available: boolean;
  supportsStreaming: boolean; supportsImageInput: boolean; thinking: ThinkingCapabilities; syncedAt: string | null;
};
export type ModelCatalog = {
  revision: number; last_attempt_at: string | null; last_success_at: string | null; last_error: string | null;
  models: CatalogModel[]; specialized: Array<{ capability: string; model: string; source: string }>;
};
export type AiQuote = {
  id: string; expiresAt: string; estimatedCredits: number;
  steps: Array<{ step: { featureKey: string; conditional: boolean; calls: string; estimatedInput: number; estimatedOutput: number };
    feature: AiFeature; selected: AiModelSelection; estimatedCredits: number }>;
};
export function featureName(feature: Pick<AiFeature, "definition">, language: string) {
  return language.startsWith("en") ? feature.definition.en : language === "zh-TW" ? feature.definition.tw : feature.definition.zh;
}

export type AiPendingPayment = { id: string; target: string; body: unknown; requestKey: string; quoteId: string | null; label: string };
