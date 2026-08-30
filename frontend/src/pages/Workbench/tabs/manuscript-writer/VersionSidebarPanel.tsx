import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { Dispatch, SetStateAction } from "react";
import { Archive, ArrowDownUp, Check, Clock3, Edit, Flag, GitBranch, RotateCcw, Split, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";
import { formatDateTime, snapshotTypeLabel, versionWordCount } from "./shared";
import type { NetworkObject } from "@/lib/api-client";

type VersionSidebarPanelProps = {
  abandonBranch: (branchId: string) => Promise<void> | void;
  aiDiffSummary: string;
  autoSaveConfig: NetworkObject | null;
  branches: NetworkObject[];
  createBranch: () => Promise<void> | void;
  createManualVersion: () => Promise<void> | void;
  checkoutBranch: (branchId: string) => Promise<void> | void;
  currentBranchId: string;
  diffResult: NetworkObject | null;
  diffViewMode: "split" | "unified";
  hasMoreVersions: boolean;
  loadVersions: () => Promise<unknown> | void;
  mergeBranchId: string;
  mergeConflicts: NetworkObject[];
  mergeSelectedBranch: (resolutions?: Record<string, "target" | "source">) => Promise<void> | void;
  mergeStrategy: "REPLACE_ALL" | "SCENE_SELECT";
  newBranchName: string;
  rollbackVersion: (versionId: string) => Promise<void> | void;
  runVersionDiff: () => Promise<void> | void;
  saveAutoSaveConfig: () => Promise<void> | void;
  sceneResolutions: Record<string, "target" | "source">;
  selectedDiffVersions: string[];
  selectedManuscriptId: string;
  setAutoSaveConfig: Dispatch<SetStateAction<NetworkObject>>;
  setDiffViewMode: Dispatch<SetStateAction<"split" | "unified">>;
  setMergeBranchId: Dispatch<SetStateAction<string>>;
  setMergeStrategy: Dispatch<SetStateAction<"REPLACE_ALL" | "SCENE_SELECT">>;
  setNewBranchName: Dispatch<SetStateAction<string>>;
  setSceneResolutions: Dispatch<SetStateAction<Record<string, "target" | "source">>>;
  setVersionVisibleCount: Dispatch<SetStateAction<number>>;
  summarizeDiff: () => Promise<void> | void;
  toggleVersionSelection: (versionId: string) => void;
  versionPageSize: number;
  visibleVersions: NetworkObject[];
  updateBranch: (branchId: string, patch: Record<string, unknown>) => Promise<void> | void;
};

export function VersionSidebarPanel({
  abandonBranch,
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
  updateBranch,
}: VersionSidebarPanelProps) {
  const [editingBranchId, setEditingBranchId] = useState("");
  const [editingBranchName, setEditingBranchName] = useState("");
  const { t } = useTranslation();
  return (
    <TabsContent value="version" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <div className="space-y-2 mb-2">
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={() => void loadVersions()}>
            {t("common.refresh")}
          </Button>
          <Button size="sm" onClick={() => void createManualVersion()} disabled={!selectedManuscriptId}>
            <Flag className="h-3.5 w-3.5 mr-1" />
            {t("versionPanel.checkpoint")}
          </Button>
          <Button size="sm" variant="secondary" onClick={() => void runVersionDiff()} disabled={selectedDiffVersions.length !== 2}>
            {t("versionPanel.diff")}
          </Button>
        </div>
        <div className="rounded border p-2 space-y-2 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-medium">{t("versionPanel.branchManage")}</span>
            <Badge variant="outline">{t("versionPanel.current")} {currentBranchId ? currentBranchId.slice(0, 8) : "-"}</Badge>
          </div>
          <div className="flex gap-2">
            <Input value={newBranchName} onChange={(event) => setNewBranchName(event.target.value)} placeholder={t("versionPanel.branchName")} className="h-8" />
            <Button size="sm" onClick={() => void createBranch()}>
              <GitBranch className="h-3.5 w-3.5 mr-1" />
              {t("versionPanel.newBranch")}
            </Button>
          </div>
          <div className="space-y-1">
            {branches.map((branch) => (
              <div key={String(branch.id)} className="flex items-center justify-between gap-1 rounded border p-1">
                {editingBranchId === String(branch.id) ? <Input className="h-7 min-w-0 flex-1" value={editingBranchName} onChange={(event) => setEditingBranchName(event.target.value)} /> : <div className="truncate mr-2">
                  <span className="font-medium">{branch.name}</span>
                  <span className="text-muted-foreground ml-1">{branch.status}</span>
                </div>}
                {editingBranchId === String(branch.id) ? <><Button size="icon" variant="ghost" className="h-7 w-7" disabled={!editingBranchName.trim()} onClick={async () => { await updateBranch(String(branch.id), { name: editingBranchName.trim() }); setEditingBranchId(""); }}><Check className="h-3.5 w-3.5" /></Button><Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => setEditingBranchId("")}><X className="h-3.5 w-3.5" /></Button></> : <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => { setEditingBranchId(String(branch.id)); setEditingBranchName(String(branch.name || "")); }}><Edit className="h-3.5 w-3.5" /></Button>}
                <Button size="sm" variant="outline" className="h-6 px-2" onClick={() => void checkoutBranch(String(branch.id))} disabled={String(branch.status) !== "active"}>
                  {t("versionPanel.switch")}
                </Button>
                {!branch.isMain && String(branch.status) === "active" ? <Button size="icon" variant="ghost" className="h-7 w-7 text-destructive" title={t("versionPanel.abandonBranch")} onClick={() => { if (confirm(t("versionPanel.abandonBranchConfirm", { name: branch.name }))) void abandonBranch(String(branch.id)); }}><Archive className="h-3.5 w-3.5" /></Button> : null}
              </div>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Select value={mergeBranchId} onValueChange={setMergeBranchId}>
              <SelectTrigger className="h-8">
                <SelectValue placeholder={t("versionPanel.selectBranch")} />
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
                <SelectItem value="REPLACE_ALL">{t("versionPanel.replaceAll")}</SelectItem>
                <SelectItem value="SCENE_SELECT">{t("versionPanel.sceneSelect")}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Button size="sm" variant="secondary" onClick={() => void mergeSelectedBranch()} disabled={!mergeBranchId}>
            {t("versionPanel.mergeToMain")}
          </Button>
          {!!mergeConflicts.length && (
            <div className="space-y-2 rounded border border-amber-300 bg-amber-50 p-2">
              <div className="text-amber-700">{t("versionPanel.conflictDetected")}</div>
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
                      <SelectValue placeholder={t("versionPanel.selectKeepVersion")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="target">{t("versionPanel.keepMain")}</SelectItem>
                      <SelectItem value="source">{t("versionPanel.keepBranch")}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              ))}
              <Button size="sm" onClick={() => void mergeSelectedBranch(sceneResolutions)}>
                {t("versionPanel.submitResolution")}
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
                <span className="font-medium">{`v${Number(version.versionNumber || index + 1)} ${version.label || t("versionPanel.unnamed")}`}</span>
                <Badge variant="outline">{snapshotTypeLabel(version.snapshotType)}</Badge>
                <Button size="sm" variant="ghost" className="ml-auto h-6 px-2" onClick={() => void rollbackVersion(String(version.id))}>
                  <RotateCcw className="h-3 w-3 mr-1" />
                  {t("versionPanel.rollback")}
                </Button>
              </div>
              <div className="text-muted-foreground">{`${formatDateTime(version.createdAt)} · ${t("versionPanel.wordCount", { count: versionWordCount(version) })}`}</div>
            </div>
          </div>
        ))}
        {hasMoreVersions && (
          <Button size="sm" variant="outline" className="w-full h-7" onClick={() => setVersionVisibleCount((prev) => prev + versionPageSize)}>
            {t("versionPanel.loadMore")}
          </Button>
        )}
        {!!diffResult && (
          <div className="rounded border p-2 space-y-2 text-xs">
            <div className="flex gap-1">
              <Button size="sm" variant={diffViewMode === "split" ? "default" : "outline"} className="h-6 px-2" onClick={() => setDiffViewMode("split")}>
                <Split className="h-3 w-3 mr-1" />
                {t("versionPanel.split")}
              </Button>
              <Button size="sm" variant={diffViewMode === "unified" ? "default" : "outline"} className="h-6 px-2" onClick={() => setDiffViewMode("unified")}>
                <ArrowDownUp className="h-3 w-3 mr-1" />
                {t("versionPanel.unified")}
              </Button>
              <Button size="sm" variant="secondary" className="h-6 px-2" onClick={() => void summarizeDiff()}>
                {t("versionPanel.aiSummary")}
              </Button>
            </div>
            {!!aiDiffSummary && <div className="rounded bg-muted p-2">{aiDiffSummary}</div>}
            {(diffResult.changes || []).slice(0, 6).map((change: NetworkObject) => (
              <div key={change.sceneId} className="rounded border p-2">
                <div className="font-medium mb-1">{t("versionPanel.scene", { id: change.sceneId })}</div>
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
            <div className="font-medium">{t("versionPanel.autoSnapshot")}</div>
            <Input
              type="number"
              value={Number(autoSaveConfig.autoSaveIntervalSeconds || 300)}
              onChange={(event) =>
                setAutoSaveConfig((prev: NetworkObject) => ({ ...prev, autoSaveIntervalSeconds: Number(event.target.value || 300) }))
              }
            />
            <Input
              type="number"
              value={Number(autoSaveConfig.maxAutoVersions || 100)}
              onChange={(event) => setAutoSaveConfig((prev: NetworkObject) => ({ ...prev, maxAutoVersions: Number(event.target.value || 100) }))}
            />
            <Button size="sm" onClick={() => void saveAutoSaveConfig()}>
              {t("versionPanel.saveConfig")}
            </Button>
          </div>
        )}
      </ScrollArea>
    </TabsContent>
  );
}
