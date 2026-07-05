import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/components/ui/use-toast";
import { api } from "@/lib/api-client";
import { Loader2, Plus } from "lucide-react";

const MaterialCreateForm = ({ onSuccess }: { onSuccess: () => void }) => {
  const [title, setTitle] = useState("");
  const [type, setType] = useState("text");
  const [content, setContent] = useState("");
  const [tags, setTags] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { toast } = useToast();

  const handleSubmit = async () => {
    if (!title || !content) {
      toast({ variant: "destructive", title: "请填写完整信息" });
      return;
    }

    setIsSubmitting(true);
    try {
      await api.materials.create({
        title,
        type: type as any,
        content,
        tags: tags.split(",").map(t => t.trim()).filter(Boolean),
      });
      toast({ title: "素材创建成功" });
      setTitle("");
      setContent("");
      setTags("");
      onSuccess();
    } catch (error) {
      toast({ variant: "destructive", title: "创建失败" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>新建素材</CardTitle>
        <CardDescription>手动添加一条新的设定或资料到素材库。</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label>标题</Label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="例如：魔法体系概述" />
        </div>
        
        <div className="space-y-2">
          <Label>类型</Label>
          <Select value={type} onValueChange={setType}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="text">文本资料</SelectItem>
              <SelectItem value="image">图片链接</SelectItem>
              <SelectItem value="link">外部链接</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>内容</Label>
          <Textarea 
            value={content} 
            onChange={(e) => setContent(e.target.value)} 
            placeholder="输入素材的具体内容..." 
            className="min-h-[200px]"
          />
        </div>

        <div className="space-y-2">
          <Label>标签 (用逗号分隔)</Label>
          <Input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="例如：设定, 魔法, 规则" />
        </div>
      </CardContent>
      <CardFooter>
        <Button onClick={handleSubmit} disabled={isSubmitting} className="w-full">
          {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Plus className="mr-2 h-4 w-4" />}
          创建素材
        </Button>
      </CardFooter>
    </Card>
  );
};

export default MaterialCreateForm;