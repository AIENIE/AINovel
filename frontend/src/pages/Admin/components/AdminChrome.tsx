import { ReactNode } from "react";
import { AlertTriangle, RefreshCcw, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export const adminPanelClass = "border-zinc-800 bg-zinc-900 text-zinc-100";

export const AdminPageHeader = ({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) => (
  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
    <div>
      <h1 className="text-2xl font-semibold tracking-normal text-zinc-50">{title}</h1>
      {description ? <p className="mt-1 text-sm text-zinc-500">{description}</p> : null}
    </div>
    {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
  </div>
);

export const AdminPanel = ({
  title,
  description,
  actions,
  children,
  className,
}: {
  title?: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <Card className={cn(adminPanelClass, className)}>
    {title || description || actions ? (
      <CardHeader className="flex flex-col gap-3 space-y-0 sm:flex-row sm:items-start sm:justify-between">
        <div>
          {title ? <CardTitle className="text-base font-semibold text-zinc-100">{title}</CardTitle> : null}
          {description ? <CardDescription className="mt-1 text-zinc-500">{description}</CardDescription> : null}
        </div>
        {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
      </CardHeader>
    ) : null}
    <CardContent>{children}</CardContent>
  </Card>
);

export const AdminLoadingState = ({ rows = 4 }: { rows?: number }) => (
  <div className="space-y-3">
    {Array.from({ length: rows }, (_, index) => (
      <Skeleton key={index} className="h-14 bg-zinc-800" />
    ))}
  </div>
);

export const AdminEmptyState = ({ title, description }: { title: string; description?: string }) => (
  <div className="rounded-md border border-dashed border-zinc-800 bg-zinc-950/60 px-4 py-8 text-center">
    <div className="text-sm font-medium text-zinc-300">{title}</div>
    {description ? <div className="mt-1 text-sm text-zinc-600">{description}</div> : null}
  </div>
);

export const AdminErrorState = ({ message, onRetry }: { message: string; onRetry?: () => void }) => (
  <div className="flex flex-col gap-3 rounded-md border border-rose-900/70 bg-rose-950/20 p-4 text-sm text-rose-200 sm:flex-row sm:items-center sm:justify-between">
    <div className="flex items-center gap-2">
      <AlertTriangle className="h-4 w-4 shrink-0" />
      <span>{message}</span>
    </div>
    {onRetry ? (
      <Button size="sm" variant="outline" className="border-rose-900 text-rose-200 hover:bg-rose-950" onClick={onRetry}>
        <RefreshCcw className="mr-2 h-4 w-4" />
        重试
      </Button>
    ) : null}
  </div>
);

export const AdminSearchToolbar = ({
  value,
  onChange,
  placeholder,
  children,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  children?: ReactNode;
}) => (
  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
    <div className="relative w-full sm:max-w-sm">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-500" />
      <Input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="border-zinc-800 bg-zinc-950 pl-9 text-zinc-100 placeholder:text-zinc-600"
      />
    </div>
    {children ? <div className="flex flex-wrap items-center gap-2">{children}</div> : null}
  </div>
);

export const AdminPager = ({
  page,
  pageCount,
  total,
  onPageChange,
}: {
  page: number;
  pageCount: number;
  total: number;
  onPageChange: (page: number) => void;
}) => (
  <div className="flex flex-col gap-3 border-t border-zinc-800 pt-3 text-sm text-zinc-500 sm:flex-row sm:items-center sm:justify-between">
    <span>
      共 {total} 条，当前第 {page + 1} / {pageCount} 页
    </span>
    <div className="flex gap-2">
      <Button
        size="sm"
        variant="outline"
        className="border-zinc-800 bg-zinc-950 text-zinc-300 hover:bg-zinc-800"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        上一页
      </Button>
      <Button
        size="sm"
        variant="outline"
        className="border-zinc-800 bg-zinc-950 text-zinc-300 hover:bg-zinc-800"
        disabled={page >= pageCount - 1}
        onClick={() => onPageChange(page + 1)}
      >
        下一页
      </Button>
    </div>
  </div>
);
