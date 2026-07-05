import { History, Loader2, PanelRightClose, PanelRightOpen, Save, Sparkles, X } from "lucide-react";
import TiptapEditor from "@/components/editor/TiptapEditor";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";
import type { PlotQualityRun, SlopQualityRun } from "@/types";
import { plotStatusClass, plotStatusText, qualityStatusClass, qualityStatusText } from "./shared";

type DesktopEditorPanelProps = {
  activeGoal: any;
  content: string;
  currentWordCount: number;
  dirtyScenes: Record<string, boolean>;
  draggingTabId: string;
  focusMode: boolean;
  isGenerating: boolean;
  isSaving: boolean;
  isSidebarOpen: boolean;
  lastSavedAt: string;
  onCloseSceneTab: (sceneId: string) => void;
  onEditorChange: (html: string) => void;
  onGenerateScene: () => Promise<void> | void;
  onHandleManualSave: () => Promise<void> | void;
  onOpenVersionPanel: () => void;
  onReorderOpenTabs: (fromId: string, toId: string) => void;
  onSelectScene: (sceneId: string) => void;
  onSetDraggingTabId: (sceneId: string) => void;
  onToggleSidebar: () => void;
  openSceneIds: string[];
  sceneMap: Record<string, any>;
  selectedManuscriptId: string;
  selectedPlotRun: PlotQualityRun | null;
  selectedQualityRun: SlopQualityRun | null;
  selectedSceneId: string;
  selectedWordCount: number;
  sessionDurationSeconds: number;
  sessionNetWords: number;
};

export function DesktopEditorPanel({
  activeGoal,
  content,
  currentWordCount,
  dirtyScenes,
  draggingTabId,
  focusMode,
  isGenerating,
  isSaving,
  isSidebarOpen,
  lastSavedAt,
  onCloseSceneTab,
  onEditorChange,
  onGenerateScene,
  onHandleManualSave,
  onOpenVersionPanel,
  onReorderOpenTabs,
  onSelectScene,
  onSetDraggingTabId,
  onToggleSidebar,
  openSceneIds,
  sceneMap,
  selectedManuscriptId,
  selectedPlotRun,
  selectedQualityRun,
  selectedSceneId,
  selectedWordCount,
  sessionDurationSeconds,
  sessionNetWords,
}: DesktopEditorPanelProps) {
  return (
    <div className="h-full flex flex-col min-w-0 transition-all duration-300">
      {!focusMode && (
        <div className="flex items-center justify-between mb-2 px-2 pt-2">
          <div className="text-sm text-muted-foreground">
            {isSaving ? "正在保存..." : lastSavedAt ? `上次保存: ${lastSavedAt}` : "未保存"}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={onOpenVersionPanel}><History className="mr-2 h-4 w-4" /> 历史版本</Button>
            <Button size="sm" onClick={() => void onHandleManualSave()} disabled={!selectedManuscriptId || !selectedSceneId}>
              <Save className="mr-2 h-4 w-4" /> 保存
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => void onGenerateScene()}
              disabled={isGenerating || !selectedSceneId || !selectedManuscriptId}
            >
              {isGenerating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
              生成本场景
            </Button>
            <Button variant="ghost" size="icon" onClick={onToggleSidebar} className="ml-2" title={isSidebarOpen ? "收起右栏" : "展开右栏"}>
              {isSidebarOpen ? <PanelRightClose className="h-4 w-4" /> : <PanelRightOpen className="h-4 w-4" />}
            </Button>
          </div>
        </div>
      )}

      <div className="px-2 pb-2 flex flex-wrap items-center gap-2 text-xs">
        <Badge variant="outline" className={cn("font-normal", qualityStatusClass(selectedQualityRun))}>
          {qualityStatusText(selectedQualityRun)}
        </Badge>
        <Badge variant="outline" className={cn("font-normal", plotStatusClass(selectedPlotRun))}>
          {plotStatusText(selectedPlotRun)}
        </Badge>
        {selectedQualityRun ? (
          <>
            <span className="text-muted-foreground">风险 {selectedQualityRun.overallRiskScore}</span>
            {selectedQualityRun.summary ? <span className="text-muted-foreground truncate max-w-[520px]">{selectedQualityRun.summary}</span> : null}
          </>
        ) : (
          <span className="text-muted-foreground">生成场景后会自动记录反 slop 检查结果</span>
        )}
      </div>

      <div className="px-2 pb-2">
        <ScrollArea className="w-full whitespace-nowrap">
          <div className="flex gap-2">
            {openSceneIds.map((sceneId) => {
              const scene = sceneMap[sceneId]?.scene;
              if (!scene) return null;
              return (
                <div
                  key={sceneId}
                  draggable
                  onDragStart={() => onSetDraggingTabId(sceneId)}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => {
                    event.preventDefault();
                    onReorderOpenTabs(draggingTabId, sceneId);
                    onSetDraggingTabId("");
                  }}
                  className={cn(
                    "rounded-md border flex items-center",
                    selectedSceneId === sceneId ? "bg-secondary border-primary/40" : "bg-muted/40",
                  )}
                >
                  <button type="button" className="px-2 py-1 text-sm" onClick={() => onSelectScene(sceneId)}>
                    {scene.title}
                    {dirtyScenes[sceneId] ? " *" : ""}
                  </button>
                  {openSceneIds.length > 1 && <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => onCloseSceneTab(sceneId)}><X className="h-3 w-3" /></Button>}
                </div>
              );
            })}
          </div>
        </ScrollArea>
      </div>

      <div className="flex-1 border rounded-lg overflow-hidden bg-background shadow-sm mx-2">
        <TiptapEditor
          key={`desktop-editor-${focusMode ? "zen" : "normal"}`}
          content={content}
          onChange={onEditorChange}
          className="h-full"
          editable={!!selectedSceneId}
          zenMode={focusMode}
        />
      </div>

      {!focusMode && (
        <div className="h-8 mt-2 border-t px-3 flex items-center justify-between text-xs text-muted-foreground bg-muted/30">
          <div className="flex items-center gap-3">
            <span>字数 {currentWordCount}</span>
            <span>选中 {selectedWordCount}</span>
            <span>会话 {Math.floor(sessionDurationSeconds / 60)}m{sessionDurationSeconds % 60}s</span>
          </div>
          <div className="flex items-center gap-3">
            <span>净增 {sessionNetWords}</span>
            {!!activeGoal && <span>目标 {activeGoal.currentValue || 0}/{activeGoal.targetValue || 0}</span>}
          </div>
        </div>
      )}
    </div>
  );
}
