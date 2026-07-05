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
  return (
    <>
      {!focusMode && !isMobile && (
        <div className="absolute top-2 right-2 flex items-center gap-1">
          <Button size="icon" variant="ghost" className="h-7 w-7" onClick={onToggleLeftPanelOpen} title={leftPanelOpen ? "收起左栏" : "展开左栏"}>
            {leftPanelOpen ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="h-7 w-7"
            onClick={onToggleFocusMode}
            title={focusMode ? "退出专注" : "专注模式"}
          >
            <Focus className="h-4 w-4" />
          </Button>
        </div>
      )}

      <CommandDialog open={isCommandOpen} onOpenChange={onChangeCommandOpen}>
        <CommandInput placeholder="输入命令或场景..." value={commandQuery} onValueChange={onChangeCommandQuery} />
        <CommandList>
          <CommandEmpty>没有匹配命令</CommandEmpty>
          <CommandGroup heading="快捷操作">
            <CommandItem onSelect={() => { onChangeCommandOpen(false); void onHandleManualSave(); }}>保存当前场景<CommandShortcut>{shortcuts.save}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onToggleSidebarOpen(); }}>切换右侧面板<CommandShortcut>{shortcuts.toggle_right_panel}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onToggleFocusMode(); }}>切换专注模式<CommandShortcut>{shortcuts.focus_mode}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onJumpScene(1); }}>下一场景<CommandShortcut>{shortcuts.next_chapter}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { onChangeCommandOpen(false); onJumpScene(-1); }}>上一场景<CommandShortcut>{shortcuts.prev_chapter}</CommandShortcut></CommandItem>
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading="场景跳转">
            {sceneRows.map((row) => (
              <CommandItem key={row.id} onSelect={() => { onSelectCommandScene(row.id); onChangeCommandOpen(false); }}>
                {row.displayName}
              </CommandItem>
            ))}
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading="角色检索">
            {characters.map((character) => (
              <CommandItem
                key={character.id}
                onSelect={() => {
                  onChangeCommandOpen(false);
                  onOpenCharacterContext(character.name || "");
                }}
              >
                {character.name || "未命名角色"}
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </>
  );
}
