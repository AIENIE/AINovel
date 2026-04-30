import { useEffect, useState } from "react";
import { api } from "@/lib/mock-api";
import { PromptTemplates } from "@/types";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/components/ui/use-toast";
import { Loader2, HelpCircle } from "lucide-react";
import { Link } from "react-router-dom";

const WorkspacePrompts = () => {
  const [prompts, setPrompts] = useState<PromptTemplates | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    api.prompts.getWorkspace().then(setPrompts);
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

  if (!prompts) return <div>Loading...</div>;

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex justify-end">
        <Button variant="outline" size="sm" asChild>
          <Link to="/settings/prompt-guide">
            <HelpCircle className="mr-2 h-4 w-4" />
            查看变量说明与示例
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>故事构思</CardTitle>
          <CardDescription>用于从灵感生成故事大纲和角色卡的提示词。</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea 
            value={prompts.storyCreation}
            onChange={(e) => setPrompts({ ...prompts, storyCreation: e.target.value })}
            className="min-h-[150px] font-mono text-sm"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>章节生成</CardTitle>
          <CardDescription>用于生成章节场景列表的提示词。</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea 
            value={prompts.outlineChapter}
            onChange={(e) => setPrompts({ ...prompts, outlineChapter: e.target.value })}
            className="min-h-[150px] font-mono text-sm"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>正文写作</CardTitle>
          <CardDescription>用于根据场景描述撰写正文的提示词。</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea 
            value={prompts.manuscriptSection}
            onChange={(e) => setPrompts({ ...prompts, manuscriptSection: e.target.value })}
            className="min-h-[150px] font-mono text-sm"
          />
        </CardContent>
      </Card>

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
