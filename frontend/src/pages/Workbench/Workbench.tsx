import { useCallback, useEffect, useMemo, useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Sparkles, Book, FileText, PenTool, Search, Rocket, BookOpen, Network, ClipboardCheck } from "lucide-react";
import { useSearchParams } from "react-router-dom";

// Tabs Components
import StoryConception from "./tabs/StoryConception";
import StoryManager from "./tabs/StoryManager";
import OutlineWorkbench from "./tabs/OutlineWorkbench";
import ManuscriptWriter from "./tabs/ManuscriptWriter";
import MaterialSearchPanel from "./tabs/MaterialSearchPanel";
import V2Studio from "./tabs/V2Studio";
import LorebookPanel from "./tabs/LorebookPanel";
import KnowledgeGraphTab from "./tabs/KnowledgeGraphTab";
import AnalysisDashboard from "./tabs/AnalysisDashboard";

const Workbench = () => {
  const [params, setParams] = useSearchParams();
  const storyId = params.get("storyId") || params.get("id") || "";
  const requestedTab = params.get("tab") || "";
  const initialTab = useMemo(() => {
    const allowlist = new Set([
      "conception",
      "stories",
      "outline",
      "writing",
      "search",
      "lorebook",
      "graph",
      "analysis",
      "v2",
    ]);
    if (allowlist.has(requestedTab)) {
      return requestedTab;
    }
    return "writing";
  }, [requestedTab]);
  const [activeTab, setActiveTab] = useState(initialTab);

  useEffect(() => {
    setActiveTab(initialTab);
  }, [initialTab]);

  useEffect(() => {
    if (!params.has("id")) return;
    const next = new URLSearchParams(params);
    if (storyId) next.set("storyId", storyId);
    next.delete("id");
    setParams(next, { replace: true });
  }, [params, setParams, storyId]);

  const selectTab = (value: string) => {
    setActiveTab(value);
    const next = new URLSearchParams(params);
    next.set("tab", value);
    setParams(next, { replace: false });
  };
  const updateContext = useCallback((selection: Partial<Record<"storyId" | "outlineId" | "manuscriptId" | "sceneId", string>>) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      const currentStoryId = next.get("storyId") || next.get("id") || "";
      const currentOutlineId = next.get("outlineId") || "";
      const currentManuscriptId = next.get("manuscriptId") || "";
      if (selection.storyId !== undefined && selection.storyId !== currentStoryId) {
        next.delete("outlineId");
        next.delete("manuscriptId");
        next.delete("sceneId");
      } else if (selection.outlineId !== undefined && selection.outlineId !== currentOutlineId) {
        next.delete("manuscriptId");
        next.delete("sceneId");
      } else if (selection.manuscriptId !== undefined && selection.manuscriptId !== currentManuscriptId) {
        next.delete("sceneId");
      }
      Object.entries(selection).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
      next.delete("id");
      return next;
    }, { replace: true });
  }, [setParams]);

  return (
    <div className="h-full flex flex-col space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">创作工作台</h1>
      </div>

      <Tabs value={activeTab} onValueChange={selectTab} className="flex-1 flex flex-col min-w-0">
        <div className="space-y-3 border-b pb-4">
          <div className="flex min-w-0 items-center gap-3">
            <span className="w-14 shrink-0 text-xs font-medium text-muted-foreground">主流程</span>
            <TabsList className="h-auto flex-1 justify-start overflow-x-auto whitespace-nowrap bg-transparent p-0">
          <TabsTrigger value="conception" className="gap-2 shrink-0">
            <Sparkles className="h-4 w-4" /> 故事构思
          </TabsTrigger>
          <TabsTrigger value="stories" className="gap-2 shrink-0">
            <Book className="h-4 w-4" /> 故事管理
          </TabsTrigger>
          <TabsTrigger value="outline" className="gap-2 shrink-0">
            <FileText className="h-4 w-4" /> 大纲编排
          </TabsTrigger>
          <TabsTrigger value="writing" className="gap-2 shrink-0">
            <PenTool className="h-4 w-4" /> 小说创作
          </TabsTrigger>
            </TabsList>
          </div>
          <div className="flex min-w-0 items-center gap-3">
            <span className="w-14 shrink-0 text-xs font-medium text-muted-foreground">工具</span>
            <TabsList className="h-auto flex-1 justify-start overflow-x-auto whitespace-nowrap bg-transparent p-0">
          <TabsTrigger value="search" className="gap-2 shrink-0">
            <Search className="h-4 w-4" /> 素材检索
          </TabsTrigger>
          <TabsTrigger value="lorebook" className="gap-2 shrink-0">
            <BookOpen className="h-4 w-4" /> 知识库
          </TabsTrigger>
          <TabsTrigger value="graph" className="gap-2 shrink-0">
            <Network className="h-4 w-4" /> 知识图谱
          </TabsTrigger>
          <TabsTrigger value="analysis" className="gap-2 shrink-0">
            <ClipboardCheck className="h-4 w-4" /> 质量分析
          </TabsTrigger>
            </TabsList>
          </div>
          <div className="flex min-w-0 items-center gap-3">
            <span className="w-14 shrink-0 text-xs font-medium text-muted-foreground">高级</span>
            <TabsList className="h-auto justify-start bg-transparent p-0">
          <TabsTrigger value="v2" className="gap-2 shrink-0">
            <Rocket className="h-4 w-4" /> 高级工作台
          </TabsTrigger>
            </TabsList>
          </div>
        </div>

        <div className="flex-1 min-w-0 mt-6 bg-card rounded-xl border p-3 sm:p-6 min-h-[500px] overflow-hidden">
          <TabsContent value="conception" className="h-full m-0 border-0 p-0">
            <StoryConception />
          </TabsContent>
          <TabsContent value="stories" className="h-full m-0 border-0 p-0">
            <StoryManager initialStoryId={storyId} onStoryChange={(id) => updateContext({ storyId: id })} />
          </TabsContent>
          <TabsContent value="outline" className="h-full m-0 border-0 p-0">
            <OutlineWorkbench initialStoryId={storyId} />
          </TabsContent>
          <TabsContent value="writing" className="h-full m-0 border-0 p-0">
            <ManuscriptWriter
              initialStoryId={storyId}
              initialOutlineId={params.get("outlineId") || undefined}
              initialManuscriptId={params.get("manuscriptId") || undefined}
              initialSceneId={params.get("sceneId") || undefined}
              onSelectionChange={updateContext}
            />
          </TabsContent>
          <TabsContent value="search" className="h-full m-0 border-0 p-0">
            <MaterialSearchPanel />
          </TabsContent>
          <TabsContent value="lorebook" className="h-full m-0 border-0 p-0">
            <LorebookPanel initialStoryId={storyId} />
          </TabsContent>
          <TabsContent value="graph" className="h-full m-0 border-0 p-0">
            <KnowledgeGraphTab initialStoryId={storyId} />
          </TabsContent>
          <TabsContent value="analysis" className="h-full m-0 border-0 p-0">
            <AnalysisDashboard />
          </TabsContent>
          <TabsContent value="v2" className="h-full m-0 border-0 p-0">
            <V2Studio />
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
};

export default Workbench;
