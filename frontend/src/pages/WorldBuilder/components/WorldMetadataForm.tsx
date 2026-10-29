import { useTranslation } from "react-i18next";
import { WorldDetail } from "@/types";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface WorldMetadataFormProps {
  data: WorldDetail;
  onChange: (field: keyof WorldDetail, value: unknown) => void;
}

const WorldMetadataForm = ({ data, onChange }: WorldMetadataFormProps) => {
  const { t } = useTranslation();
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("worldMeta.basicInfo")}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="world-name">{t("worldMeta.name")}</Label>
            <Input 
              id="world-name"
              value={data.name} 
              onChange={(e) => onChange("name", e.target.value)} 
              placeholder={t("worldMeta.namePlaceholder")}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="world-tagline">{t("worldMeta.tagline")}</Label>
            <Input 
              id="world-tagline"
              value={data.tagline} 
              onChange={(e) => onChange("tagline", e.target.value)} 
              placeholder={t("worldMeta.taglinePlaceholder")}
            />
          </div>
        </div>
        
        <div className="space-y-2">
          <Label htmlFor="world-intent">{t("worldMeta.creativeIntent")}</Label>
          <Textarea 
            id="world-intent"
            value={data.creativeIntent} 
            onChange={(e) => onChange("creativeIntent", e.target.value)} 
            placeholder={t("worldMeta.creativeIntentPlaceholder")}
            className="h-20"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="world-themes">{t("worldMeta.themes")}</Label>
          <Input 
            id="world-themes"
            value={data.themes.join(", ")} 
            onChange={(e) => onChange("themes", e.target.value.split(",").map(s => s.trim()))} 
            placeholder={t("worldMeta.themesPlaceholder")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="world-notes">{t("worldMeta.notes")}</Label>
          <Textarea
            id="world-notes"
            value={data.notes || ""}
            onChange={(e) => onChange("notes", e.target.value)}
            placeholder={t("worldMeta.notesPlaceholder")}
            className="min-h-24"
          />
        </div>
      </CardContent>
    </Card>
  );
};

export default WorldMetadataForm;
