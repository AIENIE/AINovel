export type AiModelSelection = {
  featureKey: string; optionId: string; modelKey: string; label: string;
  thinkingMode: "DEFAULT" | "OFF" | "ON"; thinkingEffort: string | null;
  streaming: boolean; billingEnabled: boolean; pricingMode: "FIXED" | "TOKENS";
  fixedCredits: number | null; inputPer1k: number | null; outputPer1k: number | null; revision: number;
};
export type ModelResponsibility = {
  definition: { key: string; group: string; zh: string; tw: string; en: string };
  revision: number;
  billingEnabled: boolean;
  defaultOptionId: string | null;
  preferredOptionId: string | null;
  preferenceInvalid: boolean;
  options: AiModelSelection[];
  unavailableOptions: string[];
  outputTokenLimits: Record<string, number>;
};
export type ThinkingCapabilities = {
  declared: boolean; supportsEnable: boolean; supportsDisable: boolean;
  options: Array<{ effort: string; label: string }>; defaultEnabled: boolean | null; defaultEffort: string | null;
};
export type CatalogModel = {
  id: string | null; modelKey: string; displayName: string; provider: string; available: boolean;
  supportsStreaming: boolean; supportsImageInput: boolean; thinking: ThinkingCapabilities; syncedAt: string | null;
  chatCapabilities?: {
    budgetDeclared: boolean; budgetAvailable: boolean; budgetToolsDeclared: boolean; budgetToolsAvailable: boolean;
    maxPromptTokens: number; maxOutputTokens: number;
  };
};
export type ModelCatalog = {
  revision: number; last_attempt_at: string | null; last_success_at: string | null; last_error: string | null;
  models: CatalogModel[]; specialized: Array<{ capability: string; model: string; source: string }>;
};
export type AiQuoteEstimate = { conditional: boolean; calls: string; estimatedInput: number; estimatedOutput: number };
export type AiQuote = {
  id: string; expiresAt: string; estimatedCredits: number;
  roles: Array<{ role: ModelResponsibility; selected: AiModelSelection; estimates: AiQuoteEstimate[]; estimatedCredits: number }>;
};
export function roleName(role: Pick<ModelResponsibility, "definition">, language: string) {
  return language.startsWith("en") ? role.definition.en : language === "zh-TW" ? role.definition.tw : role.definition.zh;
}

export type AiPendingPayment = { id: string; target: string; body: unknown; requestKey: string; quoteId: string | null; label: string };
