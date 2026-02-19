import { useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Terminal, Globe, Palette, Cpu, Keyboard } from "lucide-react";
import { useSearchParams } from "react-router-dom";

import WorkspacePrompts from "./tabs/WorkspacePrompts";
import WorldPrompts from "./tabs/WorldPrompts";
import StyleProfiles from "./tabs/StyleProfiles";
import ModelPreferences from "./tabs/ModelPreferences";
import WorkspaceExperience from "./tabs/WorkspaceExperience";

const VALID_TABS = new Set(["workspace", "world", "style", "models", "experience"]);

export const normalizeSettingsTabParam = (raw: string | null) => {
  const value = String(raw || "").trim().toLowerCase();
  if (!value) return "workspace";
  if (value === "model") return "models";
  if (VALID_TABS.has(value)) return value;
  return "workspace";
};

const Settings = () => {
  const [params] = useSearchParams();
  const initialTab = normalizeSettingsTabParam(params.get("tab"));
  const initialStoryId = params.get("storyId") || undefined;
  const [activeTab, setActiveTab] = useState(initialTab);

  return (
    <div className="h-full flex flex-col space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">系统设置</h1>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 flex flex-col">
        <TabsList className="grid w-full grid-cols-5 lg:w-[980px]">
          <TabsTrigger value="workspace" className="gap-2">
            <Terminal className="h-4 w-4" /> 工作区提示词
          </TabsTrigger>
          <TabsTrigger value="world" className="gap-2">
            <Globe className="h-4 w-4" /> 世界观提示词
          </TabsTrigger>
          <TabsTrigger value="style" className="gap-2">
            <Palette className="h-4 w-4" /> 风格画像
          </TabsTrigger>
          <TabsTrigger value="models" className="gap-2">
            <Cpu className="h-4 w-4" /> 模型偏好
          </TabsTrigger>
          <TabsTrigger value="experience" className="gap-2">
            <Keyboard className="h-4 w-4" /> 工作台体验
          </TabsTrigger>
        </TabsList>

        <div className="flex-1 mt-6">
          <TabsContent value="workspace" className="m-0">
            <WorkspacePrompts />
          </TabsContent>
          <TabsContent value="world" className="m-0">
            <WorldPrompts />
          </TabsContent>
          <TabsContent value="style" className="m-0">
            <StyleProfiles initialStoryId={initialStoryId} />
          </TabsContent>
          <TabsContent value="models" className="m-0">
            <ModelPreferences />
          </TabsContent>
          <TabsContent value="experience" className="m-0">
            <WorkspaceExperience />
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
};

export default Settings;
