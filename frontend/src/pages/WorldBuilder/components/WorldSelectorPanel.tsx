import { World } from "@/types";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Plus, Globe, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

interface WorldSelectorPanelProps {
  worlds: World[];
  selectedWorldId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
}

const WorldSelectorPanel = ({ worlds, selectedWorldId, onSelect, onCreate }: WorldSelectorPanelProps) => {
  return (
    <div className="w-64 border-r bg-card flex flex-col h-full">
      <div className="p-4 border-b flex items-center justify-between">
        <h2 className="font-semibold flex items-center gap-2">
          <Globe className="h-4 w-4" /> 我的世界
        </h2>
        <Button variant="ghost" size="icon" onClick={onCreate} title="新建世界">
          <Plus className="h-4 w-4" />
        </Button>
      </div>
      
      <ScrollArea className="flex-1">
        <div className="p-2 space-y-1">
          {worlds.map((world) => (
            <button
              key={world.id}
              onClick={() => onSelect(world.id)}
              className={cn(
                "w-full flex items-center justify-between p-3 rounded-md text-sm transition-colors",
                selectedWorldId === world.id
                  ? "bg-primary/10 text-primary font-medium"
                  : "hover:bg-accent text-muted-foreground"
              )}
            >
              <div className="flex flex-col items-start truncate">
                <span className="truncate w-full text-left">{world.name}</span>
                <span className="text-xs opacity-70">v{world.version}</span>
              </div>
              {selectedWorldId === world.id && <ChevronRight className="h-4 w-4 opacity-50" />}
            </button>
          ))}
          
          {worlds.length === 0 && (
            <div className="text-center py-8 text-muted-foreground text-sm">
              暂无世界设定<br/>点击右上角新建
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  );
};

export default WorldSelectorPanel;
