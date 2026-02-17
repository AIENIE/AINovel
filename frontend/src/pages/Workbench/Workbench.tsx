import { useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Sparkles, Book, FileText, PenTool, Search, Rocket } from "lucide-react";
import { useSearchParams } from "react-router-dom";

// Tabs Components
import StoryConception from "./tabs/StoryConception";
import StoryManager from "./tabs/StoryManager";
import OutlineWorkbench from "./tabs/OutlineWorkbench";
import ManuscriptWriter from "./tabs/ManuscriptWriter";
import MaterialSearchPanel from "./tabs/MaterialSearchPanel";
import V2Studio from "./tabs/V2Studio";

const Workbench = () => {
  const [params] = useSearchParams();
  const storyId = params.get("id") || "";
  const [activeTab, setActiveTab] = useState(storyId ? "writing" : "conception");

  return (
    <div className="h-full flex flex-col space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">创作工作台</h1>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 flex flex-col">
        <TabsList className="grid w-full grid-cols-6 lg:w-[980px]">
          <TabsTrigger value="conception" className="gap-2">
            <Sparkles className="h-4 w-4" /> 故事构思
          </TabsTrigger>
          <TabsTrigger value="stories" className="gap-2">
            <Book className="h-4 w-4" /> 故事管理
          </TabsTrigger>
          <TabsTrigger value="outline" className="gap-2">
            <FileText className="h-4 w-4" /> 大纲编排
          </TabsTrigger>
          <TabsTrigger value="writing" className="gap-2">
            <PenTool className="h-4 w-4" /> 小说创作
          </TabsTrigger>
          <TabsTrigger value="search" className="gap-2">
            <Search className="h-4 w-4" /> 素材检索
          </TabsTrigger>
          <TabsTrigger value="v2" className="gap-2">
            <Rocket className="h-4 w-4" /> v2工作台
          </TabsTrigger>
        </TabsList>

        <div className="flex-1 mt-6 bg-card rounded-xl border shadow-sm p-6 min-h-[500px]">
          <TabsContent value="conception" className="h-full m-0 border-0 p-0">
            <StoryConception />
          </TabsContent>
          <TabsContent value="stories" className="h-full m-0 border-0 p-0">
            <StoryManager initialStoryId={storyId} />
          </TabsContent>
          <TabsContent value="outline" className="h-full m-0 border-0 p-0">
            <OutlineWorkbench initialStoryId={storyId} />
          </TabsContent>
          <TabsContent value="writing" className="h-full m-0 border-0 p-0">
            <ManuscriptWriter initialStoryId={storyId} />
          </TabsContent>
          <TabsContent value="search" className="h-full m-0 border-0 p-0">
            <MaterialSearchPanel />
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
