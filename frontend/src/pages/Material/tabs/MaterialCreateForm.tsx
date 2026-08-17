import { useState } from "react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();

  const handleSubmit = async () => {
    if (!title || !content) {
      toast({ variant: "destructive", title: t("material.fillComplete") });
      return;
    }

    setIsSubmitting(true);
    try {
      await api.materials.create({
        title,
        type: type as unknown,
        content,
        tags: tags.split(",").map(tag => tag.trim()).filter(Boolean),
      });
      toast({ title: t("material.created") });
      setTitle("");
      setContent("");
      setTags("");
      onSuccess();
    } catch (error) {
      toast({ variant: "destructive", title: t("errors.createFailed") });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>{t("material.createDialogTitle")}</CardTitle>
        <CardDescription>{t("material.createDialogDesc")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label>{t("common.title")}</Label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t("material.titlePlaceholder")} />
        </div>
        
        <div className="space-y-2">
          <Label>{t("common.type")}</Label>
          <Select value={type} onValueChange={setType}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="text">{t("material.typeText")}</SelectItem>
              <SelectItem value="image">{t("material.typeImage")}</SelectItem>
              <SelectItem value="link">{t("material.typeLink")}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>{t("common.content")}</Label>
          <Textarea 
            value={content} 
            onChange={(e) => setContent(e.target.value)} 
            placeholder={t("material.contentPlaceholder")}
            className="min-h-[200px]"
          />
        </div>

        <div className="space-y-2">
          <Label>{t("material.tagsLabel")}</Label>
          <Input value={tags} onChange={(e) => setTags(e.target.value)} placeholder={t("material.tagsPlaceholder")} />
        </div>
      </CardContent>
      <CardFooter>
        <Button onClick={handleSubmit} disabled={isSubmitting} className="w-full">
          {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Plus className="mr-2 h-4 w-4" />}
          {t("material.createButton")}
        </Button>
      </CardFooter>
    </Card>
  );
};

export default MaterialCreateForm;
