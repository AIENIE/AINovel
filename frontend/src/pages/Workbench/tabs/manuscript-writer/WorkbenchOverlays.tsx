import { useTranslation } from "react-i18next";
import { Focus, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator, CommandShortcut } from "@/components/ui/command";

type WorkbenchOverlaysProps = {
  characters: Array<{ id: string; name?: string }>;
  commandQuery: string;
  focusMode: boolean;
  isCommandOpen: boolean;
  isMobile: boolean;
  leftPanelOpen: boolean;
  onChangeCommandOpen: (open: boolean) => void;
  onChangeCommandQuery: (value: string) => void;
  onHandleManualSave: () => Promise<void> | void;
  onJumpScene: (offset: number) => void;
  onOpenCharacterContext: (name: string) => void;
  onSelectCommandScene: (sceneId: string) => void;
  onToggleFocusMode: () => void;
  onToggleLeftPanelOpen: () => void;
  onToggleSidebarOpen: () => void;
  sceneRows: Array<{ id: string; displayName: string }>;
  shortcuts: Record<string, string>;
};

export function WorkbenchOverlays({
  characters,
  commandQuery,
  focusMode,
  isCommandOpen,
  isMobile,
  leftPanelOpen,
  onChangeCommandOpen,
  onChangeCommandQuery,
  onHandleManualSave,
  onJumpScene,
  onOpenCharacterContext,
  onSelectCommandScene,
  onToggleFocusMode,
  onToggleLeftPanelOpen,
  onToggleSidebarOpen,
  sceneRows,
  shortcuts,
}: WorkbenchOverlaysProps) {
  const { t } = useTranslation();
  return (
    <>
      {!focusMode && !isMobile && (
        <div className="absolute top-2 left-1/2 -translate-x-1/2 flex items-center gap-1">
          <Button size="icon" variant="ghost" className="h-7 w-7" onClick={onToggleLeftPanelOpen} title={leftPanelOpen ? t("overlays.collapseLeft") : t("overlays.expandLeft")}>
            {leftPanelOpen ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="h-7 w-7"
            onClick={onToggleFocusMode}
            title={focusMode ? t("overlays.exitFocus") : t("overlays.focusMode")}
          >
            <Focus className="h-4 w-4" />
          </Button>
        </div>
      )}

      <CommandDialog open={isCommandOpen} onOpenChange={onChangeCommandOpen}>
        <CommandInput placeholder={t("overlays.commandPlaceholder")} value={commandQuery} onValueChange={onChangeCommandQuery} />
        <CommandList>
          <CommandEmpty>{t("overlays.noMatch")}</CommandEmpty>
          <CommandGroup heading={t("overlays.quickActions")}>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); void onHandleManualSave(); }}>{t("overlays.saveCurrentScene")}<CommandShortcut>{shortcuts.save}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onToggleSidebarOpen(); }}>{t("overlays.toggleSidebar")}<CommandShortcut>{shortcuts.toggle_right_panel}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onToggleFocusMode(); }}>{t("overlays.toggleFocus")}<CommandShortcut>{shortcuts.focus_mode}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onJumpScene(1); }}>{t("overlays.nextScene")}<CommandShortcut>{shortcuts.next_chapter}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onJumpScene(-1); }}>{t("overlays.prevScene")}<CommandShortcut>{shortcuts.prev_chapter}</CommandShortcut></CommandItem>
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading={t("overlays.sceneJump")}>
            {sceneRows.map((row) => (
              <CommandItem key={row.id} onSelect={() => { onSelectCommandScene(row.id); onChangeCommandOpen(false); }}>
                {row.displayName}
              </CommandItem>
            ))}
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading={t("overlays.characterSearch")}>
            {characters.map((character) => (
              <CommandItem
                key={character.id}
                onSelect={() => {
                  onChangeCommandOpen(false);
                  onOpenCharacterContext(character.name || "");
                }}
              >
                {character.name || t("overlays.unnamedCharacter")}
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </>
  );
}
