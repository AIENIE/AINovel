import { Download } from "lucide-react";
import { api } from "@/lib/api-client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";

type ExportSidebarPanelProps = {
  chapterRange: string;
  createExportJob: () => Promise<void> | void;
  createTemplate: () => Promise<void> | void;
  deleteTemplate: (templateId: string) => Promise<void> | void;
  exportAuthorName: string;
  exportFormat: string;
  exportJobs: any[];
  exportTemplateId: string;
  exportTemplates: any[];
  includeTableOfContents: boolean;
  includeTitlePage: boolean;
  selectedManuscriptId: string;
  setChapterRange: (value: string) => void;
  setExportAuthorName: (value: string) => void;
  setExportFormat: (value: string) => void;
  setExportTemplateId: (value: string) => void;
  setIncludeTableOfContents: (value: boolean) => void;
  setIncludeTitlePage: (value: boolean) => void;
  setTemplateDescription: (value: string) => void;
  setTemplateName: (value: string) => void;
  setTxtEncoding: (value: string) => void;
  templateDescription: string;
  templateName: string;
  txtEncoding: string;
  updateTemplate: (template: any) => Promise<void> | void;
};

export function ExportSidebarPanel({
  chapterRange,
  createExportJob,
  createTemplate,
  deleteTemplate,
  exportAuthorName,
  exportFormat,
  exportJobs,
  exportTemplateId,
  exportTemplates,
  includeTableOfContents,
  includeTitlePage,
  selectedManuscriptId,
  setChapterRange,
  setExportAuthorName,
  setExportFormat,
  setExportTemplateId,
  setIncludeTableOfContents,
  setIncludeTitlePage,
  setTemplateDescription,
  setTemplateName,
  setTxtEncoding,
  templateDescription,
  templateName,
  txtEncoding,
  updateTemplate,
}: ExportSidebarPanelProps) {
  return (
    <TabsContent value="export" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <div className="grid grid-cols-2 gap-2 mb-2">
        <Select value={exportFormat} onValueChange={setExportFormat}>
          <SelectTrigger className="h-8">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="txt">TXT</SelectItem>
            <SelectItem value="docx">DOCX</SelectItem>
            <SelectItem value="epub">EPUB</SelectItem>
            <SelectItem value="pdf">PDF</SelectItem>
          </SelectContent>
        </Select>
        <Select value={exportTemplateId} onValueChange={setExportTemplateId}>
          <SelectTrigger className="h-8">
            <SelectValue placeholder="模板" />
          </SelectTrigger>
          <SelectContent>
            {exportTemplates.map((tpl) => (
              <SelectItem key={tpl.id} value={String(tpl.id)}>
                {tpl.name || tpl.id}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="grid grid-cols-2 gap-2 mb-2">
        <Input className="h-8" value={chapterRange} onChange={(event) => setChapterRange(event.target.value)} placeholder="章节范围：如 3-7" />
        <Input className="h-8" value={exportAuthorName} onChange={(event) => setExportAuthorName(event.target.value)} placeholder="作者名（标题页）" />
      </div>
      <div className="grid grid-cols-2 gap-2 mb-2 rounded border p-2 text-xs">
        <label className="flex items-center gap-2">
          <Checkbox checked={includeTitlePage} onCheckedChange={(checked) => setIncludeTitlePage(checked === true)} />
          标题页
        </label>
        <label className="flex items-center gap-2">
          <Checkbox checked={includeTableOfContents} onCheckedChange={(checked) => setIncludeTableOfContents(checked === true)} />
          目录
        </label>
        <div className="col-span-2 grid grid-cols-[56px_1fr] items-center gap-2">
          <span>编码</span>
          <Select value={txtEncoding} onValueChange={setTxtEncoding}>
            <SelectTrigger className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UTF-8">UTF-8</SelectItem>
              <SelectItem value="GBK">GBK</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="rounded border p-2 space-y-2 mb-2 text-xs">
        <div className="font-medium">模板管理</div>
        <div className="grid grid-cols-2 gap-2">
          <Input className="h-8" value={templateName} onChange={(event) => setTemplateName(event.target.value)} placeholder="模板名称" />
          <Input className="h-8" value={templateDescription} onChange={(event) => setTemplateDescription(event.target.value)} placeholder="模板说明" />
        </div>
        <div className="flex gap-2">
          <Button size="sm" onClick={() => void createTemplate()}>
            新建模板
          </Button>
          <Button size="sm" variant="secondary" onClick={() => void createExportJob()}>
            创建导出任务
          </Button>
        </div>
        <div className="space-y-1">
          {exportTemplates.map((template) => (
            <div key={template.id} className="flex items-center justify-between rounded border p-1">
              <div className="truncate mr-2">{template.name}</div>
              <div className="flex gap-1">
                <Button size="sm" variant="outline" className="h-6 px-2" onClick={() => void updateTemplate(template)} disabled={!template.userId}>
                  更新
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  className="h-6 px-2"
                  onClick={() => void deleteTemplate(String(template.id))}
                  disabled={!template.userId}
                >
                  删除
                </Button>
              </div>
            </div>
          ))}
        </div>
      </div>
      <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 space-y-2 text-xs">
        {exportJobs.map((job) => (
          <div key={job.id} className="rounded border p-2">
            <div className="flex items-center justify-between">
              <span>{job.fileName || `${job.id}.${job.format || exportFormat}`}</span>
              <Badge variant="outline">{job.status || "pending"}</Badge>
            </div>
            <Progress className="mt-1" value={Number(job.progress || 0)} />
            {String(job.status).toLowerCase() === "completed" && (
              <a
                className="inline-flex items-center gap-1 text-primary mt-1"
                target="_blank"
                rel="noreferrer"
                href={api.v2.export.downloadUrl(selectedManuscriptId, String(job.id))}
              >
                <Download className="h-3 w-3" />
                下载
              </a>
            )}
          </div>
        ))}
      </ScrollArea>
    </TabsContent>
  );
}
