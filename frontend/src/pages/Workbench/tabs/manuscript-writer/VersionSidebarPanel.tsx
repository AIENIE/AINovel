import type { Dispatch, SetStateAction } from "react";
import { ArrowDownUp, Clock3, Flag, GitBranch, RotateCcw, Split } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";
import { formatDateTime, snapshotTypeLabel, versionWordCount } from "./shared";

type VersionSidebarPanelProps = {
  aiDiffSummary: string;
  autoSaveConfig: any;
  branches: any[];
  createBranch: () => Promise<void> | void;
  createManualVersion: () => Promise<void> | void;
  checkoutBranch: (branchId: string) => Promise<void> | void;
  currentBranchId: string;
  diffResult: any;
  diffViewMode: "split" | "unified";
  hasMoreVersions: boolean;
  loadVersions: () => Promise<void> | void;
  mergeBranchId: string;
  mergeConflicts: any[];
  mergeSelectedBranch: (resolutions?: Record<string, "target" | "source">) => Promise<void> | void;
  mergeStrategy: "REPLACE_ALL" | "SCENE_SELECT";
  newBranchName: string;
  rollbackVersion: (versionId: string) => Promise<void> | void;
  runVersionDiff: () => Promise<void> | void;
  saveAutoSaveConfig: () => Promise<void> | void;
  sceneResolutions: Record<string, "target" | "source">;
  selectedDiffVersions: string[];
  selectedManuscriptId: string;
  setAutoSaveConfig: Dispatch<SetStateAction<any>>;
  setDiffViewMode: Dispatch<SetStateAction<"split" | "unified">>;
  setMergeBranchId: Dispatch<SetStateAction<string>>;
  setMergeStrategy: Dispatch<SetStateAction<"REPLACE_ALL" | "SCENE_SELECT">>;
  setNewBranchName: Dispatch<SetStateAction<string>>;
  setSceneResolutions: Dispatch<SetStateAction<Record<string, "target" | "source">>>;
  setVersionVisibleCount: Dispatch<SetStateAction<number>>;
  summarizeDiff: () => Promise<void> | void;
  toggleVersionSelection: (versionId: string) => void;
  versionPageSize: number;
  visibleVersions: any[];
};

export function VersionSidebarPanel({
  aiDiffSummary,
  autoSaveConfig,
  branches,
  createBranch,
  createManualVersion,
  checkoutBranch,
  currentBranchId,
  diffResult,
  diffViewMode,
  hasMoreVersions,
  loadVersions,
  mergeBranchId,
  mergeConflicts,
  mergeSelectedBranch,
  mergeStrategy,
  newBranchName,
  rollbackVersion,
  runVersionDiff,
  saveAutoSaveConfig,
  sceneResolutions,
  selectedDiffVersions,
  selectedManuscriptId,
  setAutoSaveConfig,
  setDiffViewMode,
  setMergeBranchId,
  setMergeStrategy,
  setNewBranchName,
  setSceneResolutions,
  setVersionVisibleCount,
  summarizeDiff,
  toggleVersionSelection,
  versionPageSize,
  visibleVersions,
}: VersionSidebarPanelProps) {
  return (
    <TabsContent value="version" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <div className="space-y-2 mb-2">
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={() => void loadVersions()}>
            刷新
          </Button>
          <Button size="sm" onClick={() => void createManualVersion()} disabled={!selectedManuscriptId}>
            <Flag className="h-3.5 w-3.5 mr-1" />
            检查点
          </Button>
          <Button size="sm" variant="secondary" onClick={() => void runVersionDiff()} disabled={selectedDiffVersions.length !== 2}>
            对比
          </Button>
        </div>
        <div className="rounded border p-2 space-y-2 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-medium">分支管理</span>
            <Badge variant="outline">当前 {currentBranchId ? currentBranchId.slice(0, 8) : "-"}</Badge>
          </div>
          <div className="flex gap-2">
            <Input value={newBranchName} onChange={(event) => setNewBranchName(event.target.value)} placeholder="分支名称" className="h-8" />
            <Button size="sm" onClick={() => void createBranch()}>
              <GitBranch className="h-3.5 w-3.5 mr-1" />
              新建分支
            </Button>
          </div>
          <div className="space-y-1">
            {branches.map((branch) => (
              <div key={String(branch.id)} className="flex items-center justify-between rounded border p-1">
                <div className="truncate mr-2">
                  <span className="font-medium">{branch.name}</span>
                  <span className="text-muted-foreground ml-1">{branch.status}</span>
                </div>
                <Button size="sm" variant="outline" className="h-6 px-2" onClick={() => void checkoutBranch(String(branch.id))} disabled={String(branch.status) !== "active"}>
                  切换
                </Button>
              </div>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Select value={mergeBranchId} onValueChange={setMergeBranchId}>
              <SelectTrigger className="h-8">
                <SelectValue placeholder="选择分支" />
              </SelectTrigger>
              <SelectContent>
                {branches
                  .filter((branch) => !branch.isMain && String(branch.status) === "active")
                  .map((branch) => (
                    <SelectItem key={String(branch.id)} value={String(branch.id)}>
                      {branch.name}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
            <Select value={mergeStrategy} onValueChange={(value) => setMergeStrategy(value as "REPLACE_ALL" | "SCENE_SELECT")}>
              <SelectTrigger className="h-8">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="REPLACE_ALL">整体替换</SelectItem>
                <SelectItem value="SCENE_SELECT">逐场景选择</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Button size="sm" variant="secondary" onClick={() => void mergeSelectedBranch()} disabled={!mergeBranchId}>
            合并到主线
          </Button>
          {!!mergeConflicts.length && (
            <div className="space-y-2 rounded border border-amber-300 bg-amber-50 p-2">
              <div className="text-amber-700">检测到冲突，请逐场景选择保留版本。</div>
              {mergeConflicts.map((conflict) => (
                <div key={conflict.sceneId} className="rounded border p-2">
                  <div className="font-medium">{conflict.sceneId}</div>
                  <Select
                    value={sceneResolutions[conflict.sceneId] || ""}
                    onValueChange={(value) =>
                      setSceneResolutions((prev) => ({ ...prev, [conflict.sceneId]: value as "target" | "source" }))
                    }
                  >
                    <SelectTrigger className="h-7 mt-1">
                      <SelectValue placeholder="选择保留主线或分支" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="target">保留主线</SelectItem>
                      <SelectItem value="source">保留分支</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              ))}
              <Button size="sm" onClick={() => void mergeSelectedBranch(sceneResolutions)}>
                提交冲突解决
              </Button>
            </div>
          )}
        </div>
      </div>
      <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 space-y-2">
        {visibleVersions.map((version, index) => (
          <div key={String(version.id)} className="flex gap-2 text-xs">
            <div className="flex flex-col items-center pt-0.5">
              <Clock3 className="h-3.5 w-3.5 text-muted-foreground" />
              {index < visibleVersions.length - 1 && <div className="mt-1 w-px flex-1 min-h-4 bg-border" />}
            </div>
            <div className="flex-1 rounded border p-2 space-y-1">
              <div className="flex items-center gap-2">
                <Checkbox checked={selectedDiffVersions.includes(String(version.id))} onCheckedChange={() => toggleVersionSelection(String(version.id))} />
                <span className="font-medium">{`v${Number(version.versionNumber || index + 1)} ${version.label || "未命名版本"}`}</span>
                <Badge variant="outline">{snapshotTypeLabel(version.snapshotType)}</Badge>
                <Button size="sm" variant="ghost" className="ml-auto h-6 px-2" onClick={() => void rollbackVersion(String(version.id))}>
                  <RotateCcw className="h-3 w-3 mr-1" />
                  回滚
                </Button>
              </div>
              <div className="text-muted-foreground">{`${formatDateTime(version.createdAt)} · ${versionWordCount(version)} 字`}</div>
            </div>
          </div>
        ))}
        {hasMoreVersions && (
          <Button size="sm" variant="outline" className="w-full h-7" onClick={() => setVersionVisibleCount((prev) => prev + versionPageSize)}>
            加载更多版本
          </Button>
        )}
        {!!diffResult && (
          <div className="rounded border p-2 space-y-2 text-xs">
            <div className="flex gap-1">
              <Button size="sm" variant={diffViewMode === "split" ? "default" : "outline"} className="h-6 px-2" onClick={() => setDiffViewMode("split")}>
                <Split className="h-3 w-3 mr-1" />
                并排
              </Button>
              <Button size="sm" variant={diffViewMode === "unified" ? "default" : "outline"} className="h-6 px-2" onClick={() => setDiffViewMode("unified")}>
                <ArrowDownUp className="h-3 w-3 mr-1" />
                统一
              </Button>
              <Button size="sm" variant="secondary" className="h-6 px-2" onClick={() => void summarizeDiff()}>
                AI 总结
              </Button>
            </div>
            {!!aiDiffSummary && <div className="rounded bg-muted p-2">{aiDiffSummary}</div>}
            {(diffResult.changes || []).slice(0, 6).map((change: any) => (
              <div key={change.sceneId} className="rounded border p-2">
                <div className="font-medium mb-1">场景 {change.sceneId}</div>
                {diffViewMode === "split" ? (
                  <div className="grid grid-cols-2 gap-2">
                    <div className="bg-rose-50/40 rounded p-1 whitespace-pre-wrap">{change.beforeContent || "<empty>"}</div>
                    <div className="bg-emerald-50/40 rounded p-1 whitespace-pre-wrap">{change.afterContent || "<empty>"}</div>
                  </div>
                ) : (
                  <div className="space-y-1">
                    <div className="text-rose-600 whitespace-pre-wrap">- {change.beforeContent || "<empty>"}</div>
                    <div className="text-emerald-600 whitespace-pre-wrap">+ {change.afterContent || "<empty>"}</div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
        {!!autoSaveConfig && (
          <div className="rounded border p-2 text-xs space-y-2">
            <div className="font-medium">自动快照</div>
            <Input
              type="number"
              value={Number(autoSaveConfig.autoSaveIntervalSeconds || 300)}
              onChange={(event) =>
                setAutoSaveConfig((prev: any) => ({ ...prev, autoSaveIntervalSeconds: Number(event.target.value || 300) }))
              }
            />
            <Input
              type="number"
              value={Number(autoSaveConfig.maxAutoVersions || 100)}
              onChange={(event) => setAutoSaveConfig((prev: any) => ({ ...prev, maxAutoVersions: Number(event.target.value || 100) }))}
            />
            <Button size="sm" onClick={() => void saveAutoSaveConfig()}>
              保存配置
            </Button>
          </div>
        )}
      </ScrollArea>
    </TabsContent>
  );
}
