export interface User {
  id: string;
  username: string;
  email: string;
  avatar?: string;
  role: 'user' | 'admin';
  credits: number; // legacy: projectCredits
  projectCredits: number;
  publicCredits: number;
  totalCredits: number;
  isBanned: boolean;
}

export interface Story {
  id: string;
  title: string;
  synopsis: string;
  genre: string;
  tone: string;
  status: 'draft' | 'published' | 'archived';
  updatedAt: string;
  cover?: string;
}

export interface PlotBeat {
  id: string;
  label: string;
  summary: string;
}

export interface TwistOption {
  id: string;
  label: string;
  track: 'instinct' | 'structure';
  hook: string;
  hiddenTruth: string;
  setup: string[];
  misdirection: string[];
  revealBeat?: string;
  revealTiming: string;
  payoff: string;
  risk: string;
}

export interface ForeshadowPlan {
  id: string;
  clue: string;
  disguise: string;
  payoff: string;
  revealTiming?: string;
}

export interface MemeStrategy {
  reference: string;
  purpose: string;
  usage: string;
  caution: string;
}

export interface PlotPlanning {
  corePromise: string;
  centralQuestion: string;
  hiddenTruth: string;
  readerMisdirect: string;
  stakes: string;
  beats: PlotBeat[];
  twistOptions: TwistOption[];
  foreshadowPlans: ForeshadowPlan[];
  memeStrategy?: MemeStrategy;
  selectedTwistId?: string;
  lorebookSeeds?: Array<Record<string, unknown>>;
  graphSeeds?: Array<Record<string, unknown>>;
  confidence?: number;
  warnings?: string[];
}

export interface ChapterPlanning {
  purpose?: string;
  informationRelease?: string;
  twistRole?: string;
  selectedTwistId?: string;
  revealFocus?: string;
  tensionShift?: string;
}

export interface ScenePlanning {
  goal?: string;
  conflict?: string;
  infoRelease?: string;
  foreshadowId?: string;
  revealFor?: string;
  foreshadowHint?: string;
  misdirectionAction?: string;
  revealTrigger?: string;
  payoffPlan?: string;
  memeUsage?: string;
}

export interface Character {
  id: string;
  name: string;
  role: string;
  archetype: string;
  summary: string;
}

export interface World {
  id: string;
  name: string;
  tagline: string;
  status: 'draft' | 'generating' | 'active' | 'archived';
  version: string;
  updatedAt: string;
}

export interface Material {
  id: string;
  title: string;
  type: 'text' | 'image' | 'link';
  content: string;
  summary?: string;
  tags: string[];
  status: 'pending' | 'approved' | 'rejected';
  createdAt?: string;
}

export interface MaterialSearchResult {
  materialId: string;
  chunkId: string;
  title: string;
  snippet: string;
  score: number;
  chunkSeq?: number;
  source: 'keyword' | 'vector' | string;
  matchReasons: string[];
}

export interface FileImportJob {
  id: string;
  fileName: string;
  status: 'processing' | 'completed' | 'failed';
  progress: number;
  message?: string;
}

export interface Scene {
  id: string;
  title: string;
  summary: string;
  content?: string;
  planning?: ScenePlanning;
}

export interface Chapter {
  id: string;
  title: string;
  summary: string;
  scenes: Scene[];
  planning?: ChapterPlanning;
}

export interface Outline {
  id: string;
  storyId: string;
  title: string;
  chapters: Chapter[];
  updatedAt: string;
  planning?: PlotPlanning;
  activeTwistId?: string;
}

export interface Manuscript {
  id: string;
  outlineId: string;
  title: string;
  worldId?: string;
  sections: Record<string, string>; // sceneId -> html content
  updatedAt: string;
}

export interface SlopQualityIssue {
  id: string;
  dimension: string;
  severity: string;
  riskScore: number;
  evidence?: string;
  whyItMatters?: string;
  minimalFix?: string;
  charStart?: number;
  charEnd?: number;
  quote?: string;
  module?: string;
  patternId?: string;
  issueType?: string;
  evidenceLevel?: string;
  alternativeExplanations: string[];
  repairHint?: string;
}

export interface SlopRewriteTask {
  task_id?: string;
  taskId?: string;
  priority?: number;
  target_span?: unknown;
  problem?: string;
  repair_goal?: string;
  repairGoal?: string;
  constraints?: string[];
  suggested_method?: string;
  suggestedMethod?: string;
}

export interface SlopQualityRun {
  id: string;
  storyId: string;
  manuscriptId: string;
  sceneId: string;
  status: "ACCEPTED" | "REVISED" | "ACCEPTED_WITH_ISSUES" | "DEGRADED" | string;
  maxSeverity: "LOW" | "MEDIUM" | "HIGH" | "BLOCKING" | string;
  overallRiskScore: number;
  revised: boolean;
  revisionCount: number;
  summary?: string;
  analysisMode?: string;
  riskLabel?: string;
  evidenceLevel?: string;
  safeClaim?: string;
  moduleScores: Record<string, unknown>;
  alternativeExplanations: string[];
  revisionPriorities: unknown[];
  rewriteTasks: SlopRewriteTask[];
  createdAt?: string;
  issues: SlopQualityIssue[];
}

export interface SlopDriftRun {
  id: string;
  storyId: string;
  manuscriptId: string;
  status: "COMPLETED" | "INSUFFICIENT_TEXT" | "DEGRADED" | string;
  overallRiskScore: number;
  riskLabel?: string;
  safeClaim?: string;
  summary?: string;
  totalCharacters: number;
  windowCount: number;
  sourceTextHash?: string;
  windowSummaries: any[];
  metricCurves: Record<string, any>;
  driftPoints: any[];
  evidenceItems: any[];
  alternativeExplanations: string[];
  rewriteTasks: any[];
  createdAt?: string;
}

export interface PlotQualityIssue {
  id: string;
  dimension: string;
  severity: string;
  riskScore: number;
  evidence?: string;
  whyItMatters?: string;
  minimalFix?: string;
}

export interface PlotQualityRun {
  id: string;
  storyId: string;
  manuscriptId: string;
  sceneId: string;
  chapterTitle?: string;
  sceneTitle?: string;
  chapterOrder: number;
  sceneOrder: number;
  status: "ACCEPTED" | "ACCEPTED_WITH_ISSUES" | "DEGRADED" | string;
  maxSeverity: "LOW" | "MEDIUM" | "HIGH" | "BLOCKING" | string;
  overallRiskScore: number;
  summary?: string;
  rewritePlan: string[];
  surgicalFixes: string[];
  revisionCandidateText?: string;
  revisionApplied: boolean;
  revisionAppliedAt?: string;
  createdAt?: string;
  issues: PlotQualityIssue[];
}

export interface PlotQualityTrendPoint {
  runId: string;
  sceneId: string;
  chapterTitle?: string;
  sceneTitle?: string;
  chapterOrder: number;
  sceneOrder: number;
  riskScore: number;
  maxSeverity?: string;
  status?: string;
}

export interface PlotQualityTrend {
  manuscriptId: string;
  averageRisk: number;
  highRiskScenes: number;
  dimensionCounts: Record<string, number>;
  points: PlotQualityTrendPoint[];
}

export interface UserSummary {
  novelCount: number;
  worldCount: number;
  totalWords: number;
  totalEntries: number;
}

export interface CreditLedgerItem {
  id: string;
  type: string;
  delta: number;
  balanceAfter: number;
  referenceType?: string;
  referenceId?: string;
  description?: string;
  createdAt: string;
}

export interface CreditConversionRecord {
  id: string;
  orderNo: string;
  requestedAmount: number;
  convertedAmount: number;
  projectBefore: number;
  projectAfter: number;
  publicBefore: number;
  publicAfter: number;
  status: string;
  message?: string;
  createdAt: string;
}

// --- World Building Types ---

export interface WorldFieldDefinition {
  key: string;
  label: string;
  type: 'text' | 'textarea' | 'select';
  description?: string;
  placeholder?: string;
}

export interface WorldModuleDefinition {
  key: string;
  label: string;
  description: string;
  fields: WorldFieldDefinition[];
}

export interface WorldModuleData {
  key: string;
  fields: Record<string, string>; // fieldKey -> value
}

export interface WorldDetail extends World {
  themes: string[];
  creativeIntent: string;
  notes: string;
  modules: WorldModuleData[];
}

export interface PromptTemplates {
  storyCreation: string;
  outlineChapter: string;
  manuscriptSection: string;
  refineWithInstruction: string;
  refineWithoutInstruction: string;
}

export interface WorldPromptTemplates {
  modules: Record<string, string>; // moduleKey -> template
  finalTemplates: Record<string, string>;
  fieldRefine: string;
}

export interface PromptMetadata {
  syntaxTips: { name: string; description: string }[];
  functions: { name: string; description: string; example: string }[];
  templates: { key: string; variables: { name: string; type: string; description: string }[] }[];
  examples: string[];
}

export interface WorldPromptMetadata {
  variables: { name: string; type: string; description: string }[];
  functions: { name: string; description: string; example: string }[];
  modules: { key: string; label: string; fields: { key: string; label: string; maxLength: number }[] }[];
  examples: string[];
}

export interface MaterialDuplicateCandidate {
  sourceMaterialId: string;
  targetMaterialId: string;
  sourceTitle: string;
  targetTitle: string;
  score: number;
  reasons: string[];
}

export interface MaterialCitation {
  materialId: string;
  storyId: string;
  storyTitle: string;
  manuscriptId: string;
  sceneId: string;
  chapterTitle: string;
  sceneTitle: string;
  snippet: string;
  reason: string;
}

export interface AdminSlopReviewSample {
  id: string;
  sourceType: "MANUAL" | "SLOP_RUN" | string;
  sourceRunId?: string | null;
  storyId?: string | null;
  manuscriptId?: string | null;
  sceneId?: string | null;
  sampleId?: string | null;
  text: string;
  textPreview?: string;
  genre?: string;
  tone?: string;
  chapterTitle?: string;
  sceneTitle?: string;
  characterContext?: string;
  styleContext?: string;
  expectedEvidenceLevel: string;
  expectedRequiresAiReview: boolean;
  observedEvidenceLevel: string;
  observedRequiresAiReview: boolean;
  observedRiskScore: number;
  observedMaxSeverity: string;
  matchesExpected: boolean;
  status: "PENDING" | "APPROVED" | "REJECTED" | "NEEDS_DISCUSSION" | string;
  reviewerNote?: string;
  createdBy?: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

// --- V2 Admin & Economy Types ---

export interface ModelConfig {
  id: string;
  name: string; // Internal ID, e.g., 'gpt-4'
  displayName: string; // UI Name, e.g., 'GPT-4 Turbo'
  modelType?: string; // text | embedding | unspecified
  inputMultiplier: number; // e.g., 1.0
  outputMultiplier: number; // e.g., 3.0
  poolId: string;
  isEnabled: boolean;
}

export interface ApiPool {
  id: string;
  name: string;
  apis: {
    id: string;
    baseUrl: string;
    weight: number;
    status: 'active' | 'error';
  }[];
}

export interface AdminDashboardStats {
  totalUsers: number;
  todayNewUsers: number;
  totalCreditsConsumed: number;
  todayCreditsConsumed: number;
  apiErrorRate: number;
  pendingReviews: number;
}
