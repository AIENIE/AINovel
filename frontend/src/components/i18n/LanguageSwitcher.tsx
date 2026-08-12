import { Check, Globe } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SUPPORTED_LOCALES, type UserLocale, setUserLocale } from "@/i18n";

export type LanguageSwitcherProps = {
  className?: string;
  align?: "start" | "end" | "center";
  /** 是否在按钮上展示当前语言名称（窄屏自动隐藏）。 */
  showLabel?: boolean;
};

export function LanguageSwitcher({ className, align = "end", showLabel = true }: LanguageSwitcherProps) {
  const { i18n, t } = useTranslation();
  const current = (SUPPORTED_LOCALES as readonly string[]).includes(i18n.language)
    ? (i18n.language as UserLocale)
    : ("zh-CN" as UserLocale);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className={className}
          aria-label={t("app.language")}
          title={t("app.language")}
        >
          <Globe className="h-4 w-4" />
          {showLabel ? (
            <span className="hidden sm:inline">{t(`app.lang.${current}`)}</span>
          ) : null}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align={align} className="w-44">
        <DropdownMenuLabel>{t("app.language")}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {SUPPORTED_LOCALES.map((locale) => (
          <DropdownMenuItem
            key={locale}
            onClick={() => void setUserLocale(locale)}
          >
            {t(`app.lang.${locale}`)}
            {i18n.language === locale ? <Check className="ml-auto h-4 w-4" /> : null}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
