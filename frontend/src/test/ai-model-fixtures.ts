import type { AiFeature, AiQuote, ModelCatalog } from "@/lib/ai-model-config";
export function featureFixture(): AiFeature {
  return { definition: { key: "generic.chat", group: "tools", zh: "通用对话", tw: "通用對話", en: "General chat" }, revision: 1,
    billingEnabled: true, defaultOptionId: "option-a", preferredOptionId: null, preferenceInvalid: false, unavailableOptions: [],
    options: [
      { featureKey: "generic.chat", optionId: "option-a", modelKey: "model-a", label: "Fast", thinkingMode: "DEFAULT", thinkingEffort: null,
        streaming: true, billingEnabled: true, pricingMode: "FIXED", fixedCredits: 1, inputPer1k: null, outputPer1k: null, revision: 1 },
      { featureKey: "generic.chat", optionId: "option-b", modelKey: "model-a", label: "Careful", thinkingMode: "ON", thinkingEffort: "HIGH",
        streaming: true, billingEnabled: true, pricingMode: "TOKENS", fixedCredits: null, inputPer1k: 0.1, outputPer1k: 0.3, revision: 1 },
    ] };
}
export function quoteFixture(): AiQuote {
  const feature = featureFixture();
  return { id: "quote-one", expiresAt: new Date(Date.now() + 600000).toISOString(), estimatedCredits: 1,
    steps: [{ step: { featureKey: "generic.chat", conditional: false, calls: "ONE", estimatedInput: 100, estimatedOutput: 4096 },
      feature, selected: feature.options[0], estimatedCredits: 1 }] };
}
export function catalogFixture(): ModelCatalog {
  return { revision: 1, last_attempt_at: null, last_success_at: null, last_error: null, specialized: [
    { capability: "Rerank", model: "specialized-rerank", source: "read only" }],
    models: [{ id: "1", modelKey: "model-a", displayName: "Model A", provider: "fixture", available: true, supportsStreaming: true,
      supportsImageInput: false, syncedAt: null, thinking: { declared: false, supportsEnable: false, supportsDisable: false, options: [], defaultEnabled: null, defaultEffort: null } }] };
}
