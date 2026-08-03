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
  return (
    <Card>
      <CardHeader>
        <CardTitle>基础信息</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>世界名称</Label>
            <Input 
              value={data.name} 
              onChange={(e) => onChange("name", e.target.value)} 
              placeholder="例如：中土世界"
            />
          </div>
          <div className="space-y-2">
            <Label>标语 (Tagline)</Label>
            <Input 
              value={data.tagline} 
              onChange={(e) => onChange("tagline", e.target.value)} 
              placeholder="一句话描述这个世界的特色"
            />
          </div>
        </div>
        
        <div className="space-y-2">
          <Label>创作意图</Label>
          <Textarea 
            value={data.creativeIntent} 
            onChange={(e) => onChange("creativeIntent", e.target.value)} 
            placeholder="你为什么要创造这个世界？核心冲突是什么？"
            className="h-20"
          />
        </div>

        <div className="space-y-2">
          <Label>主题标签 (用逗号分隔)</Label>
          <Input 
            value={data.themes.join(", ")} 
            onChange={(e) => onChange("themes", e.target.value.split(",").map(s => s.trim()))} 
            placeholder="例如：魔法, 战争, 探索"
          />
        </div>
        <div className="space-y-2">
          <Label>创作备注</Label>
          <Textarea
            value={data.notes || ""}
            onChange={(e) => onChange("notes", e.target.value)}
            placeholder="记录仅供创作者参考的约束、来源或后续补充事项"
            className="min-h-24"
          />
        </div>
      </CardContent>
    </Card>
  );
};

export default WorldMetadataForm;
