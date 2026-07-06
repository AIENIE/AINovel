import React, { useState, useEffect, forwardRef, useImperativeHandle } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export interface SlashCommandItem {
  title: string;
  description: string;
  icon: React.ReactNode;
  command: (editor: any) => void;
}

interface SlashCommandListProps {
  items: SlashCommandItem[];
  command: (item: SlashCommandItem) => void;
}

const SlashCommandList = forwardRef((props: SlashCommandListProps, ref) => {
  const [selectedIndex, setSelectedIndex] = useState(0);

  const selectItem = (index: number) => {
    const item = props.items[index];
    if (item) {
      props.command(item);
    }
  };

  useEffect(() => {
    setSelectedIndex(0);
  }, [props.items]);

  useImperativeHandle(ref, () => ({
    onKeyDown: ({ event }: { event: KeyboardEvent }) => {
      if (event.key === "ArrowUp") {
        setSelectedIndex((selectedIndex + props.items.length - 1) % props.items.length);
        return true;
      }
      if (event.key === "ArrowDown") {
        setSelectedIndex((selectedIndex + 1) % props.items.length);
        return true;
      }
      if (event.key === "Enter") {
        selectItem(selectedIndex);
        return true;
      }
      return false;
    },
  }));

  return (
    <div className="z-50 min-w-[300px] overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md animate-in fade-in zoom-in-95 duration-200">
      <div className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
        基础块
      </div>
      {props.items.map((item, index) => (
        <Button
          key={index}
          variant="ghost"
          className={cn(
            "w-full justify-start h-auto py-2 px-2 text-sm",
            index === selectedIndex ? "bg-accent text-accent-foreground" : ""
          )}
          onClick={() => selectItem(index)}
        >
          <div className="mr-2 flex h-8 w-8 items-center justify-center rounded-md border bg-background">
            {item.icon}
          </div>
          <div className="flex flex-col items-start">
            <span className="font-medium">{item.title}</span>
            <span className="text-xs text-muted-foreground">{item.description}</span>
          </div>
        </Button>
      ))}
    </div>
  );
});

SlashCommandList.displayName = "SlashCommandList";

export default SlashCommandList;
