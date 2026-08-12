import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" title={t("editor.appearance")}>
          <Settings2 className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-4" align="end">
        <div className="space-y-6">
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Type className="h-4 w-4" /> {t("editor.fontStyle")}
            </div>
            <div className="grid grid-cols-3 gap-2">
              <Button
                variant={font === "sans" ? "default" : "outline"}
                size="sm"
                onClick={() => setFont("sans")}
                className="font-sans"
              >
                {t("editor.fontSans")}
              </Button>
              <Button
                variant={font === "serif" ? "default" : "outline"}
                size="sm"
                onClick={() => setFont("serif")}
                className="font-serif"
              >
                {t("editor.fontSerif")}
              </Button>
              <Button
                variant={font === "mono" ? "default" : "outline"}
                size="sm"
                onClick={() => setFont("mono")}
                className="font-mono"
              >
                {t("editor.fontMono")}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Monitor className="h-4 w-4" /> {t("editor.width")}
            </div>
            <div className="grid grid-cols-3 gap-2">
              <Button
                variant={width === "narrow" ? "default" : "outline"}
                size="sm"
                onClick={() => setWidth("narrow")}
              >
                {t("editor.widthNarrow")}
              </Button>
              <Button
                variant={width === "medium" ? "default" : "outline"}
                size="sm"
                onClick={() => setWidth("medium")}
              >
                {t("editor.widthMedium")}
              </Button>
              <Button
                variant={width === "wide" ? "default" : "outline"}
                size="sm"
                onClick={() => setWidth("wide")}
              >
                {t("editor.widthWide")}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Palette className="h-4 w-4" /> {t("editor.theme")}
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Button
                variant={theme === "light" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("light")}
                className="bg-white text-black border-gray-200 hover:bg-gray-100"
              >
                {t("editor.themeLight")}
              </Button>
              <Button
                variant={theme === "dark" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("dark")}
                className="bg-zinc-900 text-white border-zinc-700 hover:bg-zinc-800"
              >
                {t("editor.themeDark")}
              </Button>
              <Button
                variant={theme === "parchment" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("parchment")}
                className="bg-[#f5e6c8] text-[#4a3b2a] border-[#e0d0b0] hover:bg-[#e6d5b5]"
              >
                {t("editor.themeParchment")}
              </Button>
              <Button
                variant={theme === "hacker" ? "default" : "outline"}
                size="sm"
                onClick={() => setTheme("hacker")}
                className="bg-black text-[#00ff00] border-[#003300] hover:bg-[#001100]"
              >
                {t("editor.themeHacker")}
              </Button>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
};

export default AppearanceSettings;
