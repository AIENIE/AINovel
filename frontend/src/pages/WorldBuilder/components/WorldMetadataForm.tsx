import { useTranslation } from "react-i18next";
import { WorldDetail } from "@/types";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface WorldMetadataFormProps {
  data: WorldDetail;
  onChange: (field: keyof WorldDetail, value: any) => void;
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
            <Label>{t("worldMeta.name")}</Label>
            <Input 
              value={data.name} 
              onChange={(e) => onChange("name", e.target.value)} 
              placeholder={t("worldMeta.namePlaceholder")}
            />
          </div>
          <div className="space-y-2">
            <Label>{t("worldMeta.tagline")}</Label>
            <Input 
              value={data.tagline} 
              onChange={(e) => onChange("tagline", e.target.value)} 
              placeholder={t("worldMeta.taglinePlaceholder")}
            />
          </div>
        </div>
        
        <div className="space-y-2">
          <Label>{t("worldMeta.creativeIntent")}</Label>
          <Textarea 
            value={data.creativeIntent} 
            onChange={(e) => onChange("creativeIntent", e.target.value)} 
            placeholder={t("worldMeta.creativeIntentPlaceholder")}
            className="h-20"
          />
        </div>

        <div className="space-y-2">
          <Label>{t("worldMeta.themes")}</Label>
          <Input 
            value={data.themes.join(", ")} 
            onChange={(e) => onChange("themes", e.target.value.split(",").map(s => s.trim()))} 
            placeholder={t("worldMeta.themesPlaceholder")}
          />
        </div>
        <div className="space-y-2">
          <Label>{t("worldMeta.notes")}</Label>
          <Textarea
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
