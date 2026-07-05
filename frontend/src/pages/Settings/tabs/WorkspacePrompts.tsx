import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { PromptMetadata, PromptTemplates } from "@/types";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { useToast } from "@/components/ui/use-toast";
import { Loader2, HelpCircle, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";

const WorkspacePrompts = () => {
  const [prompts, setPrompts] = useState<PromptTemplates | null>(null);
  const [metadata, setMetadata] = useState<PromptMetadata | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    Promise.all([api.prompts.getWorkspace(), api.prompts.getWorkspaceMetadata()]).then(([templates, nextMetadata]) => {
      setPrompts(templates);
      setMetadata(nextMetadata);
    });
  }, []);

  const handleSave = async () => {
    if (!prompts) return;
    setIsSaving(true);
    try {
      await api.prompts.updateWorkspace(prompts);
      toast({ title: "提示词模板已更新" });
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = async () => {
    setIsResetting(true);
    try {
      const defaults = await api.prompts.resetWorkspace();
      setPrompts(defaults);
      toast({ title: "提示词模板已恢复默认" });
    } finally {
      setIsResetting(false);
    }
  };

  if (!prompts) return <div>Loading...</div>;

  const templateCards: Array<{ key: keyof PromptTemplates; title: string; description: string }> = [
    { key: "storyCreation", title: "故事构思", description: "用于从灵感生成故事大纲和角色卡的提示词。" },
    { key: "outlineChapter", title: "章节生成", description: "用于生成章节场景列表的提示词。" },
    { key: "manuscriptSection", title: "正文写作", description: "用于根据场景描述撰写正文的提示词。" },
    { key: "refineWithInstruction", title: "按指令润色", description: "用于根据用户指令改写或优化正文片段的提示词。" },
    { key: "refineWithoutInstruction", title: "默认润色", description: "用于没有额外指令时润色文本的提示词。" },
  ];

  const variablesByTemplate = new Map(metadata?.templates.map((item) => [item.key, item.variables]) ?? []);

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button variant="outline" size="sm" asChild>
          <Link to="/settings/prompt-guide">
            <HelpCircle className="mr-2 h-4 w-4" />
            查看变量说明与示例
          </Link>
        </Button>
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="outline" size="sm" disabled={isResetting}>
              {isResetting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RotateCcw className="mr-2 h-4 w-4" />}
              恢复默认
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>恢复默认提示词模板？</AlertDialogTitle>
              <AlertDialogDescription>当前工作台提示词会被系统默认模板覆盖。</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>取消</AlertDialogCancel>
              <AlertDialogAction onClick={handleReset}>恢复默认</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>

      {templateCards.map((card) => (
        <Card key={card.key}>
          <CardHeader>
            <CardTitle>{card.title}</CardTitle>
            <CardDescription>{card.description}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Textarea
              value={prompts[card.key]}
              onChange={(e) => setPrompts({ ...prompts, [card.key]: e.target.value })}
              className="min-h-[150px] font-mono text-sm"
            />
            {(variablesByTemplate.get(card.key) ?? []).length > 0 ? (
              <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                {(variablesByTemplate.get(card.key) ?? []).map((variable) => (
                  <span key={variable.name} className="rounded border px-2 py-1 font-mono text-foreground">
                    {"{"}{variable.name}{"}"}
                  </span>
                ))}
              </div>
            ) : null}
          </CardContent>
        </Card>
      ))}

      <div className="sticky bottom-6 flex justify-end bg-background/80 backdrop-blur p-4 border rounded-lg shadow-lg">
        <Button onClick={handleSave} disabled={isSaving} size="lg">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
          保存所有修改
        </Button>
      </div>
    </div>
  );
};

export default WorkspacePrompts;
