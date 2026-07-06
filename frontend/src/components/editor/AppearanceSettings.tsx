import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Settings2, Type, Monitor, Palette } from "lucide-react";

export type FontType = "sans" | "serif" | "mono";
export type WidthType = "narrow" | "medium" | "wide";
export type ThemeType = "light" | "dark" | "parchment" | "hacker";

interface AppearanceSettingsProps {
  font: FontType;
  setFont: (font: FontType) => void;
  width: WidthType;
  setWidth: (width: WidthType) => void;
  theme: ThemeType;
  setTheme: (theme: ThemeType) => void;
}

const AppearanceSettings = ({
  font,
  setFont,
  width,
  setWidth,
  theme,
  setTheme,
}: AppearanceSettingsProps) => {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" title="外观设置">
          <Settings2 className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-4" align="end">
        <div className="space-y-6">
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Type className="h-4 w-4" /> 字体风格
            </div>
            <div className="grid grid-cols-3 gap-2">
              <Button
                variant={font === "sans" ? "default" : "outline"}
                size="sm"
                onClick={() => setFont("sans")}
                className="font-sans"
              >
                无衬线
              </Button>
              <Button
                variant={font === "serif" ? "default" : "outline"}
                size="sm"
                onClick={() => setFont("serif")}
                className="font-serif"
              >
                衬线体
              </Button>
              <Button
                variant={font === "mono" ? "default" : "outline"}
                size="sm"
                onClick={() => setFont("mono")}
                className="font-mono"
              >
                等宽
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Monitor className="h-4 w-4" /> 版心宽度
            </div>
            <div className="grid grid-cols-3 gap-2">
              <Button
                variant={width === "narrow" ? "default" : "outline"}
                size="sm"
                onClick={() => setWidth("narrow")}
              >
                窄
              </Button>
              <Button
                variant={width === "medium" ? "default" : "outline"}
                size="sm"
                onClick={() => setWidth("medium")}
              >
                标准
              </Button>
              <Button
                variant={width === "wide" ? "default" : "outline"}
                size="sm"
                onClick={() => setWidth("wide")}
              >
                宽
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Palette className="h-4 w-4" /> 阅读主题
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Button
                variant={theme === "light" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("light")}
                className="bg-white text-black border-gray-200 hover:bg-gray-100"
              >
                明亮
              </Button>
              <Button
                variant={theme === "dark" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("dark")}
                className="bg-zinc-900 text-white border-zinc-700 hover:bg-zinc-800"
              >
                深色
              </Button>
              <Button
                variant={theme === "parchment" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("parchment")}
                className="bg-[#f5e6c8] text-[#4a3b2a] border-[#e0d0b0] hover:bg-[#e6d5b5]"
              >
                羊皮纸
              </Button>
              <Button
                variant={theme === "hacker" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("hacker")}
                className="bg-black text-[#00ff00] border-[#003300] hover:bg-[#001100]"
              >
                黑客
              </Button>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
};

export default AppearanceSettings;
