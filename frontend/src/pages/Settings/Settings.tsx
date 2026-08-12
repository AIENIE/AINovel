import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const activeTab = normalizeSettingsTabParam(params.get("tab"));
  const initialStoryId = params.get("storyId") || undefined;
  const selectTab = (value: string) => {
    const next = new URLSearchParams(params);
    next.set("tab", value);
    setParams(next);
  };

  return (
    <div className="h-full flex flex-col space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">{t("settings.title")}</h1>
      </div>

      <Tabs value={activeTab} onValueChange={selectTab} className="flex-1 flex flex-col min-w-0">
        <TabsList className="flex h-auto w-full justify-start gap-1 overflow-x-auto lg:w-[980px]">
          <TabsTrigger value="workspace" className="gap-2 shrink-0">
            <Terminal className="h-4 w-4" /> {t("settings.workspacePrompts")}
          </TabsTrigger>
          <TabsTrigger value="world" className="gap-2 shrink-0">
            <Globe className="h-4 w-4" /> {t("settings.worldPrompts")}
          </TabsTrigger>
          <TabsTrigger value="style" className="gap-2 shrink-0">
            <Palette className="h-4 w-4" /> {t("settings.styleProfiles")}
          </TabsTrigger>
          <TabsTrigger value="models" className="gap-2 shrink-0">
            <Cpu className="h-4 w-4" /> {t("settings.modelPrefs")}
          </TabsTrigger>
          <TabsTrigger value="experience" className="gap-2 shrink-0">
            <Keyboard className="h-4 w-4" /> {t("settings.workspaceExperience")}
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
